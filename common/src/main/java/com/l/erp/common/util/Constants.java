package com.l.erp.common.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

public class Constants {

    private Constants(){
        //construtor vazio
    }

    public static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("10.00");

    /** Ator usado em auditoria de ações do sistema (eventos Kafka, crons) — sem usuário logado. */
    public static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    public static final String EMAIL  = "email";
    public static final String ISSUER  = "L-ERP-auth-service";

    public static final String STATUS = "status";
    public static final String STATUS_PENDENTE  = "PENDENTE";
    public static final String STATUS_ATIVO     = "ATIVO";
    public static final String STATUS_REPROVADO = "REPROVADO";
    public static final String STATUS_INATIVO   = "INATIVO";

    public static final String SYSTEM = "SYSTEM";
    public static final String ADMIN = "ADMIN";
    public static final String ATIVO = "ATIVO";
    public static final String INACTIVE = "INACTIVE";
    public static final String BLOCKED = "BLOCKED";
    public static final String UNBLOCKED = "UNBLOCKED";

    public static final String UTF8 = "UTF-8";
    public static final String EQ_ERP = "Equipe Syax";

    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR   = "ERROR";
    public static final String FAILED  = "FAILED";

    public static final String TENANT = "TENANT";
    public static final String USER = "USER";
    public static final String ROLE = "ROLE";
    public static final String PERMISSION = "PERMISSION";
    public static final String USER_ROLE = "USER_ROLE";

    public static final String INSERT = "INSERT";
    public static final String ASSIGN = "ASSIGN";
    public static final String UPDATE = "UPDATE";
    public static final String CANCEL = "CANCEL";
    public static final String DELETE = "DELETE";
    public static final String TENANT_NOT_FOUND= "Tenant não Encontrado!";
    public static final String TENANT_CREATION = TENANT + "_" + INSERT;
    public static final String TENANT_UPDATE = TENANT + "_" + UPDATE;
    public static final String TENANT_CANCEL= "TENANT" + "_" + CANCEL;
    public static final String TENANT_SUBSCRIPTION_ACTIVATED = TENANT + "_SUBSCRIPTION_ACTIVATED";

    public static final String ASSINATURA = "ASSINATURA";
    public static final String ASSINATURA_REPROCESS = "ASSINATURA_REPROCESS";
    public static final String ASAAS_EVENT_PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String ASSINATURA_CANCEL_ADMIN = "ASSINATURA_CANCEL_ADMIN";
    public static final String ASSINATURA_NOT_FOUND = "Assinatura não encontrada";
    /** Status de cobrança do Asaas que contam como "pago" (webhook e reconciliação). */
    public static final java.util.Set<String> ASAAS_PAID_STATUSES = java.util.Set.of("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH");

    public static final String USER_CREATION = USER + "_" + INSERT;
    public static final String USER_UPDATE = USER + "_" + UPDATE;
    public static final String USER_UNLOCK = USER + "_UNLOCK";
    public static final String USER_NOT_FOUND= "Usuário não Encontrado";
    public static final String USER_NOT_LOCKED = "Usuário não está bloqueado";

    public static final String USER_HAS_OWNER_MARKER= "Usuário não pode ser cancelado/excluído pois possui um marker de proprietário";

    public static final String USER_EMAIL_NOT_CORRECT = "Credenciais Inválidas - Senha ou Email incorretos";
    public static final String USER_INACTIVE = "Usuário Inativo";

    public static final String PERMISSION_CREATION = PERMISSION + "_" + INSERT;
    public static final String PERMISSION_UPDATE = PERMISSION + "_" + UPDATE;
    public static final String PERMISSION_DELETE = PERMISSION + "_" + DELETE;
    public static final String PERMISSION_NOT_FOUND = "Permissão não encontrada";

    public static final String ROLE_CREATION = ROLE + "_" + INSERT;
    public static final String ROLE_UPDATE = ROLE + "_" + UPDATE;
    public static final String ROLE_DELETE = ROLE + "_" + DELETE;
    public static final String ROLE_NOT_FOUND = "Role não encontrada!";

    public static final String ROLE_PERMISSION = ROLE + "_" + PERMISSION;
    public static final String ROLE_PERMISSION_CREATION = ROLE_PERMISSION + "_" + INSERT;
    public static final String ROLE_PERMISSION_ASSIGNMENT = ROLE_PERMISSION + "_" + ASSIGN;
    public static final String ROLE_PERMISSION_UPDATE = ROLE_PERMISSION + "_" + UPDATE;
    public static final String ROLE_PERMISSION_DELETE = ROLE_PERMISSION + "_" + DELETE;

    public static final String USER_ROLE_CREATION = USER_ROLE + "_" + INSERT;
    public static final String USER_ROLE_UPDATE   = USER_ROLE + "_" + UPDATE;
    public static final String USER_ROLE_DELETE   = USER_ROLE + "_" + DELETE;


    public static final String USUARIO_NAO_AUTENTICADO= "Usuário não autenticado";
    public static final String USUARIO_UUID_NAO_ENCONTRADO= "Usuário logado não possuí UUID! Contate um administrador do sistema.";

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    public static final String MAXIMUM_PAYMENT_VALUE_PERCENT = "100.00";
    public static final String _ID = " - id: ";

    // Eventos de Login/Auditoria
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String LOGIN_SUCCESS = LOGIN + "_" + SUCCESS;
    public static final String LOGIN_FAILED = LOGIN + "_" + FAILED;
    public static final String LOGIN_LOCKED = LOGIN + "_LOCKED";
    public static final String LOGIN_USER_INACTIVE = LOGIN + "_USER_INACTIVE";
    public static final String USER_UNLOCKED = "USER_UNLOCKED";

