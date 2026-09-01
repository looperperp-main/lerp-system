# -*- coding: utf-8 -*-
"""
Dados brutos da auditoria de segurança do ERP-VSD.
Editar este arquivo e rodar `generate_report.py` para regenerar o PDF.

severidade: "critica" | "alta" | "media" | "baixa" | "informativa"
categoria: 1..5 (ver CATEGORY_NAMES abaixo)
"""

PROJECT_NAME = "ERP-VSD"
AUDIT_DATE = "31 de agosto de 2026"

STACK_SUMMARY = (
    "Monorepo Maven multi-módulo em Java 25 / Spring Boot 4.x / Spring Cloud 2025.x "
    "(registry, gateway, auth-service, cadastro-service, partner-service, billing-service, "
    "fiscal-service, liquibase-service) + frontend Angular 21 standalone em 3 SPAs "
    "(erp-front-end-web, erp-front-end-admin, erp-front-end-partner). ORM: Spring Data JPA "
    "(Hibernate) com Liquibase controlando todo DDL. Auth: JWT HS256 emitido pelo auth-service, "
    "validado no gateway (SecurityFilter), que injeta X-Tenant-Id/X-User-Id/X-Is-Owner/authorities "
    "como headers para os serviços downstream; downstream services não revalidam a assinatura do "
    "JWT, apenas confiam nos headers do gateway, e usam @PreAuthorize (RBAC método-a-método) para "
    "autorização granular. Deploy: Docker (Dockerfile por serviço) + docker-compose (infra local) + "
    "Jenkins/SonarQube (CI/CD declarativo via Jenkinsfile)."
)

METHODOLOGY = (
    "Cada categoria genérica foi mapeada para o equivalente desta stack: "
    "(1) BANCO SEM TRANCA -> filtro de tenant via Hibernate @Filter/@FilterDef + AspectJ "
    "(TenantFilterAspect) + ThreadLocal (TenantContext), cuja limitação documentada é não cobrir "
    "acesso por chave primária (findById/deleteById) — verificado call-a-call; "
    "(2) PERMISSÃO NO NAVEGADOR -> cruzamento de *ngIf/@if e route guards Angular com anotações "
    "@PreAuthorize/SecurityConfig nos controllers Spring correspondentes; "
    "(3) IDOR -> varredura de todo handler REST que recebe um ID (path/query/body) checando "
    "ownership/tenant antes de ler, alterar ou excluir; "
    "(4) CHAVES EXPOSTAS -> grep de padrões ${VAR:-default} em application*.yml/properties, "
    "compose.yaml, Dockerfiles, Jenkinsfile e histórico git recente; "
    "(5) INPUTS SEM TRATAMENTO -> grep de innerHTML/bypassSecurityTrust/eval no Angular e de "
    "concatenação de dado de usuário em HTML/e-mail no backend."
)

CATEGORY_NAMES = {
    1: "Banco sem tranca (isolamento de tenant)",
    2: "Permissão definida no navegador",
    3: "IDOR",
    4: "Chaves expostas",
    5: "Inputs sem tratamento (XSS/HTML injection)",
}

SEVERITY_COLORS = {
    "critica": "#B91C1C",
    "alta": "#EA580C",
    "media": "#D97706",
    "baixa": "#2563EB",
    "informativa": "#6B7280",
    "ponto_forte": "#059669",
}

SEVERITY_LABELS = {
    "critica": "Crítica",
    "alta": "Alta",
    "media": "Média",
    "baixa": "Baixa",
    "informativa": "Informativa",
}

