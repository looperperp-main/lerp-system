package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndTenant_Id(String name, Long tenant_id);

}