    // Tenant Login
    public static final String TENANT_LOGIN = "TENANT_LOGIN";
    public static final String TENANT_LOGIN_SUCCESS = TENANT_LOGIN + "_" + SUCCESS;
    public static final String TENANT_LOGIN_FAILED = TENANT_LOGIN + "_" + FAILED;
    public static final String TENANT_NOT_ACTIVE = "Empresa inativa ou bloqueada. Entre em contato com o suporte.";
    public static final String TENANT_CNPJ_NOT_FOUND = "Empresa não encontrada. Verifique o CNPJ informado.";

    //GRUPO C
    public static final String GROUP_C = "GROUP_CLIENT";
    public static final String GROUP_C_NOT_FOUND = "Grupo de cliente não encontrado";
    public static final String GROUP_C_ALREADY_EXISTS = "Já existe um grupo de cliente com este nome";
    public static final String AUDIT_TOPIC = "audit.events";
    public static final String GROUP_C_CREATION = GROUP_C + "_" + INSERT;
    public static final String GROUP_C_UPDATE = GROUP_C + "_" + UPDATE;

    public static final String TENANT_ASSOC_ERROR = TENANT + "_" + ERROR + "_ Voce nao está autorizado a realizar essa operacao";

    public static final String DEPOSITO = "DEPOSITO";
    public static final String DEPOSITO_CREATION = DEPOSITO + "_" + INSERT;
    public static final String DEPOSITO_UPDATE = DEPOSITO +"_" + UPDATE;
    public static final String DEPOSITO_NOT_FOUND = "Deposito nao encontrado!";
    public static final String DEPOSITO_ALREADY_EXISTS = "Já existe um deposito com este nome";

    public static final String COND_PAG = "CONDICAO_PAGAMENTO";
    public static final String COND_PAG_CREATION = COND_PAG + "_" + INSERT;
    public static final String COND_PAG_UPDATE = COND_PAG +"_" + UPDATE;
    public static final String COND_PAG_NOT_FOUND = "Condição de Pagamento nao encontrada!";
    public static final String COND_PAG_ALREADY_EXISTS = "Já existe uma Condição de Pagamento com este nome";

    public static final String COND_PAG_PAR = "CONDICAO_PAGAMENTO_PARCELA";
    public static final String COND_PAG_PAR_CREATION = COND_PAG_PAR + "_" + INSERT;
    public static final String COND_PAG_PAR_UPDATE = COND_PAG_PAR +"_" + UPDATE;

    public static final String PESSOA = "PESSOA";
    public static final String PESSOA_CREATION = PESSOA + "_" + INSERT;
    public static final String PESSOA_UPDATE = PESSOA +"_" + UPDATE;
    public static final String PESSOA_DELETE = PESSOA +"_" + DELETE;
    public static final String PESSOA_NOT_FOUND = "Pessoa nao encontrada!";
    public static final String PESSOA_ALREADY_EXISTS = "Já existe uma Pessoa com este nome";

    public static final String END = "ENDERECO";
    public static final String END_CREATION = END + "_" + INSERT;
    public static final String END_UPDATE = END +"_" + UPDATE;
    public static final String END_NOT_FOUND = "Endereço nao encontrada!";
    public static final String END_ALREADY_EXISTS = "Já existe um Endereço com este nome";

    public static final String CONTATO = "CONTATO";
    public static final String CONTATO_CREATION = CONTATO + "_" + INSERT;
    public static final String CONTATO_UPDATE = CONTATO +"_" + UPDATE;
    public static final String CONTATO_NOT_FOUND = "Contato nao encontrada!";
    public static final String CONTATO_ALREADY_EXISTS = "Já existe um Contato com este nome";

    public static final String ESTABELECIMENTO = "ESTABELECIMENTO";
    public static final String ESTABELECIMENTO_CREATION = ESTABELECIMENTO + "_" + INSERT;
    public static final String ESTABELECIMENTO_UPDATE = ESTABELECIMENTO + "_" + UPDATE;
    public static final String ESTABELECIMENTO_NOT_FOUND = "Estabelecimento nao encontrado!";
    public static final String ESTABELECIMENTO_MATRIZ_NAO_ENCONTRADA = "Matriz nao encontrada para esta Pessoa!";
    public static final String ESTABELECIMENTO_APENAS_PJ = "Apenas Pessoa Juridica pode ter Estabelecimentos!";
    public static final String ESTABELECIMENTO_PROPRIO_JA_DEFINIDO = "Este tenant ja possui um estabelecimento proprio definido!";
    public static final String ESTABELECIMENTO_MATRIZ_NAO_PODE_SER_INATIVADA = "A matriz nao pode ser inativada, pois nao e possivel criar outra em seu lugar!";
    public static final String ESTABELECIMENTO_PROPRIO_NAO_ENCONTRADO = "Estabelecimento proprio do tenant nao encontrado!";

    public static final String VENDEDOR = "VENDEDOR";
    public static final String VENDEDOR_CREATION = VENDEDOR + "_" + INSERT;
    public static final String VENDEDOR_UPDATE = VENDEDOR +"_" + UPDATE;
    public static final String VENDEDOR_NOT_FOUND = "Vendedor não encontrado!";
    public static final String VENDEDOR_ALREADY_EXISTS = "Já existe um Vendedor com este nome";