# ---------------------------------------------------------------------------
# ACHADOS (findings) — só o que foi verificado em código real.
# ---------------------------------------------------------------------------
FINDINGS = [
    {
        "id": "F1",
        "categoria": [2, 3],
        "severidade": "critica",
        "titulo": "partner-service: PartnerController sem nenhuma autorização — CRUD administrativo aberto a qualquer usuário autenticado",
        "arquivos": [
            ("partner-service/src/main/java/com/l/erp/partnerservice/api/controllers/PartnerController.java", "1-247"),
            ("partner-service/src/main/java/com/l/erp/partnerservice/infra/config/SecurityConfig.java", "15, 23-24"),
            ("gateway/src/main/resources/application.yml", "13-18"),
            ("gateway/src/main/java/com/l/erp/gateway/security/SecurityFilter.java", "~126 (verifyPartnerURL)"),
            ("Angular/erp-front-end-admin/src/app/pages/cadastros/parceiros/parceiros.service.ts", "1-53"),
            ("Angular/erp-front-end-admin/src/app/util/authguard.ts", "4-14"),
        ],
        "snippet": (
            "// SecurityConfig.java\n"
            ".authorizeHttpRequests(auth -> auth.anyRequest().permitAll())\n\n"
            "// PartnerController.java — nenhum método tem @PreAuthorize\n"
            "@GetMapping\npublic ResponseEntity<...> findAll(...) { ... }\n"
            "@GetMapping(\"/{id}\")\npublic ResponseEntity<...> findById(@PathVariable UUID id) { ... }\n"
            "@PatchMapping(\"/{id}/approve\")\npublic ResponseEntity<...> approve(@PathVariable UUID id, ...) { ... }\n"
            "@GetMapping(\"/referrals/by-tenant/{tenantId}\")\npublic ResponseEntity<...> origemPorTenant(@PathVariable Long tenantId) { ... }"
        ),
        "por_que": (
            "partner-service não impõe autorização na cadeia de filtros do Spring Security "
            "(anyRequest().permitAll()), delegando 100% da autorização a @PreAuthorize por método — "
            "e PartnerController não tem NENHUM @PreAuthorize. O único gate que resta é o "
            "InternalRequestFilter, que apenas confirma que a requisição chegou via gateway com o "
            "segredo interno e um X-User-Id presente — não valida role/authority. O gateway, por sua "
            "vez, só valida a assinatura do JWT e injeta os headers; não há restrição de role por rota "
            "para /partner/**. Curiosamente o gateway TEM um mecanismo pronto para esse tipo de "
            "restrição — verifyPartnerURL() em SecurityFilter.java (~linha 126) já restringe JWTs com "
            "loginType==\"PARTNER\" a um allowlist de prefixos (/partner/api/v1/partners/me, "
            "/cnpj/, /billing/partner/) — mas essa restrição não existe para nenhum OUTRO loginType "
            "(ex.: usuário comum de um tenant) alcançando as mesmas rotas administrativas. No frontend "
            "admin, a rota /parceiros/contadores é protegida só pelo authGuard (checa apenas se existe "
            "um token em sessionStorage, não o papel do usuário). Resultado: qualquer usuário "
            "autenticado no admin (ou qualquer portador de um JWT válido de loginType diferente de "
            "PARTNER) pode listar todos os parceiros da plataforma, ver/editar qualquer parceiro por "
            "ID, ver dados de engajamento/indicação de qualquer tenant por ID, e "
            "aprovar/reprovar/inativar qualquer parceiro."
        ),
        "exploitability": (
            "Sem pré-condição de configuração — comportamento padrão do código em qualquer ambiente. "
            "Único requisito: um JWT válido de qualquer usuário autenticado (gateway apenas valida "
            "assinatura/expiração, não checa role para esta rota). Endpoints /me/* (dashboard, "
            "convites, payout-info, comissões) são seguros — derivam partnerId do JWT via "
            "SecurityUtils.getPartnerId(), não de path param. POST /api/v1/partners e "
            "GET /cnpj/{cnpj} são intencionalmente públicos (solicitação de parceria e consulta de "
            "CNPJ) e não fazem parte deste achado."
        ),
    },
    {
        "id": "F2",
        "categoria": [4],
        "severidade": "baixa",
        "titulo": "Redis: default hardcoded \"test-password\" quando REDIS_PASSWORD não está setada",
        "arquivos": [
            ("compose.yaml", "27"),
            ("compose.yaml", "33"),
            ("billing-service/src/main/resources/application.yaml", "20"),
        ],
        "snippet": (
            "command: [\"redis-server\", \"--appendonly\", \"yes\", \"--requirepass\", \"${REDIS_PASSWORD:-test-password}\"]\n"
            "test: [\"CMD\", \"redis-cli\", \"-a\", \"${REDIS_PASSWORD:-test-password}\", \"ping\"]\n\n"
            "# billing-service/application.yaml\npassword: ${REDIS_PASSWORD:-test-password}"
        ),
        "por_que": (
            "Se a variável de ambiente REDIS_PASSWORD não estiver setada (esquecimento de deploy, "
            ".env incompleto), tanto o Redis quanto o billing-service sobem silenciosamente com a "
            "senha fixa \"test-password\" em vez de falhar no startup — um valor previsível, presente "
            "em texto claro no repositório. Diferente de JWT_SECRET/DB_PASS/ASAAS_*, que usam apenas "
            "${VAR} sem fallback."
        ),
        "exploitability": (
            "Requer: (a) REDIS_PASSWORD ausente no ambiente e (b) a porta do Redis alcançável pela "
            "rede (não apenas localhost). Em docker-compose local isolado o risco é baixo; em um "
            "deploy onde a rede do Redis for exposta sem essa env var, um atacante ganha acesso de "
            "escrita ao Redis usado pelo DistributedLock dos jobs do billing-service."
        ),
    },
    {
        "id": "F3",
        "categoria": [5],
        "severidade": "media",
        "titulo": "auth-service: HTML de e-mail transacional monta corpo com dado de usuário sem escaping",
        "arquivos": [
            ("auth-service/src/main/java/com/l/erp/authservice/services/EmailConsumerService.java", "104"),
            ("auth-service/src/main/java/com/l/erp/authservice/services/EmailConsumerService.java", "151"),
            ("auth-service/src/main/java/com/l/erp/authservice/services/EmailConsumerService.java", "183-184"),
            ("auth-service/src/main/java/com/l/erp/authservice/services/EmailConsumerService.java", "258"),
            ("auth-service/src/main/java/com/l/erp/authservice/services/EmailConsumerService.java", "301, 304, 380, 383"),
        ],
        "snippet": (
            "// linha 104\n\"<h2 style='color: #0056b3;'>Olá, %s!</h2>\".formatted(name)\n\n"
            "// linha 258 — texto livre digitado pelo parceiro, direto no HTML\n"
            "\"<blockquote>%s</blockquote>\".formatted(message)"
        ),
        "por_que": (
            "Todos os métodos send*Email montam o corpo HTML do e-mail via String.format/text block "
            "com %s, concatenando diretamente name, partnerName, clientName, clientCnpj e — o caso "
            "mais sensível — message (texto livre digitado pelo parceiro no follow-up de um convite, "
            "campo extra.get(\"message\") do payload Kafka), sem nenhuma função de escaping "
            "(nenhum import de HtmlUtils/OWASP encoder no arquivo). Um parceiro mal-intencionado "
            "pode injetar HTML/markup arbitrário no corpo do e-mail enviado a clientes/tenants."
        ),
        "exploitability": (
            "Requer que um parceiro cadastrado use o endpoint de follow-up "
            "(POST /me/convites/{referralId}/followup) com um campo message contendo markup HTML. "
            "A maioria dos clientes de e-mail bloqueia execução de script, mas HTML injection ainda "
            "permite phishing (links falsos, layout falsificado se passando por comunicação oficial) "
            "e quebra de layout — por isso média, não alta/crítica."
        ),
    },
    {
        "id": "F4",
        "categoria": [4],
        "severidade": "baixa",
        "titulo": "gateway: JWT_SECRET sem validação de tamanho mínimo no startup (inconsistente com auth-service)",
        "arquivos": [
            ("gateway/src/main/java/com/l/erp/gateway/security/SecurityFilter.java", "30-31"),
            ("auth-service/src/main/java/com/l/erp/authservice/infra/TokenService.java", "27-32"),
        ],
        "snippet": (
            "// gateway/SecurityFilter.java — sem @PostConstruct, sem checagem\n"
            "@Value(\"${api.security.jwt.secret}\")\nprivate String secret;\n\n"
            "// auth-service/TokenService.java — valida no startup\n"
            "@PostConstruct\nvoid validateSecret() {\n"
            "    if (secret == null || secret.length() < 32) throw new IllegalStateException(...);\n"
            "}"
        ),
        "por_que": (
            "auth-service falha rápido no startup se JWT_SECRET tiver menos de 32 caracteres; o "
            "gateway, que também assina/valida o mesmo tipo de token, não tem a mesma guarda. Não é "
            "um valor hardcoded (ambos usam apenas ${VAR}, sem default) — é uma inconsistência de "
            "hardening: se algum dia os dois serviços receberem valores diferentes de JWT_SECRET (erro "
            "de config), o gateway aceitaria um secret fraco silenciosamente em vez de recusar subir."
        ),
        "exploitability": (
            "Baixo impacto prático hoje, pois ambos os serviços leem a mesma env var obrigatória sem "
            "default — a falha do auth-service já impediria o sistema de funcionar. Vira relevante só "
            "se as duas envs divergirem (configuração incorreta por serviço)."
        ),
    },
    {
        "id": "F5",
        "categoria": [4],
        "severidade": "informativa",
        "titulo": "Grafana local com senha hardcoded no compose.yaml (achado pré-existente, já rastreado)",
        "arquivos": [
            ("compose.yaml", "139"),
        ],
        "snippet": "GF_SECURITY_ADMIN_PASSWORD=admin123",
        "por_que": (
            "Senha de admin do Grafana em texto claro no compose.yaml. Já identificado em auditoria "
            "anterior desta mesma base (scan de secrets vazados, 2026-08-11); reconfirmado presente. "
            "Escopo é infra de observabilidade local/dev, não credencial de dado de cliente."
        ),
        "exploitability": (
            "Só explorável se a porta 3000 (Grafana) estiver exposta a uma rede não confiável — em "
            "docker-compose local isolado o risco é nulo."
        ),
    },
    {
        "id": "F6",
        "categoria": [1],
        "severidade": "informativa",
        "titulo": "cadastro-service / partner-service: /actuator/loggers sem autenticação (achado pré-existente, já rastreado)",
        "arquivos": [
            ("cadastro-service/src/main/java/com/l/erp/cadastroservice/infra/filter/InternalRequestFilter.java", "public-exempt paths"),
            ("partner-service/src/main/java/com/l/erp/partnerservice/infra/filter/InternalRequestFilter.java", "72-73"),
        ],
        "snippet": "if (path.startsWith(\"/actuator/loggers\")) return true; // isento de auth",
        "por_que": (
            "/actuator/loggers permite ler e alterar o nível de log em runtime sem autenticação, "
            "isento deliberadamente para um painel de diagnóstico interno. Já mapeado em auditoria "
            "anterior (F2 do projeto, severidade LOW) — não é roteado pelo gateway, só acessível se a "
            "porta do serviço for alcançada diretamente."
        ),
        "exploitability": (
            "Requer acesso direto à porta do serviço (8086/8087), não exposta via gateway/rotas "
            "públicas. Risco interno, já registrado como item de backlog de segurança."
        ),
    },
]

