package com.l.erp.authservice.dominio.enumerators;

import java.io.Serializable;

public enum EnumTenantStatus implements Serializable {

    ATIVO("ATIVO"),
    TRIAL("TRIAL"),
    PENDENTE("PENDENTE"),
    CONVIDADO("CONVIDADO"),
    SUSPENSO("SUSPENSO"),
    CANCELADO("CANCELADO");

    private String description;

    EnumTenantStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
