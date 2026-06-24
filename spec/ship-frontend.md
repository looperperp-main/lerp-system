# Ship do Front-end — 3 portais Angular

> **Status:** PLANEJADO (não iniciado). Documento de referência para colocar os portais Angular em produção.
> **Data:** 2026-06-24. **Escopo:** build de produção, correção de URLs, conteinerização e deploy na OCI.
> **Método:** estado verificado no repo (builders do `angular.json`, `environments/`, hardcodes de `localhost`).

---

## 1. Inventário dos portais

| Portal | Pasta | Builder | SSR? | Porta dev | Destino em prod | Servido por |
|---|---|---|---|---|---|---|
| **Tenant** | `Angular/erp-front-end-web` | `@angular/build:application` | ❌ não | 4200 | `{slug}.syax.com.br` (wildcard DNS) | nginx (estático) |
| **Contador/Parceiro** | `Angular/erp-front-end-partner` | `@angular/build:application` | ✅ **SIM** (`outputMode: server`, `@angular/ssr`) | — | `portal-do-contador.syax.com.br` | **Node** (`server.mjs`) + nginx na frente |
| **Admin** | `Angular/erp-front-end-admin` | `@angular/build:application` | ❌ não | 4201 | sem subdomínio público — **SSH tunnel :4201** | nginx (estático, bind `127.0.0.1`) |

> O builder novo `@angular/build:application` gera em `dist/<projeto>/` com subpasta `browser/` (assets do cliente) e, quando SSR, também `server/` (`server.mjs` + `main.server`).

---

## 2. Modelos de serviço

### 2.1 SPA estática (web, admin)
`ng build` → copiar `dist/<app>/browser/` para um nginx. Roteamento de SPA exige fallback:
```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

### 2.2 SSR / Node (partner)
`ng build` → produz `dist/erp-front-end-partner/server/server.mjs`. Em prod roda como **processo Node** (`node dist/erp-front-end-partner/server/server.mjs`, escuta em `PORT`, default 4000) com um nginx reverso na frente (TLS + cache dos assets estáticos de `browser/`). **Não** dá para servir só com nginx estático.

---

## 3. 🔴 Bloqueadores (resolver ANTES de qualquer build de prod)

| # | Problema | Onde | Impacto |
|---|---|---|---|
| B1 | `apiUrl` aponta para `http://localhost:8090` e **não há `environment.prod.ts`** nem `fileReplacements` no `angular.json` | `web` e `admin`: `src/environments/environment.ts` | build de produção chama `localhost` → front quebrado |
| B2 | **partner não tem `src/environments/`** | `erp-front-end-partner` | não há de onde ler a URL da API |
| B3 | URLs `http://localhost` **hardcoded** (fora do `environment`) | ver §3.1 | ignoram qualquer config; quebram em prod |
| B4 | partner é **SSR** — o `server.ts` também faz fetch e precisa da URL **interna** do gateway (server-side não usa o domínio público) | `partner/src/server.ts` | SSR chama API; em container, host = `gateway:8090`/`api.syax.com.br`, não `localhost` |

### 3.1 Arquivos com `localhost` hardcoded (trocar por `environment.apiUrl`)
- **web (3 + env):** `app/pages/ativar/ativar.ts`, `app/services/cnpj.service.ts`, `app/services/feature-tracking.service.ts` (+ `environments/environment.ts`)
- **partner (5 + server):** `app/pages/login/partner-login.service.ts`, `app/services/cnpj.service.ts`, `app/services/convite.service.ts`, `app/services/dashboard.service.ts`, `app/services/partner-session.service.ts` (+ `src/server.ts`)
- **admin (env):** só `environments/environment.ts`

---

## 4. Plano de execução

### Fase 1 — Configuração de ambiente (destrava B1, B2)
1. Criar `src/environments/environment.prod.ts` nos **3** portais:
   ```ts
   export const environment = {
     production: true,
     apiUrl: 'https://api.syax.com.br' // gateway público
   };
   ```
2. Criar a pasta `environments/` no **partner** (hoje inexistente) com `environment.ts` (dev, `localhost:8090`) + `environment.prod.ts`.
3. Adicionar `fileReplacements` na config `production` de cada `angular.json`:
   ```json
   "fileReplacements": [
     { "replace": "src/environments/environment.ts", "with": "src/environments/environment.prod.ts" }
   ]
   ```

