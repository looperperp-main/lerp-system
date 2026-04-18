package com.l.erp.cadastroservice.util;

import java.time.Duration;

public class Constants {

    private Constants(){
        //construtor vazio
    }

    public static final String SYSTEM = "SYSTEM";
    public static final String ADMIN = "ADMIN";
    public static final String ATIVO = "ATIVO";
    public static final String INACTIVE = "INACTIVE";
    public static final String BLOCKED = "BLOCKED";
    public static final String UNBLOCKED = "UNBLOCKED";

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

    public static final String USER_CREATION = USER + "_" + INSERT;
    public static final String USER_UPDATE = USER + "_" + UPDATE;
    public static final String USER_NOT_FOUND= "Usuário não Encontrado";

    public static final String USER_HAS_OWNER_MARKER= "Usuário não pode ser cancelado/excluído pois possui um marker de proprietário";

    public static final String USER_EMAIL_NOT_CORRECT = "Credenciais Inválidas - Senha ou Email incorretos";
    public static final String USER_INACTIVE = "Usuário Inativo";

    public static final String PERMISSION_CREATION = PERMISSION + "_" + INSERT;
    public static final String PERMISSION_UPDATE = PERMISSION + "_" + UPDATE;
    public static final String PERMISSION_DELETE = PERMISSION + "_" + DELETE;

    public static final String ROLE_CREATION = ROLE + "_" + INSERT;
    public static final String ROLE_UPDATE = ROLE + "_" + UPDATE;
    public static final String ROLE_DELETE = ROLE + "_" + DELETE;

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

    public static final String VENDEDOR = "VENDEDOR";
    public static final String VENDEDOR_CREATION = VENDEDOR + "_" + INSERT;
    public static final String VENDEDOR_UPDATE = VENDEDOR +"_" + UPDATE;
    public static final String VENDEDOR_NOT_FOUND = "Vendedor não encontrado!";
    public static final String VENDEDOR_ALREADY_EXISTS = "Já existe um Vendedor com este nome";
}