    public static final String CLIENTE = "CLIENTE";
    public static final String CLIENTE_CREATION = CLIENTE + "_" + INSERT;
    public static final String CLIENTE_UPDATE = CLIENTE +"_" + UPDATE;
    public static final String CLIENTE_DELETE = CLIENTE +"_" + DELETE;
    public static final String CLIENTE_NOT_FOUND = "Cliente não encontrado!";
    public static final String CLIENTE_ALREADY_EXISTS = "Já existe um Cliente com este nome";

    public static final String PROD_CAT = "PRODUTO_CATEGORIA";
    public static final String PROD_CAT_CREATION = PROD_CAT + "_" + INSERT;
    public static final String PROD_CAT_UPDATE = PROD_CAT +"_" + UPDATE;
    public static final String PROD_CAT_NOT_FOUND = "Categoria de Produto não encontrada!";
    public static final String PROD_CAT_ALREADY_EXISTS = "Já existe uma Categoria de Produto com este nome";

    public static final String FORNECEDORES = "FORNECEDORES";
    public static final String FORNECEDORES_CREATION = FORNECEDORES + "_" + INSERT;
    public static final String FORNECEDORES_UPDATE = FORNECEDORES +"_" + UPDATE;
    public static final String FORNECEDORES_NOT_FOUND = "Fornecedor não encontrado!";
    public static final String FORNECEDORES_ALREADY_EXISTS = "Já existe um Fornecedor com este nome";
    public static final String FORNECEDORES_NOT_FOUND_ID = "Fornecedor não encontrado - id: ";

    public static final String TRANSPORTADORA = "TRANSPORTADORA";
    public static final String TRANSPORTADORA_CREATION = TRANSPORTADORA + "_" + INSERT;
    public static final String TRANSPORTADORA_UPDATE = TRANSPORTADORA +"_" + UPDATE;
    public static final String TRANSPORTADORA_NOT_FOUND = "Transportadora não encontrada!";
    public static final String TRANSPORTADORA_ALREADY_EXISTS = "Já existe uma Transportadora com este nome";

    public static final String TABELA_PRECO = "TABELA_PRECO";
    public static final String TABELA_PRECO_CREATION = TABELA_PRECO + "_" + INSERT;
    public static final String TABELA_PRECO_UPDATE = TABELA_PRECO +"_" + UPDATE;
    public static final String TABELA_PRECO_NOT_FOUND = "Tabela de Preco não encontrada!";
    public static final String TABELA_PRECO_ALREADY_EXISTS = "Já existe uma Tabela de Preco com este nome";
    public static final String TABELA_PRECO_PADRAO_ALREADY_EXISTS = "Já existe uma Tabela de Preco Padrao pra esse Tenant";
    public static final String TABELA_PRECO_VIGENCIA_INVALIDA = "Início de vigência não pode ser posterior ao fim de vigência";
    public static final String PRECO_NAO_RESOLVIDO = "Não foi possível resolver um preço para o produto informado";

    public static final String GRP_C_TABELA_PRECO = "TABELA_PRECO_GRUPO_CLIENTE";
    public static final String GRP_C_TABELA_PRECO_ASSOCIACAO = GRP_C_TABELA_PRECO+"_ASSOCIACAO";

    public static final String PRODUTO = "PRODUTO";
    public static final String PRODUTO_CREATION = PRODUTO + "_" + INSERT;
    public static final String PRODUTO_UPDATE = PRODUTO +"_" + UPDATE;
    public static final String PRODUTO_DELETE = PRODUTO +"_" + DELETE;
    public static final String PRODUTO_NOT_FOUND = "Produto não encontrado!";
    public static final String PRODUTO_ALREADY_EXISTS = "Já existe um Produto com este nome";
    // Suporte a serviço (Produto.tipo) — cadastro-service
    public static final String PRODUTO_NCM_OBRIGATORIO_MERCADORIA = "NCM é obrigatório para produto do tipo MERCADORIA";
    public static final String PRODUTO_CODIGO_SERVICO_OBRIGATORIO =
            "Código de serviço é obrigatório para produto do tipo SERVICO";
    public static final String PRODUTO_CODIGO_SERVICO_APENAS_SERVICO =
            "Código de serviço só é permitido para produto do tipo SERVICO";
    // D4 (spec/o2c-vendas.md §8) — classificação tributária IBS/CBS do serviço (Anexo VIII),
    // exigida pelo fiscal-service (MotorFiscalService) sempre que codigoServico vem preenchido.
    public static final String PRODUTO_CLASS_TRIB_OBRIGATORIO_SERVICO =
            "Classificação tributária (cClassTrib) é obrigatória para produto do tipo SERVICO";
    public static final String PRODUTO_PRECO_VIGENCIA_INVALIDA =
            "Início de vigência do preço não pode ser posterior ao fim de vigência";
    public static final String PRODUTO_PRECO_VIGENCIA_SOBREPOSTA =
            "Já existe um preço vigente para esta Tabela de Preço no período informado";

    public static final String PLAN = "PLAN";
    public static final String PLAN_CREATION = PLAN + "_" + INSERT;
    public static final String PLAN_UPDATE = PLAN +"_" + UPDATE;
    public static final String PLAN_DELETE = PLAN +"_" + DELETE;
    public static final String PLAN_NOT_FOUND = "Plano não encontrado!";
    public static final String PLAN_ALREADY_EXISTS = "Já existe um Plano com este nome";

    //STATUS

