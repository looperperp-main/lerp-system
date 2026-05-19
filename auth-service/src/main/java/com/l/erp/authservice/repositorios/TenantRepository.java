package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Page<Tenant> findAllByStatusIs(@Size(max = 30) @NotNull EnumTenantStatus status, Pageable pageable);

    Optional<Tenant> findByCnpj(String cnpj);

    /**
     * To be used
     * @param slug slug from Tenant URL
     * @return Tenant
     */
    Optional<Tenant> findBySlug(String slug);

    List<Tenant> findByStatusAndTrialStartedAtBetween(EnumTenantStatus status, Instant from, Instant to);

    List<Tenant> findByStatusAndTrialExpiresAtBefore(EnumTenantStatus status, Instant cutoff);
}
