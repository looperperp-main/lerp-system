package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    // Busca todas as permissões de uma Role específica
    List<RolePermission> findAllByRoleId(UUID roleId);

    // Verifica se uma Role já tem uma permissão específica
    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    // Deleta o vínculo (opcional, para quando quiser remover uma permissão da role)
    void deleteByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

}
