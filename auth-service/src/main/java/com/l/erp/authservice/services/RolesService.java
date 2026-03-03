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
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.Constants;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BussinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final AuditService auditService;

    public RolesService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            TenantRepository tenantRepository,
            RoleMapper roleMapper,
            AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.tenantRepository = tenantRepository;
        this.roleMapper = roleMapper;
        this.auditService = auditService;
    }

    /**
     *
     * @return lista de todas as roles
     */
    public List<RoleDTO> getAllRoles() {
        logger.debug("Recuperando a Lista de Roles");
        List<Role> roles = roleRepository.findAll();
        return roleMapper.toRoleDTOs(roles);
    }

    public Page<RoleDTO> getAllRoles(Pageable pageable) {
        logger.debug("Recuperando a Lista de Roles Paginada");
        return roleRepository.findAll(pageable).map(roleMapper :: toRoleDTO);
    }

    /**
     * Cria uma Role
     * @param roleDTO Role a ser criada
     * @return Role criada
     */
    @Transactional
    public RoleDTO createRole(RoleDTO roleDTO) {
        logger.debug("Criando nova Role: {} para o Tenant: {}", roleDTO.name(), roleDTO.tenantId());

        // Verifica se a role já existe para este tenant
        if (roleRepository.findByNameAndTenant_Id(roleDTO.name(), roleDTO.tenantId()).isPresent()) {
            throw new BussinessException("Já existe uma Role com este nome neste Tenant", HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = tenantRepository.findById(roleDTO.tenantId())
                .orElseThrow(() -> new BussinessException("Tenant não encontrado", HttpStatus.BAD_REQUEST));

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        String createdBy = Optional.of(currentUser).map(CurrentUser::email)
                .orElseThrow(() -> new BussinessException("Usuário não Autenticado",HttpStatus.UNAUTHORIZED));
        Role role = new Role();
        role.setName(roleDTO.name());
        role.setTenant(tenant);
        role.setCreatedBy(createdBy);
        role.setCreatedDate(Instant.now());

        Role saved = roleRepository.save(role);

        if (currentUser != null) {
            // Pegando o Correlation ID da requisição
            UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
            auditService.logAuditEvent(Constants.ROLE_CREATION,
                    currentUser.id(), Constants.ROLE,
                    saved.getId(), Constants.SUCCESS,
                    null, correlationId);
        }

        return roleMapper.toRoleDTO(saved);
    }

    /**
     *
     * @param roleId
     * @param permissionId
     * @param tenantId
     */
    @Transactional
    public void addPermissionToRoleAndTenant(UUID roleId, UUID permissionId, Long tenantId) {
        logger.debug("Vinculando Permissao: {} a Role: {}", permissionId, roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BussinessException("Role não encontrada", HttpStatus.BAD_REQUEST));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BussinessException("Permissão não encontrada", HttpStatus.BAD_REQUEST));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BussinessException("Tenant não encontrado", HttpStatus.BAD_REQUEST));

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

        RolePermission updated = rolePermissionRepository.save(rolePermission);

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();

        if (currentUser != null) {
            // Pegando o Correlation ID da requisição
            UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
            auditService.logAuditEvent(Constants.ROLE_PERMISSION_CREATION,
                    currentUser.id(), Constants.ROLE_PERMISSION,
                    updated.getId().getRoleId(), Constants.SUCCESS,
                    null, correlationId);
        }
    }

    public List<Permission> getPermissionsByRoleId(UUID roleId) {
        return rolePermissionRepository.findAllByRoleId(roleId)
                .stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toList());
    }
}