# ---------------------------------------------------------------------------
# PONTOS FORTES (verified-correct) — prova de cobertura da auditoria.
# ---------------------------------------------------------------------------
STRENGTHS = [
    {
        "titulo": "cadastro-service: isolamento de tenant end-to-end consistente",
        "evidencia": (
            "TenantInterceptor (OncePerRequestFilter) popula TenantContext (ThreadLocal) a partir do "
            "header X-Tenant-Id em toda requisição; TenantFilterAspect (@Before via AspectJ) ativa o "
            "filtro Hibernate @Filter/@FilterDef definido em BaseTenantEntity (tenant_id = :tenantId) "
            "em toda chamada de service/repository, cobrindo listagens/buscas/agregações. Os 4 pontos "
            "de acesso por chave primária (findById cru, não coberto pelo @Filter — limitação "
            "documentada no próprio Javadoc do aspecto) em CondicaoPagamentoService.java:127, "
            "DepositoService.java:109, GrupoClienteService.java:125 e PessoaService.java:89 têm todos "
            "guarda manual explícita logo em seguida (if (!entity.getTenantId().equals(tenantID)) "
            "throw BusinessException(TENANT_ASSOC_ERROR, BAD_REQUEST)). O único @DeleteMapping do "
            "serviço (ClienteController) usa deleteByIdAndTenantId (tenant já embutido na query)."
        ),
    },
    {
        "titulo": "InternalRequestFilter: gateway como único ponto de confiança para headers de tenant",
        "evidencia": (
            "SecurityUtils.getCurrentTenantId() em cadastro-service lê o header X-Tenant-Id "
            "diretamente — isso só é seguro porque InternalRequestFilter (presente em cadastro-service "
            "e partner-service) valida, em toda rota não-pública, um segredo compartilhado "
            "(internal.gateway.secret) via comparação de tempo constante (MessageDigest.isEqual) mais "
            "a presença do header X-User-Id, garantindo que só o gateway (ou quem souber o segredo) "
            "alcança o serviço diretamente."
        ),
    },
    {
        "titulo": "billing-service: SubscriptionController — toda operação cross-tenant é gated por authority",
        "evidencia": (
            "listar (com filtro opcional de tenantId), mrr, reprocessar, cobrancas e cancelarAdmin — "
            "todos anotados @PreAuthorize(\"hasAuthority('ASSINATURA_MANAGE')\"). O endpoint "
            "self-service (POST /me/cancel) deriva o tenant do header X-Tenant-Id, nunca de um ID no "
            "path/body — nenhuma superfície de IDOR nesse controller. Os findById internos de "
            "SubscriptionService (linhas 162, 177, 185, 198) só são alcançados a partir desses métodos "
            "admin-gated."
        ),
    },
    {
        "titulo": "auth-service: RBAC consistente em UserController/AttributionsController/RoleController/PermissionController",
        "evidencia": (
            "Todo endpoint administrativo cross-tenant (createUser, updateUserById, "
            "updateUserStatusById, unlockUserById, getUserById, searchUsers, getUserRoles, "
            "assignRolesToUser, removeRoleFromUser em UserController/AttributionsController; "
            "createRole/updateRole/deleteRole/assignPermissions em RoleController; CRUD de "
            "PermissionController) está anotado @PreAuthorize(\"hasRole('APP_OWNER') and "
            "hasAuthority('...')\"). Os wrappers usados pelo portal do tenant "
            "(updateUserForTenant/updateUserStatusForTenant/unlockUserForTenant em UserService) "
            "chamam assertUserInTenant (findByIdAndTenantId) antes de qualquer operação — IDOR-safe "
            "por construção, confirmado também pelos testes em TenantSecurityControllerTest.java "
            "(assignRolesRejectsIdMismatch, assignPermissionsRejectsIdMismatch)."
        ),
    },
    {
        "titulo": "Frontends Angular: nenhum uso de innerHTML/bypassSecurityTrust/eval em 318 arquivos",
        "evidencia": (
            "Varredura completa dos 3 apps (85 .html + 233 .ts) não encontrou nenhuma ocorrência de "
            "[innerHTML], DomSanitizer/bypassSecurityTrust*, SafeHtml/SafeUrl/SafeResourceUrl, "
            "Renderer2.setProperty(...,'innerHTML',...), eval( ou new Function(, e nenhuma lib de "
            "markdown/DOMPurify instalada. Toda interpolação de dado dinâmico usa {{ }} (escapa por "
            "padrão) ou property binding de atributo ([href]/[src]) apontando para URLs de resposta "
            "de API (Asaas: fatura, boleto, PIX), sanitizadas automaticamente pelo Angular "
            "(SecurityContext.URL/RESOURCE_URL)."
        ),
    },
    {
        "titulo": "Segredos reais sempre via variável de ambiente, sem fallback hardcoded",
        "evidencia": (
            "JWT_SECRET, DB_USER/DB_PASS, KAFKA_BROKERS, EMAIL_USER/EMAIL_PASSWORD, "
            "ASAAS_API_KEY/ASAAS_WEBHOOK_TOKEN e INTERNAL_GATEWAY_SECRET usam apenas ${VAR} (sem "
            "default) em todos os 7 serviços com application.yaml/.properties. auth-service valida "
            "tamanho mínimo do JWT_SECRET (32 chars) via @PostConstruct em TokenService.java. Nenhum "
            "Dockerfile (dos 7) embute ARG/ENV com valor de segredo. git log dos últimos 20 dias não "
            "traz nenhum segredo real commitado, apenas constantes de teste (test-secret-*, "
            "test-internal-secret) usadas só em *Test.java."
        ),
    },
    {
        "titulo": "fiscal-service: categoria 1 (isolamento de tenant) não aplicável por design",
        "evidencia": (
            "/fiscal/calcular é determinístico e sem persistência (não há tabela por tenant a "
            "isolar). A única superfície tenant-sensível é o feature flag de split payment "
            "(fiscal.split-payment.tenants, um allowlist de tenants elegíveis lido de config, não de "
            "dado de outro tenant), default desligado — condizente com o registrado em memória de "
            "sessões anteriores."
        ),
    },
    {
        "titulo": "Frontends: nenhum gate de UI condicionado a papel/permissão em lugar nenhum",
        "evidencia": (
            "Varredura sistemática de isAdmin/isOwner/canEdit/hasRole/hasPermission/hasAuthority e de "
            "todos os *ngIf/@if (250+ ocorrências) nos 3 apps não encontrou nenhum gate de UI baseado "
            "em papel — toda condicional é estado de domínio/formulário. Os tipos de resposta de login "
            "não armazenam roles/authorities/isOwner. Isso confirma que a arquitetura de autorização é "
            "deliberadamente backend-only (o front mostra os mesmos menus a qualquer usuário "
            "autenticado e confia 100% no @PreAuthorize do Spring Security) — um modelo válido, mas "
            "que exige que TODO endpoint sensível tenha sua própria guarda no backend, o que é "
            "justamente o que falha em F1 (partner-service)."
        ),
    },
]
