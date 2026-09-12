# Performance e Qualidade Arquitetural — plano de testes

**Última atualização:** 11 de setembro de 2026
**Status:** planejado (nada implementado)

## 1. Por que este doc existe

MVP em novembro de 2026, 1 dev, VPS única com 8 JVMs + PostgreSQL + Kafka + Redis.
Não dá pra "descobrir" performance em produção. O plano abaixo vai do mais barato
(regras estáticas que rodam em todo `mvn test`) ao mais caro (carga sintética
noturna), em ordem de retorno por hora investida. Cada camada tem um critério de
pronto e um gate que falha o build ou o job.

**O que já existe e vai ser reaproveitado (não reinventar):**

| Já temos | Onde | Serve pra |
|---|---|---|
| Testcontainers (Postgres 16 + Kafka) | `*/src/test/.../integration/AbstractIntegrationTest.java` em auth, cadastro, partner, billing | testes de N+1 e de índice sobre Postgres real |
| Failsafe (`*IT.java`) + JaCoCo gate | poms de auth/cadastro/partner/billing; root `pom.xml` (pluginManagement) | encaixar novos ITs sem config nova |
| Actuator + Micrometer/Prometheus | todos os serviços | métricas p95, Hikari, JVM, Kafka |
| Prometheus + Grafana + Loki | `compose.yaml`, `prometheus.yml` | baseline e comparação carga sintética × produção |
| Jenkins declarativo + DinD | `Jenkinsfile`, `compose.yaml` (`dind`, `jenkins`) | stage noturno de carga |
| **k6 já em uso**: `billing-tenant-load.js` (degraus 1→10→50→100 VUs, 1 VU = 1 tenant, bate direto no billing via `X-Internal-Secret`) + README com queries PromQL de p99/Hikari por tenant | `billing-service/loadtest/` | semente do `perf/k6/` da §6 — decisão de ferramenta já tomada |
| Tag `tenant_id` nas métricas HTTP do billing (`MetricsConfig.java`) | `billing-service` | p99 por tenant; **cardinalidade** controlada só porque o load test fixa ≤100 tenants, não é padrão pra produção |
| Bucket4j no gateway | `gateway/pom.xml` | **atrapalha** o teste de carga, ver §6.4 |
| Hikari `DB_POOL_MAX` (10; fiscal 5) | `*/application.yaml` | único ajuste de pool feito até hoje |

**Lacunas que aparecem já no inventário:** `operacoes-service` e `fiscal-service`
não têm Testcontainers nem Failsafe; nenhum serviço tem `open-in-view=false`,
`default_batch_fetch_size` nem histograma de percentis (o README do loadtest usa
`http_server_requests_seconds_bucket`, que só existe com a config da §4.1);
nenhuma regra de arquitetura automatizada; o único load test cobre um endpoint
de leitura de um serviço, sem gateway/JWT no caminho e sem gate no CI.

## 2. Camada 0 — Orçamento de performance (SLOs)

Sem número-alvo, teste de carga não tem critério de aprovação. Definir antes de
qualquer ferramenta. Proposta inicial (revisar com os sócios, ajustar após o
primeiro baseline da §6):

| Operação | p95 | p99 | Erro 5xx |
|---|---|---|---|
| `POST /auth/login`, `/auth/tenant/login` | 500 ms | 1 s | 0,1 % |
| Listagens paginadas (cadastro, operações) — página de 20 | 300 ms | 700 ms | 0,1 % |
| Escritas simples (CRUD cadastro, requisição/cotação/pedido) | 800 ms | 1,5 s | 0,1 % |
| Faturamento O2C (pedido → NF, com motor fiscal no caminho) | 2 s | 4 s | 0,1 % |
| `POST /fiscal/calcular` (1 item / 50 itens) | 50 ms / 300 ms | 100 ms / 600 ms | 0 |
| Webhook Asaas (`billing`) | 1 s | 2 s | 0 (idempotência não pode furar) |

**Capacidade alvo do MVP:** 30 tenants ativos, 150 usuários simultâneos, 20 req/s
sustentados no gateway, pico de 60 req/s por 5 min. Estes números dimensionam o
cenário de carga da §6; não são promessa ao cliente.