    public static final String TRIAL = "TRIAL";
    public static final String CONVIDADO = "CONVIDADO";
    public static final String FOLLOWUP = "FOLLOWUP";
    public static final String ATIVADO = "ATIVADO";
    public static final String CONVERTIDO = "CONVERTIDO";
    public static final String PERDIDO = "PERDIDO";
    public static final String IGNORADO = "IGNORADO";

    // Status de webhook (billing.webhook_log)
    public static final String WEBHOOK_RECEBIDO = "RECEBIDO";
    public static final String WEBHOOK_PROCESSADO = "PROCESSADO";
    public static final String WEBHOOK_ERRO = "ERRO";

    /** Minutos em RECEBIDO após os quais um webhook é considerado preso (recovery job + gauge). */
    public static final int WEBHOOK_STUCK_MINUTES = 10;

    // Métricas Micrometer de webhook (expostas em /actuator/prometheus do billing)
    public static final String METRIC_WEBHOOK_PROCESSADO = "webhook_processado_total";
    public static final String METRIC_WEBHOOK_PENDENTE = "webhook_pendente";
    public static final String METRIC_TAG_EVENTO = "evento";
    public static final String METRIC_TAG_RESULTADO = "resultado";
    public static final String METRIC_RESULTADO_OK = "ok";
    public static final String METRIC_RESULTADO_DUPLICADO = "duplicado";
    public static final String METRIC_RESULTADO_IGNORADO = "ignorado";
    public static final String METRIC_RESULTADO_ERRO_TRANSITORIO = "erro_transitorio";
    public static final String METRIC_RESULTADO_ERRO_PERMANENTE = "erro_permanente";

    /** Segundos desde a última execução OK de um job agendado; -1 = nunca rodou com sucesso. */
    public static final String METRIC_JOB_SEGUNDOS_DESDE_OK = "job_segundos_desde_ok";
    public static final String METRIC_TAG_JOB = "job";
    public static final double METRIC_JOB_NUNCA_EXECUTADO = -1.0;

    // Tag de tenant nas métricas http.server.requests (load test estruturado do billing-service,
    // ver billing-service/loadtest/README.md). Cardinalidade só é aceitável porque o número de
    // tenants é limitado ao load test controlado — não usar tenant_id como label fora desse cenário.
    public static final String METRIC_TAG_TENANT = "tenant_id";

    //Parceiros
    public static final String PARCEIRO = "PARCEIRO";
    public static final String PARCEIRO_CREATION = PARCEIRO + "_" + INSERT;
    public static final String PARCEIRO_UPDATE = PARCEIRO +"_" + UPDATE;
    public static final String PARCEIRO_DELETE = PARCEIRO +"_" + DELETE;
    public static final String PARCEIRO_NOT_FOUND = "Parceiro não encontrado!";
    public static final String PARCEIRO_NOT_FOUND_EM = "{ERROR: Parceiro não encontrado}";
    public static final String PARCEIRO_CNPJ_ALREADY_EXISTS = "CNPJ já cadastrado";
    public static final String PARCEIRO_EMAIL_ALREADY_EXISTS = "E-mail já cadastrado";
    public static final String PARCEIRO_REFERRAL_CODE_ALREADY_EXISTS = "Código de parceiro já cadastrado";
    public static final String PARCEIRO_INACTIVATE = PARCEIRO + "_INACTIVATE";
    public static final String PARCEIRO_APPROVE = PARCEIRO + "_APPROVE";
    public static final String PARCEIRO_REJECT = PARCEIRO + "_REJECT";
    public static final String PARCEIRO_ID_NOT_FOUND = "PartnerId não encontrado no token";


    public static final String CONVITE = "CONVITE";
    public static final String CONVITE_SEND = CONVITE + "_SEND";
    public static final String CONVITE_RESEND = CONVITE + "_RESEND";
    public static final String CONVITE_NOT_FOUND = "Convite não encontrado";

    // Partner
    public static final String PARTNER = "PARTNER";
    public static final String PARTNER_LOGIN = "PARTNER_LOGIN";
    public static final String PARTNER_LOGIN_SUCCESS = PARTNER_LOGIN + "_" + SUCCESS;
    public static final String PARTNER_LOGIN_FAILED = PARTNER_LOGIN + "_" + FAILED;

    // Refresh Token
    public static final String TENANT_USER = "TENANT_USER";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String TOKEN_REFRESH_SUCCESS = TOKEN_REFRESH + "_" + SUCCESS;
    public static final String TOKEN_REFRESH_REUSE = TOKEN_REFRESH + "_REUSE_DETECTED";

    // Password reset (esqueci minha senha)
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_RESET_REQUESTED = PASSWORD_RESET + "_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = PASSWORD_RESET + "_COMPLETED";
    public static final String PASSWORD_RESET_ACTOR = "password-reset";
    public static final String EMAIL_TYPE_RESET_SENHA = "RESET_SENHA";
    public static final String SENHAS_NAO_CONFEREM = "Senhas não conferem";
    public static final String TOKEN_INVALIDO_EXPIRADO = "Token inválido ou expirado";

    // Bootstrap da role do owner do tenant (criação inicial automática)
    public static final String OWNER_ROLE_NAME = "PROPRIETARIO";
    public static final String SYSTEM_BOOTSTRAP = "system-bootstrap";

