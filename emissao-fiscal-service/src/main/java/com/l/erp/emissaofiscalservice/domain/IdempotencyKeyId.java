package com.l.erp.emissaofiscalservice.domain;

import java.io.Serializable;
import java.util.Objects;

public class IdempotencyKeyId implements Serializable {

    private Long tenantId;
    private String idempotencyKey;

    public IdempotencyKeyId() {
    }

    public IdempotencyKeyId(Long tenantId, String idempotencyKey) {
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKeyId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, idempotencyKey);
    }
}