**Orçamento de recursos (por JVM na VPS):** heap conforme `JAVA_TOOL_OPTIONS`
já usado localmente (`-Xmx256m`), a validar no soak (§6.3). Se estourar, a
decisão é subir heap ou consolidar serviços, nunca "ver em produção".

Critério de pronto: tabela acima revisada e colada em `spec/performance-e-arquitetura.md` (este doc) com data.

## 3. Camada 1 — ArchUnit (roda em todo `mvn test`, sem infra)

**Custo:** ~2 dias. **Ganho:** trava regressão de arquitetura e de performance
estática pra sempre, em todos os oito módulos.

### 3.1 Dependência

Root `pom.xml`, `dependencyManagement`: `com.tngtech.archunit:archunit-junit5`
(versão 1.4.x). Cada serviço declara a dependência com `scope=test`.

### 3.2 Uma classe por serviço

`src/test/java/com/l/erp/<svc>/ArchitectureTest.java` com
`@AnalyzeClasses(packages = "com.l.erp.<svc>", importOptions = DoNotIncludeTests.class)`.
Regras iguais em todos (copiar; **não** criar módulo de regras compartilhadas
enquanto forem 8 arquivos de ~60 linhas — `ponytail:` promover pra test-jar do
`common` só se as regras divergirem e precisarem de manutenção central).

### 3.3 Regras de camadas (padrão fixado no `CLAUDE.md`)

Atenção aos dois nomes de pacote: `repositorios`/`dominio` (auth) e
`repository`/`domain` (demais). Usar regex `..repositor(y|ios)..` e `..domain..|..dominio..`.

1. **Controllers não falam com repositório.** `noClasses().that().resideInAPackage("..api.controllers..").should().dependOnClassesThat().resideInAnyPackage("..repository..", "..repositorios..")`.
2. **Controllers não expõem entidade JPA.** `noClasses().that().resideInAPackage("..api.controllers..").should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity.class)` (enums de domínio passam, entidades não).
3. **Repositórios só são usados por services** (e pelo `TenantInterceptor`/infra quando houver — listar exceções explícitas por serviço, não relaxar a regra).
4. **`@Transactional` só em `..services..`** — controller transacional segura conexão do Hikari durante serialização JSON.
5. **Sem ciclos entre services**: `slices().matching("com.l.erp.(*).services.(*)..").should().beFreeOfCycles()`.
6. **Sem `@Scheduled` fora de `..job..`/`..scheduler..`** (billing/partner): garante que todo job passa pelo `DistributedLock`.
7. Regras prontas do ArchUnit (`GeneralCodingRules`): `NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS`, `NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS`, `NO_CLASSES_SHOULD_USE_FIELD_INJECTION`, `NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING`.

### 3.4 Regras de performance estática (é aqui que ArchUnit "prevê")

8. **Todo `@ManyToOne`/`@OneToOne` declara `fetch = LAZY`.** O default do JPA é EAGER — é a causa nº 1 de N+1 silencioso. Condição customizada sobre `fields().that().areAnnotatedWith(ManyToOne.class)`. Hoje já sabemos que `UserRole` (auth) viola; a regra nasce com essa exceção listada (`because("legado, corrigir em ...")`) e a exceção sai quando o campo for corrigido.
9. **Nenhum `findAll()` sem `Pageable` chamado a partir de services** — `noClasses().that().resideInAPackage("..services..").should().callMethodWhere(target(name("findAll")).and(target(rawParameterTypes(new Class[0]))))`. Listagem sem paginação é bomba-relógio com tenant grande.
10. **Nenhuma coleção `@OneToMany` com `fetch = EAGER`** (mesma condição customizada da regra 8, invertida).
11. **Entidade tenant-scoped não expõe `findById` puro** — regra **futura**, depende de fechar o modelo de repositório base do M8 (ver memória `project_pendencias_seguranca`). Documentar como TODO no teste, não inventar solução agora.

### 3.5 Critério de pronto