    // Proteção contra self-demotion / tenant órfão (AttributionsService)
    public static final String OWNER_ROLE_AUTO_REMOCAO =
            "Um usuário não pode remover a própria role de proprietário do tenant";
    public static final String OWNER_ROLE_ULTIMO_PROPRIETARIO =
            "Esta é a última role de proprietário do tenant — remova-a só depois de atribuir outro proprietário";
    public static final String OWNER_ROLE_CONCESSAO_NAO_AUTORIZADA =
            "Apenas um proprietário do tenant pode conceder a role de proprietário a um usuário";

    // Origem do cadastro (created_by)
    public static final String SELF_REGISTRATION = "self-registration";
    public static final String SELF_ACTIVATION = "self-activation";
    public static final String CRIAR_CONTA_GRATIS = "CRIAR_CONTA_GRATIS";

    // DLQ / log de erro de consumidores Kafka (audit.consumer_error_log)
    public static final String AUTH_SERVICE_NAME = "auth-service";
    public static final String TOPIC_SUBSCRIPTION_ACTIVATED = "billing.subscription.activated";
    public static final String AUTH_SERVICE_GROUP = "auth-service-group";

    // Headers de identidade injetados pelo gateway (gateway/SecurityFilter não depende de common)
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_IS_OWNER = "X-Is-Owner";
    public static final String HEADER_PARTNER_ID = "X-Partner-Id";
    public static final String HEADER_AUTHORITIES = "X-Authorities";
    // Segredo compartilhado gateway -> serviços internos (cadastro/partner/billing), prova de que
    // a request passou pelo gateway e não bateu direto no serviço (issue #62)
    public static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";
    public static final String HEADER_ASAAS_ACCESS_TOKEN = "asaas-access-token";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    // Chave do MDC que carrega o correlationId em toda a thread da requisição (log + corpo de erro).
    public static final String MDC_CORRELATION_ID = "correlationId";

    // Jobs agendados do billing (runner de diagnóstico #5) — chaves e status persistidos em
    // billing.job_execution. As chaves batem com as usadas na tela de jobs do admin.
    public static final String JOB_KEY_RECONCILIATION = "reconciliation";
    public static final String JOB_KEY_WEBHOOK_RECOVERY = "webhook-recovery";
    public static final String JOB_KEY_DUNNING = "dunning";
    public static final String JOB_KEY_COMMISSION_PAYOUT = "commission-payout";
    // Jobs de trial do auth (schedulers D+10/D+15) — persistidos em auth.job_execution
    public static final String JOB_KEY_TRIAL_D10 = "trial-d10";
    public static final String JOB_KEY_TRIAL_D15 = "trial-d15";
    public static final String JOB_STATUS_RUNNING = "EXECUTANDO";
    public static final String JOB_STATUS_OK = "OK";
    public static final String JOB_STATUS_ERROR = "ERRO";

    // Regime tributário do emitente (motor fiscal — Fin.md §1.4)
    public static final String REGIME_MEI = "MEI";
    public static final String REGIME_SIMPLES_NACIONAL = "SIMPLES_NACIONAL";
    public static final String REGIME_LUCRO_PRESUMIDO = "LUCRO_PRESUMIDO";
    public static final String REGIME_LUCRO_REAL = "LUCRO_REAL";

    // Regimes de fiscal.regime_dif_ncm que o motor trata por NOME, não só pelo percentual de
    // redução (§1.4.2 Passo 2). Os demais (ANEXO_*) entram só pelo percentual_reducao da linha.
    public static final String REGIME_DIF_PADRAO = "PADRAO";
    public static final String REGIME_DIF_MONOFASICO = "MONOFASICO";

    // Códigos de erro do motor fiscal (Fin.md §1.4.9)
    public static final String FISCAL_CFOP_NAO_ENCONTRADO = "FISCAL_CFOP_NAO_ENCONTRADO";
    public static final String FISCAL_REGIME_SEM_ALIQUOTA_CBS = "FISCAL_REGIME_SEM_ALIQUOTA_CBS";
    public static final String FISCAL_NCM_NAO_ENCONTRADO = "FISCAL_NCM_NAO_ENCONTRADO";
    // Nao ha aliquota de IBS para a data de competencia. Substituiu o antigo
    // FISCAL_MUNICIPIO_SEM_ALIQUOTA_IBS: desde o fiscal-023 existe a linha de REFERENCIA nacional
    // ('0000000') cobrindo 2026-2033, entao municipio sem linha propria calcula normalmente e o que
    // resta descoberto e o ANO — ex. 2035, fora da curva publicada pelo Senado.
    public static final String FISCAL_VIGENCIA_SEM_COBERTURA = "FISCAL_VIGENCIA_SEM_COBERTURA";
    public static final String FISCAL_SPLIT_SEM_FORMA_PAGAMENTO = "FISCAL_SPLIT_SEM_FORMA_PAGAMENTO";
    // Produto (ncm) e serviço (codigoServico) são mutuamente exclusivos: errar isso muda o destino
    // do IBS e o regime aplicado — é erro de entrada, nunca tributo calculado no escuro.
    public static final String FISCAL_NCM_OU_SERVICO_OBRIGATORIO = "FISCAL_NCM_OU_SERVICO_OBRIGATORIO";
    public static final String FISCAL_NCM_E_SERVICO_CONFLITANTES = "FISCAL_NCM_E_SERVICO_CONFLITANTES";
    public static final String FISCAL_CCLASSTRIB_OBRIGATORIO = "FISCAL_CCLASSTRIB_OBRIGATORIO";
    public static final String FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO = "FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO";
    // NFS-e é documento de serviço; NF-e/NFC-e, de produto. Documento trocado muda o destino do
    // IBS (local da prestação x município do destinatário) — erro de entrada, nunca fallback.
    public static final String FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL = "FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL";
    // Desconto incondicional não pode zerar nem inverter a operação: base <= 0 é erro de entrada.
    public static final String FISCAL_DESCONTO_MAIOR_QUE_OPERACAO = "FISCAL_DESCONTO_MAIOR_QUE_OPERACAO";

