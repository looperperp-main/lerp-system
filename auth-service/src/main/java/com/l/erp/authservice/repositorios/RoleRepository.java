package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndTenant_Id(String name, Long tenant_id);
    // Adicione esta query ao repositório
    @Query("SELECT count(ur) > 0 FROM UserRole ur WHERE ur.role.id = :roleId")
    boolean existsByRoleId(@Param("roleId") UUID roleId);



}
