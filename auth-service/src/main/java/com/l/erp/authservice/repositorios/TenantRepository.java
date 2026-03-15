package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Long countAllByNameAndCnpj(String name, String cnpj);

    Page<Tenant> findAllByStatusIs(String status, Pageable pageable);

    Optional<Tenant> findByCnpj(String cnpj);

    /**
     * To be used
     * @param slug slug from Tenant URL
     * @return Tenant
     */
    Optional<Tenant> findBySlug(String slug);

}
