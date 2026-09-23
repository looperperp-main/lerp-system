package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.IdempotencyKey;
import com.l.erp.emissaofiscalservice.domain.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {

    Optional<IdempotencyKey> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);
}
