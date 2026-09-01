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
import org.springframework.context.annotation.Lazy;
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
    private final AttributionsService self;

    public AttributionsService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            AuditService auditService,
            @Lazy AttributionsService self
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
        this.self = self;
    }

    public List<RoleDTO> getRolesByUser(UUID userId) {
        logger.debug("Buscando Roles do Usuário: {}", userId);

        userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.BAD_REQUEST));

        return userRoleRepository.findAllByUserId(userId).stream()
                .map(ur -> {
                    // Usando o mapper ou mapeamento manual
                    Role role = ur.getRole();
                    return new RoleDTO(role.getId(),
                            role.getName(), role.getTenant().getId(),
                            role.getCreatedDate(), role.getCreatedBy(),
                            role.getLastUpdateDate(), role.getLastUpdateBy(),
                            role.getDescricao());
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
                .orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

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
                assertRemocaoDeProprietarioPermitida(userId, existingUr.getRole());
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
                assertAtribuicaoDeProprietarioPermitida(role);

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

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException("Role ID " + roleId + " não encontrada", HttpStatus.NOT_FOUND));
        assertRemocaoDeProprietarioPermitida(userId, role);

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
        self.assignRolesToUser(userId, roleIds);
    }

    @Transactional
    public void removeRoleFromUserForTenant(UUID userId, UUID roleId, Long tenantId) {
        assertUserInTenant(userId, tenantId);
        self.removeRoleFromUser(userId, roleId);
    }

    private void assertUserInTenant(UUID userId, Long tenantId) {
        userAccountRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    // Self-demotion / tenant órfão: a role de proprietário não pode ser removida do próprio
    // usuário logado, nem do último proprietário restante do tenant. Chamado tanto por
    // removeRoleFromUser quanto pelo laço de sync de assignRolesToUser (mesma origem de remoção).
    private void assertRemocaoDeProprietarioPermitida(UUID userId, Role role) {
        if (!Constants.OWNER_ROLE_NAME.equals(role.getName())) {
            return;
        }
        if (userId.equals(SecurityUtils.getCurrentUserId().orElse(null))) {
            throw new BusinessException(Constants.OWNER_ROLE_AUTO_REMOCAO, HttpStatus.BAD_REQUEST);
        }
        long proprietarios = userRoleRepository.countDistinctUsersByTenantIdAndRoleName(
                role.getTenant().getId(), Constants.OWNER_ROLE_NAME);
        if (proprietarios <= 1) {
            throw new BusinessException(Constants.OWNER_ROLE_ULTIMO_PROPRIETARIO, HttpStatus.BAD_REQUEST);
        }
    }

    // Self-promotion: só quem já é proprietário do tenant pode conceder a role de proprietário
    // a alguém (a si mesmo ou a outro usuário). Espelha assertRemocaoDeProprietarioPermitida.
    private void assertAtribuicaoDeProprietarioPermitida(Role role) {
        if (!Constants.OWNER_ROLE_NAME.equals(role.getName())) {
            return;
        }
        UUID callerId = SecurityUtils.getCurrentUserId().orElse(null);
        if (callerId == null || !userRoleRepository.existsByUserAndRole(callerId, role.getId())) {
            throw new BusinessException(Constants.OWNER_ROLE_CONCESSAO_NAO_AUTORIZADA, HttpStatus.FORBIDDEN);
        }
    }
}
