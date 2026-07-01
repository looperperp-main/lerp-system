package com.l.erp.authservice.services;

import com.l.erp.authservice.dominio.Permission;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.dominio.RolePermission;
import com.l.erp.authservice.dominio.RolePermissionId;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.dominio.UserRole;
import com.l.erp.authservice.dominio.UserRoleId;
import com.l.erp.authservice.repositorios.PermissionRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.RoleRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Cria automaticamente a role do owner ({@link Constants#OWNER_ROLE_NAME}) no nascimento do tenant,
 * com as permissões dos domínios de segurança (PERMISSION/USER/ROLE), e a vincula ao usuário owner.
 *
 * <p>Resolve o deadlock de bootstrap: sem isso, um tenant recém-criado teria o owner sem nenhuma
 * role → nenhuma permissão → 403 em todas as telas (inclusive as de segurança que ele usaria para
 * se auto-conceder acesso). Idempotente: se a role já existe para o tenant, não faz nada.</p>
 */
@Service
public class TenantOwnerBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(TenantOwnerBootstrapService.class);

    // Domínios cujas permissões a role do owner recebe no bootstrap.
    private static final List<String> OWNER_DOMAINS = List.of(Constants.PERMISSION, Constants.USER, Constants.ROLE);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    public TenantOwnerBootstrapService(RoleRepository roleRepository,
                                       PermissionRepository permissionRepository,
                                       RolePermissionRepository rolePermissionRepository,
                                       UserRoleRepository userRoleRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional
    public void bootstrapOwner(Tenant tenant, UserAccount owner) {
        // Idempotência: se a role do owner já existe nesse tenant, não recria.
        if (roleRepository.findByNameAndTenant_Id(Constants.OWNER_ROLE_NAME, tenant.getId()).isPresent()) {
            logger.debug("Role {} já existe para o tenant {} — bootstrap ignorado", Constants.OWNER_ROLE_NAME, tenant.getId());
            return;
        }

        Role role = new Role();
        role.setName(Constants.OWNER_ROLE_NAME);
        role.setTenant(tenant);
        role.setCreatedBy(Constants.SYSTEM_BOOTSTRAP);
        role.setCreatedDate(Instant.now());
        Role savedRole = roleRepository.save(role);

        List<Permission> permissions = permissionRepository.findByDomainIn(OWNER_DOMAINS);
        for (Permission permission : permissions) {
            RolePermission rp = new RolePermission();
            rp.setId(new RolePermissionId(savedRole.getId(), permission.getId()));
            rp.setRole(savedRole);
            rp.setPermission(permission);
            rp.setTenant(tenant);
            rolePermissionRepository.save(rp);
        }

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(tenant.getId(), owner.getId(), savedRole.getId()));
        userRole.setTenant(tenant);
        userRole.setUser(owner);
        userRole.setRole(savedRole);
        userRoleRepository.save(userRole);

        logger.info("Bootstrap: role {} criada para o tenant {} com {} permissões e atribuída ao owner {}",
                Constants.OWNER_ROLE_NAME, tenant.getId(), permissions.size(), owner.getId());
    }
}