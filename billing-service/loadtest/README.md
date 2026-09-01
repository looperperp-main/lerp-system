# Load test estruturado — billing-service

Achar o ponto real de saturação do billing-service (p99 de latência ou CPU), instrumentado
por tenant no Prometheus. Não é chute manual — é `k6` + as métricas que o próprio serviço
já expõe em `/actuator/prometheus`, mais a tag `tenant_id` adicionada em `MetricsConfig.java`.

## Pré-requisitos (o Claude não roda nada disso — ver CLAUDE.md "Nunca rodar build")

1. Instalar o [k6](https://k6.io/docs/get-started/installation/).
2. Subir a infra: `docker compose up -d postgres redis kafka zookeeper prometheus grafana`.
3. Rodar o billing-service localmente (`./mvnw spring-boot:run -pl billing-service`) com
   `INTERNAL_GATEWAY_SECRET` = mesmo valor de `internal.gateway.secret` (env `INTERNAL_GATEWAY_SECRET`
   no `application.yaml`).
4. Confirmar que `prometheus.yml` está de fato apontando pro billing rodando (hoje o alvo é
   `host.docker.internal:8088`, comentado `billing-service:8088` — usar o que bater com o setup local).

## Rodar

```bash
k6 run -e INTERNAL_GATEWAY_SECRET=<o mesmo secret> billing-tenant-load.js
```

Sobe de 1 → 10 → 50 → 100 VUs (cada VU = 1 tenant simulado), ~13 min de run total.
Cada VU repete `GET /api/v1/subscriptions?tenantId=X` com sleep de 5-15s entre chamadas
(cadência de tenant real olhando o próprio billing, não rajada constante).

Por que `GET /subscriptions` e não outro endpoint: é leitura, não muda estado (repetível sem
sujar dados), passa pelo `SubscriptionRepository` (exercita o pool HikariCP — hoje limitado a
`maximum-pool-size: 10`, provável primeiro teto a estourar, antes até de CPU/RAM da máquina).

## Ler o resultado no Grafana/Prometheus (:3000 / :9090)

**p99 de latência por tenant** (o sinal principal — onde ele estoura por degrau de VUs):
```promql
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket{application="billing-service"}[1m])) by (le, tenant_id)
)
```

**Taxa de requisições por tenant:**
```promql
sum(rate(http_server_requests_seconds_count{application="billing-service"}[1m])) by (tenant_id)
```

**Conexões do pool HikariCP em uso / na fila** (sinal de saturação de pool, já vem de graça —
Spring Boot instrumenta `HikariDataSource` automaticamente, sem código novo):
```promql
hikaricp_connections_active{application="billing-service"}
hikaricp_connections_pending{application="billing-service"}   # > 0 = requests esperando conexão
```

**CPU e heap do processo** (JVM inteira — não dá pra segregar por tenant, ver caveat abaixo):
```promql
process_cpu_usage{application="billing-service"}
jvm_memory_used_bytes{application="billing-service", area="heap"}
```

## Caveat importante: "RAM/CPU/conexões por tenant" tem limite físico

Latência e contagem de requisições **são** atribuíveis a um tenant específico (a tag
`tenant_id` faz isso). Mas CPU, heap e pool de conexões são recursos **compartilhados** por
todos os tenants na mesma JVM/pool — não existe "os 3 tenants tal consumiram X MB de RAM" de
forma exata, só "no momento em que o tenant X estava gerando carga, o processo inteiro estava
em Y% de CPU e o pool tinha Z conexões pendentes". O ponto de saturação real é onde
`hikaricp_connections_pending` sai de 0 e o p99 dispara junto — isso sim é atribuível ao degrau
de tenants concorrentes, não a um tenant isolado.

## Cardinalidade da tag tenant_id

`MetricsConfig.java` só marca `tenant_id` como low-cardinality porque este load test controla
quantos tenants existem (≤100, IDs fixos). **Não é padrão pra produção** — um ERP multi-tenant
real teria milhares de tenants e essa tag explodiria a cardinalidade do Prometheus (mesma regra
que já vale pro Loki, ver `CLAUDE.md`). Se o teste mostrar que vale a pena manter isso em
produção, precisa de um teto (ex.: só os N tenants com mais tráfego, resto agregado em "outros").

## Se o pool de 10 conexões estourar antes da CPU

Suba `DB_POOL_MAX` e `REDIS` pool (`spring.data.redis.lettuce.pool.max-active`) antes de rodar
de novo — senão o teste mede o teto de configuração, não o teto de hardware.
