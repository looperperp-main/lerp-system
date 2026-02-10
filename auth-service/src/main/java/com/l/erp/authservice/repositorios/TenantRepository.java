package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Long countAllByNameAndCnpj(String name, String cnpj);

}