`./mvnw test` verde nos 8 módulos com `ArchitectureTest` incluído; toda violação
legada listada em `because(...)` com issue/plano. Roda no stage **Build & Test**
existente do Jenkins, sem mudança no `Jenkinsfile`.

## 4. Camada 2 — Guardrails de JPA/SQL (config + ITs baratos)

**Custo:** ~2 dias. **Ganho:** pega N+1 e falta de índice antes de existir dado.

### 4.1 Config em todos os `application.yaml` (defaults ruins do Spring/Hibernate)

```yaml
spring:
  jpa:
    open-in-view: false            # hoje default true: conexão presa até o fim da request
    properties:
      hibernate:
        default_batch_fetch_size: 50   # transforma N+1 em N/50
        jdbc.batch_size: 50            # inserts em lote (recebimento, itens de pedido)
        order_inserts: true
        order_updates: true
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true  # sem isso não existe p95 no Prometheus
```

`open-in-view=false` pode quebrar código que dependia de lazy-load no controller
ou no mapper (MapStruct em `api/mappers` tocando coleção lazy). É exatamente o
que queremos descobrir agora, com os testes existentes, e não em produção.

### 4.2 Teste de contagem de queries (N+1) nos ITs de listagem

Sem lib nova: `hibernate.generate_statistics=true` só no profile `test` e, no IT,
`sessionFactory.getStatistics().getPrepareStatementCount()` antes/depois da
chamada de listagem. Assert: página de 20 pedidos com itens ≤ 3 statements
(pedidos, itens em lote, count). Alvos iniciais, um IT cada:

- `operacoes-service`: listar pedidos de venda e pedidos de compra com itens; detalhe de cotação com fornecedores e itens.
- `cadastro-service`: listar produtos com fornecedor/tabela de preço.
- `auth-service`: login (usuário + roles + permissions — hoje `UserRole` EAGER).

Pré-requisito: `operacoes-service` ganhar `AbstractIntegrationTest` + Failsafe
(copiar de `cadastro-service`; atenção ao gotcha Testcontainers × Docker Engine 29,
memória `project_testcontainers_docker29_api`: `DOCKER_API_VERSION=1.44` ou
Testcontainers 2.x).

### 4.3 Teste de índices obrigatórios (lê `pg_indexes` no Postgres do Testcontainers)

Um IT no `liquibase-service` (ou no serviço dono do schema) que, após aplicar o
changelog, executa:

```sql
select c.table_schema, c.table_name, c.column_name
from information_schema.columns c
where c.column_name = 'tenant_id'
  and not exists (select 1 from pg_indexes i
                  where i.schemaname = c.table_schema and i.tablename = c.table_name
                    and i.indexdef like '%tenant_id%');
```

Resultado esperado: zero linhas. Mesma query pra colunas FK (`information_schema.referential_constraints`).
Toda query do sistema filtra por `tenant_id`; tabela sem índice nessa coluna vira
seq scan em todos os tenants. Este teste "prevê" o problema no dia da migração,
não com 1 milhão de linhas.

### 4.4 Critério de pronto

Config aplicada nos 6 serviços com banco; 5 ITs de contagem verdes; IT de índice
verde (com as faltas atuais corrigidas via novos changesets Liquibase).

## 5. Camada 3 — Micro-benchmark do motor fiscal (JMH, opcional, fora do CI)

`fiscal-service` é puro e determinístico: encaixe perfeito pra JMH, e é o caminho
quente do faturamento (roda por item de NF). Benchmark `calcular` com 1 e 50
itens, sob profile Maven `-Pbench`, **sem gate** (timing em CI compartilhado é
flaky). Uso: rodar antes/depois de mexer em `TabelaFiscal`/vigência por
`dataCompetencia` e comparar. `ponytail:` só implementar quando o baseline da §6
mostrar `/fiscal/calcular` fora do SLO; até lá o teste de carga já mede o
endpoint inteiro.

## 6. Camada 4 — Teste de carga sintético (k6)

**Custo:** ~1 semana pra smoke + carga do O2C com baseline. **Ganho:** único
jeito de responder "aguenta 150 usuários?" antes de novembro.

### 6.1 Ferramenta: k6 (decisão já tomada no repo)

