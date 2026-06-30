package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.dominio.UserRole;
import com.l.erp.authservice.dominio.UserRoleId;
import com.l.erp.authservice.repositorios.RoleRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttributionsService {

    private final Logger logger = LoggerFactory.getLogger(AttributionsService.class);

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    private final AuditService auditService;

    public AttributionsService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            AuditService auditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
    }

    public List<RoleDTO> getRolesByUser(UUID userId) {
        logger.debug("Buscando Roles do Usuário: {}", userId);

        userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado", HttpStatus.BAD_REQUEST));

        return userRoleRepository.findAllByUserId(userId).stream()
                .map(ur -> {
                    // Usando o mapper ou mapeamento manual
                    Role role = ur.getRole();
                    return new RoleDTO(role.getId(),
                            role.getName(), role.getTenant().getId(),
                            role.getCreatedDate(), role.getCreatedBy(),
                            role.getLastUpdateDate(),role.getLastUpdateBy());
                })
                .toList();
    }

    /**
     * Vincula uma lista de Roles a um Usuário
     */
    @Transactional
    public void assignRolesToUser(UUID userId, List<UUID> requestRoleIds) {
        logger.debug("Sincronizando {} roles para o Usuário: {}", requestRoleIds.size(), userId);

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado", HttpStatus.NOT_FOUND));

        // 1. Busca o que o usuário já tem no banco
        List<UserRole> existingUserRoles = userRoleRepository.findAllByUserId(userId);

        // Extrai apenas os IDs das roles que ele já possui para facilitar a comparação
        Set<UUID> existingRoleIds = existingUserRoles.stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        // 2. Transforma a lista do request em Set para facilitar verificações
        Set<UUID> targetRoleIds = Set.copyOf(requestRoleIds);

        // 3. REMOVER: O que tem no banco, mas NÃO veio no request, nós deletamos
        for (UserRole existingUr : existingUserRoles) {
            if (!targetRoleIds.contains(existingUr.getRole().getId())) {
                userRoleRepository.delete(existingUr);

                // Opcional: logar a deleção
                UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
                auditService.logAuditEvent(Constants.USER_ROLE_DELETE,
                        Constants.USER_ROLE, existingUr.getRole().getId(), Constants.SUCCESS,
                        null, correlationId);
            }
        }

        // 4. ADICIONAR: O que veio no request, mas NÃO tem no banco, nós inserimos
        for (UUID newRoleId : targetRoleIds) {
            if (!existingRoleIds.contains(newRoleId)) {

                Role role = roleRepository.findById(newRoleId)
                        .orElseThrow(() -> new BusinessException("Role ID " + newRoleId + " não encontrada", HttpStatus.NOT_FOUND));

                // Validação de segurança: Mesma regra de Tenant
                if (!user.getTenant().getId().equals(role.getTenant().getId())) {
                    throw new BusinessException("A Role (" + role.getName() + ") pertence a outro Tenant e não pode ser atribuída a este usuário", HttpStatus.BAD_REQUEST);
                }

                UserRole ur = new UserRole();
                ur.setId(new UserRoleId(user.getTenant().getId(), userId, newRoleId));
                ur.setTenant(user.getTenant());
                ur.setUser(user);
                ur.setRole(role);

                UserRole saved = userRoleRepository.save(ur);

                UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
                auditService.logAuditEvent(Constants.USER_ROLE_CREATION,
                        Constants.USER_ROLE, saved.getId().getRoleId(), Constants.SUCCESS,
                        null, correlationId);
            }
        }
    }

    /**
     * Remove uma Role específica de um Usuário
     */
    @Transactional
    public void removeRoleFromUser(UUID userId, UUID roleId) {
        logger.debug("Removendo role {} do usuário {}", roleId, userId);

        if (!userRoleRepository.existsByUserAndRole(userId, roleId)) {
            throw new BusinessException("O vínculo entre este Usuário e Role não existe", HttpStatus.BAD_REQUEST);
        }

        userRoleRepository.deleteByUserAndRole(userId, roleId);

        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.USER_ROLE_DELETE,
                Constants.USER_ROLE, roleId, Constants.SUCCESS,
                null, correlationId);
    }

    // ==========================================================================
    // Tenant-scoped (portal do tenant) — tenant vem do header X-Tenant-Id.
    // ==========================================================================

    public List<RoleDTO> getRolesByUserForTenant(UUID userId, Long tenantId) {
        assertUserInTenant(userId, tenantId);
        return getRolesByUser(userId);
    }

    @Transactional
    public void assignRolesToUserForTenant(UUID userId, List<UUID> roleIds, Long tenantId) {
        assertUserInTenant(userId, tenantId);
        // assignRolesToUser já valida que cada role pertence ao mesmo tenant do usuário.
        assignRolesToUser(userId, roleIds);
    }

    @Transactional
    public void removeRoleFromUserForTenant(UUID userId, UUID roleId, Long tenantId) {
        assertUserInTenant(userId, tenantId);
        removeRoleFromUser(userId, roleId);
    }

    private void assertUserInTenant(UUID userId, Long tenantId) {
        userAccountRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado", HttpStatus.NOT_FOUND));
    }
}
