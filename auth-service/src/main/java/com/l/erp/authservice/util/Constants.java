package com.l.erp.authservice.util;

public class Constants {

    private Constants(){
        //construtor vazio
    }

        public static final String TENANT_CREATION = "TENANT_INSERT";
        public static final String TENANT_UPDATE = "TENANT_UPDATE";
        public static final String TENANT_CANCEL= "TENANT_CANCEL";
        public static final String TENANT= "TENANT";
        public static final String TENANT_NOT_FOUND= "Tenant não Encontrado!";

        public static final String USUARIO_NAO_AUTENTICADO= "Usuário não autenticado";
        public static final String USUARIO_UUID_NAO_ENCONTRADO= "Usuário logado não possuí UUID! Contate um administrador do sistema.";

}