`billing-service/loadtest/billing-tenant-load.js` já usa k6. Motivo de manter:
binário único em container, thresholds nativos (pass/fail sem parser), saída
direto pro Prometheus existente (`--out experimental-prometheus-rw`), scripts JS
curtos. Não introduzir Gatling/JMeter em paralelo.

**O que muda em relação ao script existente:** ele bate direto no serviço com
`X-Internal-Secret`, isolando o billing. Isso continua válido como cenário de
saturação de um serviço só (`stress.js`), mas o cenário de capacidade do MVP
(`carga.js`) tem que entrar pelo **gateway com JWT real**, porque é ali que estão
a validação do token, o rate limit e a injeção de `X-Tenant-Id`. Mover o script
pra `perf/k6/cenarios/billing-saturacao.js` e o README pra `perf/README.md`,
mantendo o conteúdo (as queries PromQL dele viram base do dashboard da §7).

### 6.2 Estrutura

```
perf/
  k6/
    lib/auth.js          # login por tenant → token; cache por VU
    lib/dados.js         # ids de seed (tenants, produtos, clientes)
    smoke.js             # 1 VU, 1 min — "o ambiente subiu e responde"
    carga.js             # ramp 0→150 VUs em 5 min, 10 min platô, 2 min descida
    stress.js            # ramp até quebrar; achar o joelho (só manual)
    soak.js              # 30 VUs, 30–60 min — leak de conexão/heap/consumer lag
    cenarios/
      login.js
      cadastro-listagens.js
      o2c-faturamento.js  # orçamento → pedido → expedição → faturamento (fiscal dentro)
      p2p-pedido.js       # requisição → cotação → pedido (quando fase 3 fechar, + recebimento)
      fiscal-calcular.js
      billing-webhook.js  # N webhooks iguais em paralelo: idempotência sob concorrência
      billing-saturacao.js # = billing-tenant-load.js atual, movido (direto no serviço, sem gateway)
  README.md              # = billing-service/loadtest/README.md atual, movido e ampliado
  compose.perf.yaml      # stack real com limites de CPU/RAM iguais ao VPS
  seed/                  # SQL de N tenants × dados, aplicado após liquibase
```

**Multi-tenant no cenário:** cada VU recebe um tenant distinto do seed; toda
resposta é checada (`check`) pra nunca conter dado de outro tenant. Isolamento
que só quebra sob concorrência (ThreadLocal `TenantContext` vazando entre
requests, `@Async` sem propagação) aparece aqui e em nenhum teste unitário.

**Thresholds** (= SLOs da §2, por cenário):

```js
thresholds: {
  'http_req_duration{cenario:login}':      ['p(95)<500', 'p(99)<1000'],
  'http_req_duration{cenario:listagem}':   ['p(95)<300'],
  'http_req_duration{cenario:faturamento}':['p(95)<2000'],
  'http_req_duration{cenario:fiscal}':     ['p(95)<50'],
  http_req_failed:                         ['rate<0.001'],
  checks:                                  ['rate>0.999'],
}
```

### 6.3 Ambiente de carga = "prever produção"

`perf/compose.perf.yaml` sobe as **imagens já produzidas pelo pipeline**
(`vitorff1234/<svc>:latest`) + postgres/kafka/redis, num host só, com
`deploy.resources.limits` (CPU e memória) copiados do VPS alvo. Só assim o
número obtido significa algo pro deploy real. Gateway no caminho (é onde o JWT é
validado e o rate limit mora).

Após cada rodada, além dos thresholds do k6, ler do `/actuator/prometheus` de
cada serviço e falhar se:

| Métrica | Limite |
|---|---|
| `hikaricp_connections_pending` | > 0 sustentado por 30 s (pool pequeno demais) |
| `jvm_memory_used_bytes{area="heap"}` no fim do soak | > 85 % do máximo, ou crescente sem platô (leak) |
| `kafka_consumer_fetch_manager_records_lag_max` | crescente durante o platô (consumer não acompanha) |
| `webhook_pendente` (billing) | > 0 ao final |
| `job_segundos_desde_ok{job}` (billing) | jobs continuam rodando sob carga |

