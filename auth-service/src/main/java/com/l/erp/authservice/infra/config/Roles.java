package com.l.erp.authservice.infra.config;

public class Roles {
    private Roles(){
        //construtor vazio
    }

    public static final String APP_OWNER = "ROLE_APP_OWNER";
    public static final String TENANT_OWNER = "ROLE_TENANT_OWNER";
    public static final String ROLE_TENANT_USER = "ROLE_TENANT_USER";

}
