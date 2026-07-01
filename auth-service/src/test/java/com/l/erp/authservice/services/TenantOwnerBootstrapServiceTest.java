package com.l.erp.authservice.services;

import com.l.erp.authservice.dominio.Permission;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.dominio.RolePermission;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.dominio.UserRole;
import com.l.erp.authservice.repositorios.PermissionRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.RoleRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantOwnerBootstrapServiceTest {

    private RoleRepository roleRepository;
    private PermissionRepository permissionRepository;
    private RolePermissionRepository rolePermissionRepository;
    private UserRoleRepository userRoleRepository;
    private TenantOwnerBootstrapService service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        rolePermissionRepository = mock(RolePermissionRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        service = new TenantOwnerBootstrapService(roleRepository, permissionRepository, rolePermissionRepository, userRoleRepository);
    }

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setId(1L);
        t.setName("Empresa X");
        return t;
    }

    private UserAccount owner() {
        UserAccount u = new UserAccount();
        u.setId(UUID.randomUUID());
        return u;
    }

    private Permission perm(String code, String domain) {
        Permission p = new Permission();
        p.setId(UUID.randomUUID());
        p.setCode(code);
        p.setDomain(domain);
        return p;
    }

    @Test
    void bootstrap_happy_createsRoleWithPermsAndAssignsToOwner() {
        Tenant tenant = tenant();
        UserAccount owner = owner();

        when(roleRepository.findByNameAndTenant_Id(Constants.OWNER_ROLE_NAME, tenant.getId())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(permissionRepository.findByDomainIn(any())).thenReturn(List.of(
                perm("USER_READ", "USER"),
                perm("ROLE_INSERT", "ROLE"),
                perm("PERMISSION_READ", "PERMISSION")));

        service.bootstrapOwner(tenant, owner);

        verify(roleRepository).save(any(Role.class));
        verify(rolePermissionRepository, times(3)).save(any(RolePermission.class));
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void bootstrap_idempotent_skipsWhenRoleAlreadyExists() {
        Tenant tenant = tenant();
        when(roleRepository.findByNameAndTenant_Id(eq(Constants.OWNER_ROLE_NAME), anyLong()))
                .thenReturn(Optional.of(new Role()));

        service.bootstrapOwner(tenant, owner());

        verify(roleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }
}