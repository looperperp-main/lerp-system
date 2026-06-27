package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.dto.lists.RoleSearchFilterDTO;
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
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.common.util.Constants;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BusinessException;
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
    private final UserRoleRepository userRoleRepository;

    public RolesService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            TenantRepository tenantRepository,
            RoleMapper roleMapper,
            AuditService auditService,
            UserRoleRepository userRoleRepository
            ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.tenantRepository = tenantRepository;
        this.roleMapper = roleMapper;
        this.auditService = auditService;
        this.userRoleRepository = userRoleRepository;
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
     * Searches for roles using the specified filter criteria and pagination information.
     *
     * @param filter   the filter object containing criteria for the search.
     * @param pageable the pagination information specifying page size and index.
     * @return a paginated list of roles matching the filter criteria.
     */
    public Page<RoleDTO> searchRoles(RoleSearchFilterDTO filter, Pageable pageable) {
        logger.debug("Buscando Roles Paginadas com Filtros");
        return roleRepository.findWithFilters(filter, pageable).map(roleMapper :: toRoleDTO);
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
            throw new BusinessException("Já existe uma Role com este nome neste Tenant", HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = tenantRepository.findById(roleDTO.tenantId())
                .orElseThrow(() -> new BusinessException("Tenant não encontrado", HttpStatus.BAD_REQUEST));

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        String createdBy = Optional.of(currentUser).map(CurrentUser::email)
                .orElseThrow(() -> new BusinessException("Usuário não Autenticado",HttpStatus.UNAUTHORIZED));
        Role role = new Role();
        role.setName(roleDTO.name());
        role.setTenant(tenant);
        role.setCreatedBy(createdBy);
        role.setCreatedDate(Instant.now());

        Role saved = roleRepository.save(role);


        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.ROLE_CREATION,
                Constants.ROLE,
                saved.getId(), Constants.SUCCESS,
                null, correlationId);


        return roleMapper.toRoleDTO(saved);
    }

    /**
     *
     * @param roleId RoleId
     * @param permissionId PermissionId
     * @param tenantId Tenant_Id
     */
    @Transactional
    public void addPermissionToRoleAndTenant(UUID roleId, UUID permissionId, Long tenantId) {
        logger.debug("Vinculando Permissao: {} a Role: {}", permissionId, roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(Constants.ROLE_NOT_FOUND, HttpStatus.BAD_REQUEST));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("Permissão não encontrada", HttpStatus.BAD_REQUEST));

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado", HttpStatus.BAD_REQUEST));

        // Verifica se a associação já existe através da chave composta
        if (rolePermissionRepository.existsByRoleAndPermission(roleId, permissionId)) {
            throw new BusinessException("A Role já possui esta permissão vinculada", HttpStatus.BAD_REQUEST);
        }

        RolePermission rolePermission = new RolePermission();

        // Define a Chave Composta
        rolePermission.setId(new RolePermissionId(roleId, permissionId));

        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        // Associa o Tenant com base na Role
        rolePermission.setTenant(role.getTenant());

        RolePermission updated = rolePermissionRepository.save(rolePermission);


        // Pegando o Correlation ID da requisição
        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.ROLE_PERMISSION_CREATION,
                 Constants.ROLE_PERMISSION,
                updated.getId().getRoleId(), Constants.SUCCESS,
                null, correlationId);

    }

    public List<Permission> getPermissionsByRoleId(UUID roleId) {
        return rolePermissionRepository.findAllByRoleId(roleId)
                .stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toList());
    }

    /**
     * Retorna todas as Permissões associadas a uma Role específica
     */
    public List<PermissionDTO> getPermissionsByRole(UUID roleId) {
        logger.debug("Buscando permissões da Role: {}", roleId);

        // Garante que a role existe
        roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(Constants.ROLE_NOT_FOUND, HttpStatus.NOT_FOUND));

        return rolePermissionRepository.findAllByRoleId(roleId).stream()
                .map(rp -> new PermissionDTO(
                        rp.getPermission().getId(),
                        rp.getPermission().getCode(),
                        rp.getPermission().getDomain(),
                        rp.getPermission().getDescription(),
                        rp.getPermission().getCreatedDate(),
                        rp.getPermission().getCreatedBy(),
                        rp.getPermission().getLastUpdateDate(),
                        rp.getPermission().getLastUpdatedBy()
                ))
                .toList();
    }

    /**
     * Vincula uma lista de Permissões a uma Role
     */
    @Transactional
    public void assignPermissionsToRole(UUID roleId, List<UUID> permissionIds) {
        logger.debug("Atribuindo {} permissões para a Role: {}", permissionIds.size(), roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(Constants.ROLE_NOT_FOUND, HttpStatus.NOT_FOUND));

        // Para evitar duplicidade complexa, na UI de "Atribuição em Lote",
        // costuma-se remover as antigas e inserir as novas,
        // mas vamos fazer de forma mais inteligente: inserindo apenas as novas.

        for (UUID permissionId : permissionIds) {

            // Criamos a chave composta
            RolePermissionId compositeId = new RolePermissionId(roleId, permissionId);

            if (!rolePermissionRepository.existsById(compositeId)) {

                Permission permission = permissionRepository.findById(permissionId)
                        .orElseThrow(() -> new BusinessException("Permissão ID " + permissionId + " não encontrada", HttpStatus.NOT_FOUND));

                RolePermission rp = new RolePermission();
                rp.setId(compositeId);
                rp.setRole(role);
                rp.setPermission(permission);
                rp.setTenant(role.getTenant()); // Herda o tenant da Role

                RolePermission saved = rolePermissionRepository.save(rp);

                UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
                auditService.logAuditEvent(Constants.ROLE_PERMISSION_ASSIGNMENT,
                        Constants.ROLE_PERMISSION,
                        saved.getId().getRoleId(), Constants.SUCCESS,
                        null, correlationId);

            }
        }
    }

    /**
     * Remove uma permissão específica de uma Role
     */
    @Transactional
    public void removePermissionFromRole(UUID roleId, UUID permissionId) {
        logger.debug("Removendo permissão {} da Role {}", permissionId, roleId);

        if (!rolePermissionRepository.existsByRoleAndPermission(roleId, permissionId)) {
            throw new BusinessException("O vínculo entre esta Role e Permissão não existe", HttpStatus.BAD_REQUEST);
        }

        rolePermissionRepository.deleteByRoleAndPermission(roleId, permissionId);


        // Pegando o Correlation ID da requisição
        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.ROLE_PERMISSION_DELETE,
                Constants.ROLE_PERMISSION,
                permissionId, Constants.SUCCESS,
                null, correlationId);

    }

    /**
     * Deleta uma Role fisicamente do banco de dados
     *
     * @param roleId ID da Role a ser deletada
     */
    @Transactional
    public void deleteRole(UUID roleId) {
        logger.debug("Deletando Role ID: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(Constants.ROLE_NOT_FOUND, HttpStatus.NOT_FOUND));

        // OBS: Se a Role tiver vinculada a Usuários (user_role) ou Permissões (role_permission),
        // O banco de dados pode estourar erro de restrição de chave estrangeira dependendo de como as constraints foram feitas.
        // O ideal seria limpar os vínculos antes, ou ter "ON DELETE CASCADE" no banco.

        // Estratégia de Bloqueio: Verifica se a role está atrelada a alguma Permissão
        if (rolePermissionRepository.existsByRoleId(roleId)) {
            throw new BusinessException("Não é possível excluir esta Role pois ela possui permissões associadas. Desvincule as permissões antes de excluí-la.", HttpStatus.BAD_REQUEST);
        }

        // Estratégia de Bloqueio: Verifica se a role está atrelada a algum Usuário
        if (userRoleRepository.existsByRoleId(roleId)) {
            throw new BusinessException("Não é possível excluir esta Role pois ela está vinculada a um ou mais Usuários. Remova o acesso dos usuários antes de excluí-la.", HttpStatus.BAD_REQUEST);
        }

        roleRepository.delete(role);

        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.ROLE_DELETE,
                Constants.ROLE,
                roleId, Constants.SUCCESS,
                null, correlationId);
    }
}
