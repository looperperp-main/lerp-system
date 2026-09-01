import http from 'k6/http';
import { check, sleep } from 'k6';

// Load test estruturado do billing-service — degraus de tenants concorrentes (1 → 10 → 50 → 100)
// até achar o ponto real de saturação (p99 de latência ou CPU), não uma estimativa. Ver README.md
// desta pasta pra como ler o resultado no Grafana/Prometheus.
//
// Bate DIRETO no billing-service (não no gateway/auth-service) porque o InternalRequestFilter
// dele confia em X-Internal-Secret + X-User-Id/X-Authorities já validados a montante — é o mesmo
// contrato que o gateway usa (ver infra/filter/InternalRequestFilter.java). Isola o billing como
// "caso mais crítico" sem misturar o throughput do login/gateway no resultado.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const INTERNAL_SECRET = __ENV.INTERNAL_GATEWAY_SECRET;
if (!INTERNAL_SECRET) {
  throw new Error(
    'Defina INTERNAL_GATEWAY_SECRET com o mesmo valor de internal.gateway.secret do billing-service.'
  );
}

// Cada VU simula um tenant fixo (id estável durante toda a run) — assim dá pra tagear a métrica
// tenant_id no Prometheus e ver RAM/conexões/latência atribuídos a um tenant específico.
const TENANT_BASE_ID = 100000; // fora da faixa usada por tenants reais de dev/homolog

export const options = {
  scenarios: {
    saturacao_por_tenants: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '1m', target: 1 },
        { duration: '2m', target: 10 },
        { duration: '3m', target: 50 },
        { duration: '5m', target: 100 },
        { duration: '2m', target: 100 }, // platô: confirma estabilidade em 100 antes de encerrar
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // Não aborta a run — o objetivo é observar onde estoura, não parar cedo. Só marca no
    // resumo final se algum degrau já passou do teto aceitável.
    http_req_duration: ['p(99)<2000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const tenantId = TENANT_BASE_ID + __VU;
  const headers = {
    'X-Internal-Secret': INTERNAL_SECRET,
    'X-User-Id': `loadtest-${__VU}`,
    'X-Authorities': 'ASSINATURA_MANAGE',
    'X-Tenant-Id': String(tenantId),
  };

  const res = http.get(`${BASE_URL}/api/v1/subscriptions?tenantId=${tenantId}`, { headers });
  check(res, { 'status 200': (r) => r.status === 200 });

  // Cadência de um tenant real durante horário comercial: poucas consultas por sessão, não
  // rajada constante. Amostragem triangular (soma de 2 uniformes, média ~10s) em vez de sleep
  // fixo — evita o "chute manual" de intervalo uniforme.
  const think = 5 + (Math.random() + Math.random()) * 5; // 5-15s, média ~10s
  sleep(think);
}