    // Aviso do fallback para PADRAO: o codigo nao tem linha de regime, entao o motor tributa com
    // aliquota cheia e SEGUE (nao bloqueia). Vai para o log (WARN) e para a memoria de calculo —
    // quem consome precisa saber que o numero saiu de dado faltando, nao de regra fiscal.
    // Placeholders: 1o o tipo do codigo (NCM / cClassTrib), 2o o codigo em si.
    public static final String FISCAL_AVISO_REGIME_PADRAO =
            "AVISO: sem regime cadastrado para %s '%s' — tributado com alíquota CHEIA (PADRAO). "
                    + "Se o item é desonerado pela LC 214, falta carga fiscal";
    public static final String FISCAL_TIPO_CODIGO_NCM = "NCM";
    public static final String FISCAL_TIPO_CODIGO_CCLASSTRIB = "cClassTrib";

    // Codigo IBGE sentinela da linha-base de fiscal.aliq_ibs_municipio: a aliquota de REFERENCIA
    // (fixada pelo Senado) e uniforme por tipo de ente, entao ela vive numa linha por ano em vez de
    // ser replicada nos 5.570 municipios. Municipio com aliquota PROPRIA tem linha propria e vence.
    // Nao existe municipio com codigo '0000000', logo nao colide com a UNIQUE (ibge, ano).
    public static final String FISCAL_IBGE_REFERENCIA_NACIONAL = "0000000";

    // Codigo NCM/NBS sentinela da linha-base de fiscal.matriz_tributaria (fatia 3b): a aliquota
    // INTERNA geral do ICMS e carregada uma vez por UF (27 linhas), nao por NCM — excecoes por
    // produto entram como override so quando um cliente real reclamar. 8 digitos (nao 7, que e o
    // sentinela de municipio acima) porque o NCM tem 8 digitos e o NBS tem 9.
    public static final String FISCAL_NCM_NBS_FALLBACK = "00000000";

    // Aviso do fallback para a aliquota de referencia: o municipio de destino nao tem linha propria,
    // entao o motor usa a referencia e SEGUE (nao bloqueia — a referencia e a aliquota legal de quem
    // nao legislou a propria). Se o ente legislou e a carga nao tem, o imposto sai errado: por isso
    // o aviso vai pro log (WARN) e pra memoria de calculo. Placeholder: o codigo IBGE do destino.
    public static final String FISCAL_AVISO_ALIQUOTA_REFERENCIA =
            "AVISO: município '%s' sem alíquota IBS própria cadastrada — aplicada a alíquota de "
                    + "REFERÊNCIA nacional. Confirme se o ente publicou alíquota própria";

    // Valores aceitos em MotorFiscalRequest.tipoDocumento (Fin.md §1.4.10). CT-e fica FORA da
    // coerência produto x serviço: transporte tem regra própria que o motor ainda não trata.
    public static final String FISCAL_TIPO_DOC_NFE = "NFe";
    public static final String FISCAL_TIPO_DOC_NFCE = "NFCe";
    public static final String FISCAL_TIPO_DOC_NFSE = "NFSe";

    // Linha da memória de cálculo da base composta (LC 214 art. 12, §2º): frete, seguro e demais
    // despesas acessórias ENTRAM na base; desconto incondicional SAI. Só é emitida quando algum
    // componente vem no request — sem eles o valor da operação já É a base e a linha seria ruído.
    // Placeholders, na ordem: tributável, operação, frete, seguro, acessórias, desconto.
    public static final String FISCAL_MEMORIA_BASE_COMPOSTA =
            "Valor tributável: %s (operação %s + frete %s + seguro %s + acessórias %s - desconto %s)";

    // Origem do produto (MotorFiscalRequest.origemProduto). O tratamento da Zona Franca de Manaus
    // (LC 214) NÃO está implementado: o motor tributa como nacional e AVISA — mesmo padrão do
    // aviso de PADRAO, porque o erro é contra o contribuinte e não pode sair calado.
    public static final String FISCAL_ORIGEM_ZFM = "ZFM";
    public static final String FISCAL_AVISO_ORIGEM_ZFM =
            "AVISO: origem 'ZFM' informada — tratamento da Zona Franca de Manaus não implementado; "
                    + "item tributado como NACIONAL";

