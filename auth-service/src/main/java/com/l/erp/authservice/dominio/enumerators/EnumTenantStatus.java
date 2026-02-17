package com.l.erp.authservice.dominio.enumerators;

import java.io.Serializable;

public enum EnumTenantStatus implements Serializable {

    A("ATIVO"),
    P("PENDENTE"),
    S("SUSPENSO"),
    C("CANCELADO");

    private String description;

    EnumTenantStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
