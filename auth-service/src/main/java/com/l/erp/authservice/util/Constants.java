package com.l.erp.authservice.util;

public class Constants {

    private Constants(){
        //construtor vazio
    }

    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR   = "ERROR";
    public static final String FAILED  = "FAILED";

    public static final String TENANT = "TENANT";
    public static final String USER = "USER";
    public static final String ROLE = "ROLE";
    public static final String PERMISSION = "PERMISSION";
    public static final String INSERT = "INSERT";
    public static final String UPDATE = "UPDATE";
    public static final String CANCEL = "CANCEL";
    public static final String DELETE = "DELETE";
    public static final String TENANT_NOT_FOUND= "Tenant não Encontrado!";
    public static final String TENANT_CREATION = TENANT + "_" + INSERT;
    public static final String TENANT_UPDATE = TENANT + "_" + UPDATE;
    public static final String TENANT_CANCEL= "TENANT" + "_" + CANCEL;

    public static final String PERMISSION_CREATION = PERMISSION + "_" + INSERT;
    public static final String PERMISSION_UPDATE = PERMISSION + "_" + UPDATE;
    public static final String PERMISSION_DELETE = PERMISSION + "_" + DELETE;

    public static final String ROLE_CREATION = ROLE + "_" + INSERT;
    public static final String ROLE_UPDATE = ROLE + "_" + UPDATE;
    public static final String ROLE_DELETE = ROLE + "_" + DELETE;

    public static final String ROLE_PERMISSION = ROLE + "_" + PERMISSION;
    public static final String ROLE_PERMISSION_CREATION = ROLE_PERMISSION + "_" + INSERT;
    public static final String ROLE_PERMISSION_UPDATE = ROLE_PERMISSION + "_" + UPDATE;
    public static final String ROLE_PERMISSION_DELETE = ROLE_PERMISSION + "_" + DELETE;


    public static final String USUARIO_NAO_AUTENTICADO= "Usuário não autenticado";
    public static final String USUARIO_UUID_NAO_ENCONTRADO= "Usuário logado não possuí UUID! Contate um administrador do sistema.";

}