### Fase 2 — Eliminar hardcodes (destrava B3, B4)
4. Trocar os 11 usos de `http://localhost...` pelos métodos via `environment.apiUrl` (lista §3.1).
5. No `partner/src/server.ts` (SSR), usar uma URL **server-side** configurável (ex.: `process.env['API_INTERNAL_URL']`, default `http://gateway:8090`) — o render no servidor não enxerga o domínio público.

### Fase 3 — Conteinerização
6. **web e admin** — `Dockerfile` multi-stage (node build → nginx):
   ```dockerfile
   FROM node:22-alpine AS build
   WORKDIR /app
   COPY package*.json ./
   RUN npm ci
   COPY . .
   RUN npm run build -- --configuration production
   FROM nginx:alpine
   COPY nginx.conf /etc/nginx/conf.d/default.conf
   COPY --from=build /app/dist/<APP>/browser /usr/share/nginx/html
   EXPOSE 80
   ```
   `nginx.conf` com o `try_files ... /index.html` da §2.1.
7. **partner (SSR)** — `Dockerfile` node runtime:
   ```dockerfile
   FROM node:22-alpine AS build
   WORKDIR /app
   COPY package*.json ./
   RUN npm ci
   COPY . .
   RUN npm run build -- --configuration production
   FROM node:22-alpine
   WORKDIR /app
   COPY --from=build /app/dist/erp-front-end-partner ./dist/erp-front-end-partner
   ENV PORT=4000
   EXPOSE 4000
   CMD ["node", "dist/erp-front-end-partner/server/server.mjs"]
   ```

### Fase 4 — Deploy na OCI (consistente com o backend)
8. Build + push das imagens para o Docker Hub (`vitorff1234/erp-front-end-{web,partner,admin}`), seguindo o fluxo dos serviços Java (Jenkins → Docker Hub → `compose pull` na OCI). **Decisão:** se entram no Jenkinsfile ou num pipeline próprio do front.
9. nginx reverso na OCI (host ou container) com:
   - `*.syax.com.br` → container do **web** (estático)
   - `portal-do-contador.syax.com.br` → reverse proxy para o **partner** (Node :4000)
   - **admin** → nginx ligado só em `127.0.0.1:4201` (acesso via SSH tunnel, sem subdomínio público)
   - `api.syax.com.br` → gateway `:8090`
10. TLS wildcard via Certbot DNS-01 (Cloudflare) — um cert cobre `*.syax.com.br`.

---

## 5. Pontos de decisão (a definir antes de começar)
- **Pipeline do front:** entra no `Jenkinsfile` atual (mais um estágio) ou pipeline/job separado? O front não tem teste no CI hoje.
- **SSR do partner vale a pena?** Se o SEO/first-paint não for requisito, simplifica MUITO trocar o partner para SPA estática (remove o runtime Node em prod). Avaliar.
- **Versionamento das imagens:** `:latest` + `:<BUILD_NUMBER>` como no backend.
- **`apiUrl` por slug (tenant):** o web é multi-tenant por subdomínio; confirmar se a app lê `window.location.hostname` para o slug (pendência de infra já mapeada).

---

## 6. Checklist de "pronto para ship"
- [ ] `environment.prod.ts` nos 3 + `fileReplacements` no `angular.json`
- [ ] pasta `environments/` criada no partner
- [ ] 11 hardcodes de `localhost` removidos (§3.1)
- [ ] `server.ts` do partner usando URL interna configurável
- [ ] `Dockerfile` + `nginx.conf` (web, admin) e `Dockerfile` Node (partner)
- [ ] `ng build --configuration production` OK nos 3 (sem referência a `localhost` no bundle: `grep -r localhost dist/`)
- [ ] imagens no Docker Hub
- [ ] nginx + DNS + TLS dos subdomínios na OCI
- [ ] smoke test: login tenant, fluxo contador, admin via tunnel

---

> Relacionado: `project_infra` (memória) — subdomínios, OCI Ampere ARM64, SSH tunnel; e a pendência de slug multi-tenant.