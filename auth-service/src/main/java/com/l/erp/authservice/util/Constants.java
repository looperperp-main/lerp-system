package com.l.erp.authservice.util;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public class Constants {

    private Constants(){
        //construtor vazio
    }

    public static final String SYSTEM = "SYSTEM";

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

}
