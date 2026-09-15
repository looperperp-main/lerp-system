# Spec — Maturidade de Engenharia: Passos 3 e 4

> Parte do plano de 5 passos para elevar a maturidade do ERP-VSD (ver memória
> `project_maturidade_engenharia`). Passos 1 (testes de filtros), 2 (OpenAPI) e
> 5 (docs) já concluídos. Este spec cobre os **passos 3 e 4**, a implementar depois.

---

## Passo 3 — CI reproduzível com GitHub Actions

### Problema
Hoje o pipeline roda **só no Jenkins local** (DinD na máquina do dev). Consequências:
- Ninguém além do dono da máquina consegue reproduzir o gate.
- PRs não têm checks automáticos — nada impede merge de código que quebra o `verify`.
- "Passa na minha máquina" não é CI de verdade.

### Objetivo
Um workflow GitHub Actions que espelha o `./mvnw verify` e roda em todo push/PR,
dando o check verde/vermelho direto no PR. **Não substitui** o Jenkins (que continua
fazendo build+push de imagem Docker e análise SonarQube); o Actions é a porta de
entrada barata e reproduzível para validar PRs.

### Escopo
- [ ] Criar `.github/workflows/ci.yml`.
- [ ] Disparar em `push` (branches `main`) e `pull_request`.
- [ ] Job de build/test:
  - `actions/checkout@v4`
  - `actions/setup-java@v4` com `temurin` **Java 25** + cache de `~/.m2`.
  - Rodar `./mvnw -B clean verify` nos 4 serviços de aplicação com `-am`
    (auth-service, cadastro-service, partner-service, billing-service),
    igual ao Jenkinsfile (stage "Build & Test").
- [ ] Subir serviços de infra que os testes de integração (Testcontainers) exigem.
      **Atenção:** os ITs usam Testcontainers — no GitHub Actions o Docker já está
      disponível no runner `ubuntu-latest`, então Testcontainers funciona out-of-the-box
      (não precisa do hack `TESTCONTAINERS_HOST_OVERRIDE=dind` que o Jenkins usa).
- [ ] Publicar relatórios de teste como artifact (`actions/upload-artifact`) — surefire/failsafe.
- [ ] (Opcional) Publicar o relatório agregado de cobertura JaCoCo como artifact.

### Pontos de atenção
- **Java 25**: confirmar que o `setup-java` tem a distribuição (temurin 25). Se ainda
  não estiver disponível como release estável no Actions, usar a EA ou `oracle`/`zulu`.
- **Segredos**: o `verify` não deve precisar de segredos reais (testes usam H2/Testcontainers/mocks).
  Se algum teste exigir env var (ex.: `JWT_SECRET`), injetar valor dummy no workflow,
  nunca segredo real.
- **Tempo de build**: cache do `~/.m2` é essencial; sem ele cada run baixa o mundo.
- **Não duplicar o push de imagem**: o Actions só valida (`verify`). Build/push de
  imagem Docker e SonarQube continuam no Jenkins (evita custo/duplicação e vazar credenciais do Docker Hub no GitHub).

### Critério de pronto
- PR aberto mostra o check "CI / build" rodando e passando/falhando conforme o `verify`.
- Um PR que quebra um teste fica vermelho e bloqueia o merge (configurar branch protection
  em `main` exigindo o check — passo manual no GitHub, registrar no README).

---

## Passo 4 — Subir o gate de cobertura JaCoCo gradualmente

### Problema
O CLAUDE.md diz "mínimo 40% de cobertura de instrução", mas a realidade per-módulo
hoje é **muito mais baixa** (a propriedade `coverage.minimum` é sobrescrita em cada serviço):

| Módulo | `coverage.minimum` atual |
|---|---|
| pom raiz (default) | 0.40 |
| auth-service | 0.20 |
| cadastro-service | 0.03 |
| billing-service | 0.00 |
| partner-service | *(verificar no pom)* |

Ou seja: o "gate de 40%" é ilusório — cada serviço afrouxou o próprio mínimo para
fazer o build passar. Um gate que não falha não protege nada.

### Objetivo
Elevar os mínimos de forma incremental (40 → 50 → 60%) **acompanhando** o aumento de
testes (passo 1 e os que vierem), para o gate voltar a ter significado sem travar o time.

### Escopo
- [ ] Mapear o `coverage.minimum` real de cada um dos 4 serviços (ler cada `pom.xml`).
- [ ] Confirmar onde está a execução `jacoco:check` que usa essa propriedade
      (provavelmente no pom raiz `pluginManagement` ou em cada serviço) e qual
      `COUNTER` ela mede (INSTRUCTION vs LINE) e quais `excludes` aplica
      (DTOs, mappers, domain, `@Configuration` — já excluídos no Sonar via `sonar.exclusions`).
- [ ] Definir a escada de metas por serviço, partindo do valor atual + folga real:
  - **Rodada 1**: levar todos os serviços para no mínimo **0.40** (o valor "oficial").
    Pode exigir escrever testes antes (não baixar a meta — subir a cobertura).
  - **Rodada 2**: **0.50**.
  - **Rodada 3**: **0.60** nos caminhos de negócio.
- [ ] A cada rodada: rodar `./mvnw verify`, ver onde falha, escrever testes para cobrir
      o gap, só então commitar o novo `coverage.minimum`.
- [ ] Remover a sobrescrita per-módulo quando o serviço atingir o default do raiz
      (centraliza o gate num lugar só).

### Pontos de atenção
- **Ordem importa**: subir o número *antes* de ter testes só quebra o build. A regra é
  testes primeiro, gate depois.
- **Priorizar por risco**, não por facilidade: cobrir `services/` e filtros de segurança
  rende mais que cobrir getters. O JaCoCo conta instrução, então é tentador "encher"
  cobertura com código trivial — resistir a isso.
- **partner-service** tinha só 3 testes (agora +5 do `InternalRequestFilterTest`);
  billing e cadastro também são candidatos a ganhar testes de service antes de subir o gate.
- Alinhar os `excludes` do JaCoCo com os `sonar.exclusions` do pom raiz para o número
  do gate e o do Sonar contarem a mesma coisa.

### Critério de pronto
- Todos os 4 serviços com `coverage.minimum >= 0.40` e `verify` verde.
- Idealmente, a sobrescrita per-módulo removida — gate único de 0.40+ herdado do raiz.
- Meta de médio prazo registrada: 0.60 nos serviços com lógica de negócio crítica.

---

## Dependências e ordem sugerida
1. **Passo 3 primeiro** (GitHub Actions) — barato, e passa a dar feedback de PR imediato.
2. **Passo 4 em seguida**, em rodadas, cada uma acompanhada de novos testes. Com o
   Actions já rodando, cada subida de gate é validada automaticamente no PR.