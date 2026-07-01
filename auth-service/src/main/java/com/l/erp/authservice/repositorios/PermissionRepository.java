package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    // Permissões visíveis ao portal do tenant (scope TENANT; nunca PLATFORM).
    Page<Permission> findByScope(String scope, Pageable pageable);

    // Permissões por domínio — usado no bootstrap da role do owner (domínios PERMISSION/USER/ROLE).
    List<Permission> findByDomainIn(Collection<String> domains);

}
