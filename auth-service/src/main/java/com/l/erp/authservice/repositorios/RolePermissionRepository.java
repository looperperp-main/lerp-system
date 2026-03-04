package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.RolePermission;
import com.l.erp.authservice.dominio.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    // Busca todas as permissões de uma Role específica
    @Query("SELECT rp FROM RolePermission rp WHERE rp.role.id = :roleId")
    List<RolePermission> findAllByRoleId(UUID roleId);

    // Verifica se uma Role já tem uma permissão específica
    @Query("SELECT count(rp) > 0 FROM RolePermission rp WHERE rp.id.roleId = :roleId AND rp.id.permissionId = :permissionId")
    boolean existsByRoleAndPermission(UUID roleId, UUID permissionId);

    // Deleta o vínculo (opcional, para quando quiser remover uma permissão da role)
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.id.roleId = :roleId AND rp.id.permissionId = :permissionId")
    void deleteByRoleAndPermission(UUID roleId, UUID permissionId);

    @Query("SELECT count(rp) > 0 FROM RolePermission rp WHERE rp.role.id = :roleId")
    boolean existsByRoleId(@Param("roleId") UUID roleId);

}