### 6.4 Gotchas conhecidos antes de começar

- **Bucket4j no gateway** vai devolver 429 pro k6 e falsear tudo. Precisa de
  chave de config (`gateway.rate-limit.enabled=false` no profile `perf`, ou
  allowlist do IP do k6). Nunca desligar por padrão.
- **Brute-force lock do login** (auth): VUs errando senha por bug de script
  travam o usuário de teste. Seed com senha certa e `check` no status 200 antes
  de continuar.
- **Testcontainers/DinD no Jenkins:** o compose perf não usa Testcontainers,
  mas roda dentro do mesmo DinD (`DOCKER_HOST=tcp://dind:2375`) — limites de
  recurso do `dind` precisam caber a stack inteira.
- **`-Xmx256m`** hoje é ergonomia local; no compose perf o valor tem que ser o
  que vai pro VPS.

### 6.5 CI

Novo stage no `Jenkinsfile`, **fora do fluxo de PR** (leva 20+ min):

```groovy
stage('Perf (nightly)') {
    when { allOf { branch 'main'; triggeredBy 'TimerTrigger' } }
    // docker compose -f perf/compose.perf.yaml up -d --wait
    // k6 run perf/k6/smoke.js && k6 run perf/k6/carga.js --out json=perf-result.json
    // archiveArtifacts perf-result.json; falha do k6 = build vermelho
}
```

Cron do Multibranch às 2h. Resultado do soak semanal vai pro Grafana, não pro
gate (é análise de tendência).

### 6.6 Critério de pronto

Primeiro **baseline** publicado neste doc (§9): p95/p99 por cenário, 150 VUs,
limites do VPS. Sem baseline, tudo acima é opinião.

## 7. Camada 5 — Observabilidade como previsão contínua

Já temos a stack; falta usar pra performance:

1. **Dashboard "Performance" no Grafana** (provisionado em `grafana/` como os
   existentes): p95 por `uri` (`http_server_requests_seconds_bucket`, requer §4.1),
   `hikaricp_connections_{active,pending}`, GC pause, heap por serviço, Kafka lag,
   `webhook_pendente`. O mesmo painel lê carga sintética (§6) e produção — é a
   comparação que valida a previsão.
2. **Alertas** (Prometheus `rules`): p95 acima do SLO por 5 min; `pending > 0`
   por 1 min; heap > 85 % por 10 min; `job_segundos_desde_ok > 3600`.
3. **Log lento**: `logging.level.org.hibernate.SQL` fica desligado; em vez disso
   `spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=500`
   loga só query lenta, com `correlationId` no pattern já existente → Loki.

## 8. Ordem de execução e encaixe no calendário

Hoje é 11/09/2026, MVP em novembro; P2P fase 3 (recebimento) em andamento.

| Quando | O quê | Gate |
|---|---|---|
| Semana 1 (paralelo ao P2P) | §2 SLOs escritos · §4.1 config JPA/percentis nos 6 serviços · §3 ArchUnit nos 8 módulos | `mvn test` |
| Semana 2 | §4.3 IT de índices · §4.2 ITs de N+1 (operacoes ganha Testcontainers/Failsafe) | `mvn verify` |
| Semanas 3–4 | §6 k6 smoke + `carga.js` com login, listagens, O2C, fiscal · `compose.perf.yaml` · rodar manual, **baseline** | thresholds |
| Semana 5 | §6.5 stage nightly · §7 dashboard + alertas · `soak.js` semanal | job noturno |
| Pós-baseline | ajustar pool/`concurrency`/`linger.ms` (memória `project_pendencias_performance`), Caffeine em tenant/plan/permission lookup **só se** o baseline apontar · §5 JMH fiscal · `stress.js` pra achar o joelho | — |

Regra de ouro: **nenhum tuning antes do baseline.** As pendências de performance
já listadas na memória (pool, Kafka batch, cache) viram hipóteses a confirmar na
§6, não tarefas.

## 9. Baseline (preencher após a primeira rodada da §6)

| Data | Cenário | VUs | p95 | p99 | Erro | Hikari pending | Heap fim | Observações |
|---|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — | ainda não rodado |
