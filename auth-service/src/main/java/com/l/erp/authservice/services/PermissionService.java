package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.api.mappers.PermissionMapper;
import com.l.erp.authservice.dominio.Permission;
import com.l.erp.authservice.repositorios.PermissionRepository;
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
import java.util.UUID;

@Service
public class PermissionService {
    private final Logger logger = LoggerFactory.getLogger(PermissionService.class);
    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;
    private final AuditService auditService;

    public PermissionService(
            PermissionRepository permissionRepository,
            PermissionMapper permissionMapper,
            AuditService auditService
    ) {
        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
        this.auditService = auditService;
    }

    public Page<PermissionDTO> getAllPermissions(Pageable pageable) {
        logger.debug("Buscando todas as Permissões de forma paginada");
        return permissionRepository.findAll(pageable)
                .map(permissionMapper :: toPermissionDTO);
    }

    public PermissionDTO getPermissionById(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.PERMISSION_NOT_FOUND, HttpStatus.NOT_FOUND));
        return permissionMapper.toPermissionDTO(permission);
    }

    @Transactional
    public PermissionDTO createPermission(PermissionDTO dto) {
        logger.debug("Criando nova Permissão: {}", dto.code());

        if (permissionRepository.findByCode(dto.code()).isPresent()) {
            throw new BusinessException("Já existe uma permissão com este código", HttpStatus.BAD_REQUEST);
        }

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        String actor = (currentUser.email() != null) ? currentUser.email() : "system";

        Permission permission = new Permission();
        permission.setCode(dto.code().toUpperCase()); // Padronizando para uppercase
        permission.setDomain(dto.domain());
        permission.setScope(normalizeScope(dto.scope()));
        permission.setDescription(dto.description());
        permission.setCreatedBy(actor);
        permission.setCreatedDate(Instant.now());
        Permission savedPermission = permissionRepository.save(permission);


        // Pegando o Correlation ID da requisição
        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.PERMISSION_CREATION,
                Constants.PERMISSION,
                savedPermission.getId(), Constants.SUCCESS,
                null, correlationId);


        return permissionMapper.toPermissionDTO(savedPermission);
    }

    @Transactional
    public PermissionDTO updatePermission(UUID id, PermissionDTO dto) {
        logger.debug("Atualizando Permissão ID: {}", id);

        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.PERMISSION_NOT_FOUND, HttpStatus.NOT_FOUND));

        // Se estiver alterando o código, verifica se o novo código já existe
        if (!permission.getCode().equalsIgnoreCase(dto.code()) &&
                permissionRepository.findByCode(dto.code()).isPresent()) {
            throw new BusinessException("Já existe outra permissão usando este código", HttpStatus.BAD_REQUEST);
        }

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        String actor = (currentUser.email() != null) ? currentUser.email() : "system";

        permission.setCode(dto.code().toUpperCase());
        permission.setDomain(dto.domain());
        permission.setScope(normalizeScope(dto.scope()));
        permission.setDescription(dto.description());
        permission.setLastUpdatedBy(actor);
        permission.setLastUpdateDate(Instant.now());

        Permission updatedPermission = permissionRepository.save(permission);
        // Pegando o Correlation ID da requisição
        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.PERMISSION_UPDATE,
                Constants.PERMISSION,
                updatedPermission.getId(), Constants.SUCCESS,
                null, correlationId);

        return permissionMapper.toPermissionDTO(updatedPermission);
    }

    /** Normaliza o escopo: {@code PLATFORM} se informado explicitamente, senão {@code TENANT} (default seguro). */
    private String normalizeScope(String scope) {
        return "PLATFORM".equalsIgnoreCase(scope) ? "PLATFORM" : "TENANT";
    }

    @Transactional
    public void deletePermission(UUID id) {
        logger.debug("Deletando Permissão ID: {}", id);

        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.PERMISSION_NOT_FOUND, HttpStatus.NOT_FOUND));
        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.PERMISSION_DELETE,
                Constants.PERMISSION,
                permission.getId(), Constants.SUCCESS,
                null, correlationId);

        // Lógica Adicional Opcional: Verificar se essa permissão está em uso na tabela RolePermission antes de deletar
        permissionRepository.delete(permission);
    }

}