    // Fatia 3c — legado (ICMS/ISS) durante a transição 2026-2033 (spec/motor-fiscal-proximos-passos.md §3)
    // Produto sem UF de origem/destino DURANTE A TRANSICAO: sem elas a matriz de ICMS nao tem como
    // resolver a aliquota interna. So exigido quando ha ICMS remanescente (pctRemanescente > 0) —
    // em 2033 (regime permanente) a checagem nem roda.
    public static final String FISCAL_UF_OBRIGATORIA_TRANSICAO = "FISCAL_UF_OBRIGATORIA_TRANSICAO";
    // Nenhuma linha na matriz (nem tenant, nem nacional, nem fallback) para o par de UF/NCM: o
    // motor devolve 400 em vez de assumir ICMS zero — mesmo princípio de FISCAL_VIGENCIA_SEM_COBERTURA.
    public static final String FISCAL_ICMS_SEM_COBERTURA = "FISCAL_ICMS_SEM_COBERTURA";
    // Idem para ISS: nem o município nem a referência nacional tem linha para o item.
    public static final String FISCAL_ISS_SEM_COBERTURA = "FISCAL_ISS_SEM_COBERTURA";
    // PIS/COFINS ainda vigentes (só 2026): por decisão de escopo (item 7.9), o motor nunca calcula
    // o tributo — não é dado faltando. O art. 348 da LC 214/2025 dispensa o recolhimento de
    // IBS/CBS no ano de teste para quem cumprir as obrigações acessórias (§1º) e exige PIS/COFINS
    // integral do mesmo jeito (§2º); só o contribuinte que descumprir recolhe IBS/CBS e compensa
    // contra PIS/COFINS — cálculo de apuração multi-competência, fora do escopo de uma nota isolada.
    public static final String FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA =
            "AVISO: PIS/COFINS ainda vigentes nesta competência (art. 348 da LC 214/2025) — "
                    + "recolhimento e eventual compensação com IBS/CBS são apurados fora do motor fiscal";

    // Fatia 3e — retenção na fonte (ISS/IRRF/CSRF/INSS), dentro do motor (spec §3, decisão de
    // 30/07/2026). Tributo de fiscal.retencao_config; mesmos valores de Constants.TRIBUTO_*.
    public static final String TRIBUTO_IRRF = "IRRF";
    public static final String TRIBUTO_CSRF = "CSRF"; // PIS/COFINS/CSLL retidos de forma unificada (IN RFB 1234/2012)
    public static final String TRIBUTO_INSS = "INSS"; // cessão de mão de obra/empreitada (IN RFB 971/2009)
    // Retenção só existe em operação de serviço: produto não tem ISS, e IRRF/CSRF/INSS aqui são
    // sobre pagamento de serviço a PJ. Declarar retenção numa nota de produto é erro de entrada.
    public static final String FISCAL_RETENCAO_APENAS_SERVICO = "FISCAL_RETENCAO_APENAS_SERVICO";
    // Tributo declarado (reterIrrf/reterCsrf/reterInss) sem linha em fiscal.retencao_config (nem
    // do tenant, nem nacional): o motor devolve 400 em vez de deixar de reter calado.
    public static final String FISCAL_TRIBUTO_SEM_ALIQUOTA_RETENCAO = "FISCAL_TRIBUTO_SEM_ALIQUOTA_RETENCAO";

    // fiscal.aliquota_regime_tributo (item 7.7): override por regime que o percentual único de
    // regime_dif_ncm/regime_cclasstrib não expressa — redução isolada por tributo (Prouni, art.
    // 308, zera só CBS) ou alíquota somada em valor ABSOLUTO (serviço financeiro, art. 233).
    // Distintos de TRIBUTO_IRRF/CSRF/INSS acima, que são de retenção, não do cálculo de saída.
    public static final String FISCAL_TRIBUTO_IBS = "IBS";
    public static final String FISCAL_TRIBUTO_CBS = "CBS";
    public static final String FISCAL_TRIBUTO_TOTAL = "TOTAL";
    public static final String FISCAL_TIPO_PERCENTUAL_REDUCAO = "PERCENTUAL_REDUCAO";
    public static final String FISCAL_TIPO_ALIQUOTA_ABSOLUTA = "ALIQUOTA_ABSOLUTA";

    // Art. 57 §7º da LC 214/2025 (incluído pela LC 227/2026) — revenda de bem que não gerou
    // crédito na entrada (uso e consumo pessoal) pode excluir da base de saída o valor de
    // aquisição, até o limite do valor da venda. Só existe do lado da SAÍDA — a vedação em si
    // já é decidida na entrada (item 4), então declarar o flag numa entrada não faz sentido.
    public static final String FISCAL_VEDACAO_57_APENAS_SAIDA = "FISCAL_VEDACAO_57_APENAS_SAIDA";
    // Flag ligada sem o valor de aquisição: sem ele não dá pra calcular a exclusão — 400 em vez
    // de tratar como zero (o que devolveria o mesmo imposto de uma venda comum, calado).
    public static final String FISCAL_VEDACAO_57_SEM_VALOR_AQUISICAO = "FISCAL_VEDACAO_57_SEM_VALOR_AQUISICAO";
    // Memória de cálculo da exclusão (art. 57 §7º). Placeholders: valor excluído, valor de aquisição.
    public static final String FISCAL_MEMORIA_VEDACAO_57 =
            "Art. 57 §7º LC 214/2025: exclusão de %s da base de cálculo (bem sem crédito na "
                    + "entrada, valor de aquisição %s)";

