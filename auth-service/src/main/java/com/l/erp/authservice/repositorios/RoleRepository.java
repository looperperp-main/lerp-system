package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.api.dto.lists.RoleSearchFilterDTO;
import com.l.erp.authservice.dominio.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndTenant_Id(String name, Long tenantId);

    // --- Tenant-scoped (portal do tenant) ---

    @Query("SELECT r FROM Role r WHERE r.tenant.id = :tenantId")
    List<Role> findByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT r FROM Role r WHERE r.id = :id AND r.tenant.id = :tenantId")
    Optional<Role> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") Long tenantId);

    @Query("SELECT r FROM Role r WHERE r.tenant.id = :tenantId " +
            "AND (:#{#filter.name} IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :#{#filter.name}, '%')))")
    Page<Role> findWithFiltersAndTenant(@Param("filter") RoleSearchFilterDTO filter,
                                        @Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT count(ur) > 0 FROM UserRole ur WHERE ur.role.id = :roleId")
    boolean existsByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT r FROM Role r " +
            "WHERE (:#{#filter.name} IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :#{#filter.name}, '%')))")
    Page<Role> findWithFilters(@Param("filter") RoleSearchFilterDTO filter, Pageable pageable);

}
