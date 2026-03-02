package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.mappers.RoleMapper;
import com.l.erp.authservice.dominio.Permission;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.dominio.RolePermission;
import com.l.erp.authservice.dominio.RolePermissionId;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.repositorios.PermissionRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.RoleRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BussinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RolesService {
    private final Logger logger = LoggerFactory.getLogger(RolesService.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TenantRepository tenantRepository;
    private final RoleMapper roleMapper;

    public RolesService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            TenantRepository tenantRepository,
            RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.tenantRepository = tenantRepository;
        this.roleMapper = roleMapper;
    }

    /**
     *
     * @return
     */
    public List<RoleDTO> getAllRoles() {
        logger.debug("Recuperando a Lista de Roles");
        List<Role> roles = roleRepository.findAll();
        return roleMapper.toRoleDTOs(roles);
    }


    @Transactional
    public Role createRole(String name, Long tenantId) {
        logger.debug("Criando nova Role: {} para o Tenant: {}", name, tenantId);

        // Verifica se a role já existe para este tenant
        if (roleRepository.findByNameAndTenant_Id(name, tenantId).isPresent()) {
            throw new BussinessException("Já existe uma Role com este nome neste Tenant", HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BussinessException("Tenant não encontrado", HttpStatus.BAD_REQUEST));

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        String createdBy = Optional.of(currentUser).map(CurrentUser::email)
                .orElseThrow(() -> new BussinessException("Usuário não Autenticado",HttpStatus.UNAUTHORIZED));
        Role role = new Role();
        role.setName(name);
        role.setTenant(tenant);
        role.setCreatedBy(createdBy);
        role.setCreatedDate(Instant.now());

        return roleRepository.save(role);
    }

    @Transactional
    public void addPermissionToRole(UUID roleId, UUID permissionId) {
        logger.debug("Vinculando Permissao: {} a Role: {}", permissionId, roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BussinessException("Role não encontrada", HttpStatus.NOT_FOUND));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BussinessException("Permissão não encontrada", HttpStatus.NOT_FOUND));

        // Verifica se a associação já existe através da chave composta
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw new BussinessException("A Role já possui esta permissão vinculada", HttpStatus.BAD_REQUEST);
        }

        RolePermission rolePermission = new RolePermission();

        // Define a Chave Composta
        rolePermission.setId(new RolePermissionId(roleId, permissionId));

        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        // Associa o Tenant com base na Role
        rolePermission.setTenant(role.getTenant());

        rolePermissionRepository.save(rolePermission);
    }

    public List<Permission> getPermissionsByRoleId(UUID roleId) {
        return rolePermissionRepository.findAllByRoleId(roleId)
                .stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toList());
    }
}