    // O2C — Pedido de venda (operacoes-service, schema vendas — spec/o2c-vendas.md §4/§7/§8, Fase 3)
    public static final String PEDIDO = "PEDIDO";
    public static final String PEDIDO_NOT_FOUND = "Pedido não encontrado!";
    public static final String PEDIDO_SEM_ITENS = "Pedido deve ter ao menos um item";
    public static final String PEDIDO_ITEM_QUANTIDADE_INVALIDA = "Quantidade do item deve ser maior que zero";
    public static final String PEDIDO_ITEM_DESCONTO_INVALIDO =
            "Desconto do item deve ser maior ou igual a zero e menor que o valor bruto do item";
    // Placeholder: produtoId duplicado.
    public static final String PEDIDO_ITEM_PRODUTO_DUPLICADO = "Produto duplicado no pedido: %s";
    // Placeholder: produtoId sem preço. Item sem precoUnitario informado cai aqui quando o motor de
    // preço (spec/motor-resolucao-preco.md) não resolve preço em nenhum nível da cascata.
    public static final String PEDIDO_ITEM_SEM_PRECO =
            "Produto %s não possui preço vigente; informe o preço manualmente.";
    public static final String PEDIDO_DATA_VALIDADE_INVALIDA =
            "Data de validade não pode ser anterior à data de emissão";
    // Placeholders: status atual, status de destino.
    public static final String PEDIDO_TRANSICAO_INVALIDA = "Transição inválida: pedido em %s não pode ir para %s";
    public static final String PEDIDO_CONDICAO_PAGAMENTO_OBRIGATORIA =
            "Condição de pagamento é obrigatória para confirmar o pedido";
    // Placeholder: data de validade expirada.
    public static final String PEDIDO_ORCAMENTO_EXPIRADO = "Orçamento expirado — data de validade %s já passou";
    // Placeholders: exposição calculada, limite de crédito. Motivo gravado no pedido_status_historico.
    public static final String PEDIDO_BLOQUEADO_CREDITO_MOTIVO =
            "Limite de crédito excedido: exposição de %s supera o limite de %s";
    public static final String PEDIDO_CONFIRMADO_COM_BYPASS_MOTIVO =
            "Confirmado com estouro de limite de crédito (exposição %s > limite %s) por usuário com "
                    + "permissão de bypass";
    public static final String PEDIDO_DEPOSITO_OBRIGATORIO = "Depósito é obrigatório para expedir o pedido";
    public static final String PEDIDO_TRANSPORTADORA_OBRIGATORIA =
            "Transportadora é obrigatória quando a modalidade de frete não é SEM_FRETE";
    public static final String PEDIDO_MOTIVO_CANCELAMENTO_OBRIGATORIO = "Motivo do cancelamento é obrigatório";
    // Placeholder: soma dos percentuais encontrada.
    public static final String PEDIDO_PARCELAS_PERCENTUAL_INVALIDO =
            "Soma dos percentuais das parcelas da condição de pagamento deve ser 100 (atual: %s)";
    // Placeholder: tenantId.
    public static final String PEDIDO_NUMERACAO_FALHA = "Falha ao obter numeração do pedido para o tenant %s";
    // O2C — Fase 4 (API/controllers, spec/o2c-vendas.md §5/§10)
    public static final String PEDIDO_UPDATE_SO_ORCAMENTO = "Só é possível editar pedido em status ORCAMENTO";
    public static final String PEDIDO_RECALCULO_SO_ORCAMENTO =
            "Só é possível recalcular preços de pedido em status ORCAMENTO";

    // O2C — Suporte a serviço no item do pedido (spec/o2c-vendas.md, Rev. 8)
    public static final String PEDIDO_EXPEDICAO_SO_MERCADORIA =
            "Pedido só com serviços não passa por expedição; fature diretamente";
    // Placeholder: produtoId.
    public static final String PEDIDO_PRODUTO_NAO_ENCONTRADO = "Produto não encontrado: %s";
    // Placeholder: nome do produto.
    public static final String PEDIDO_PRODUTO_INATIVO = "Produto inativo: %s";
    // Placeholder: produtoId. Defesa: tipoItem só fica null se o controller não tiver resolvido
    // o produto antes de montar a entidade — não deveria acontecer no fluxo normal via API.
    public static final String PEDIDO_ITEM_SEM_TIPO = "Tipo do item não resolvido para o produto %s";
    public static final String CADASTRO_SERVICE_INDISPONIVEL = "Serviço de cadastros indisponível";

    // O2C — D4: integração fiscal no faturamento (spec/o2c-vendas.md §8, Rev. 8)
    public static final String FISCAL_SERVICE_INDISPONIVEL = "Serviço fiscal indisponível, tente novamente";
    // Placeholders: produtoId, motivo devolvido pelo fiscal-service (ex.: FISCAL_CCLASSTRIB_OBRIGATORIO).
    public static final String PEDIDO_FISCAL_CALCULO_REJEITADO = "Cálculo fiscal rejeitado para o produto %s: %s";
    // Defaults do request ao fiscal-service — CFOP (dentro do estado) e regime tributário do
    // emitente ainda não são campos modelados no ERP (UF do cliente/tenant, regime do Tenant);
    // ver ponytail em FiscalServiceClient.
    public static final String PEDIDO_FISCAL_CFOP_MERCADORIA_DEFAULT = "5102";
    public static final String PEDIDO_FISCAL_CFOP_SERVICO_DEFAULT = "5933";

    // O2C — Fase 5: eventos Kafka das transições de pedido (spec/o2c-vendas.md §8)
    public static final String PEDIDO_CONFIRMADO_TOPIC = "venda.pedido.confirmado";
    public static final String PEDIDO_FATURADO_TOPIC = "venda.pedido.faturado";
    public static final String PEDIDO_CANCELADO_TOPIC = "venda.pedido.cancelado";
    public static final String AUDIT_ACAO_PEDIDO_CONFIRMADO = "PEDIDO_CONFIRMADO";
    public static final String AUDIT_ACAO_PEDIDO_FATURADO = "PEDIDO_FATURADO";
    public static final String AUDIT_ACAO_PEDIDO_CANCELADO = "PEDIDO_CANCELADO";
}
