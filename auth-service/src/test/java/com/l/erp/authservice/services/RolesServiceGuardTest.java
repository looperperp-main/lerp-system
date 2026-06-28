package com.l.erp.authservice.services;

import com.l.erp.authservice.dominio.Permission;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.repositorios.PermissionRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.RoleRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.api.mappers.RoleMapper;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolesServiceGuardTest {

    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RoleMapper roleMapper;
    @Mock AuditService auditService;
    @Mock UserRoleRepository userRoleRepository;

    @InjectMocks
    RolesService rolesService;

    @Test
    void platformPermission_withoutAssignAuthority_isForbidden() {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(new Role()));
        Permission platform = new Permission();
        platform.setCode("REPASSE_EXECUTE");
        platform.setScope("PLATFORM");
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(platform));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(Optional.of(actorId));
            // ator não tem nenhuma role/permissão → não possui PLATFORM_PERMISSION_ASSIGN
            when(userRoleRepository.findAllByUserId(actorId)).thenReturn(List.of());

            assertThatThrownBy(() -> rolesService.addPermissionToRoleAndTenant(roleId, permissionId, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PLATFORM");
        }
    }
}
