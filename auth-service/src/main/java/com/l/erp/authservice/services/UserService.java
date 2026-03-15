package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.UserAccountDTO;
import com.l.erp.authservice.api.dto.UserAccountPageDTO;
import com.l.erp.authservice.api.mappers.UserMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.Constants;
import com.l.erp.authservice.util.PasswordValidatorUtil;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserAccountRepository userAccountRepository;

    private final AuditService auditService;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final TenantRepository tenantRepository;

    private final PasswordValidatorUtil passwordValidatorUtil;

    private final OwnerMarkerRepository ownerMarkerRepository;

    public UserService(
            UserAccountRepository userAccountRepository,
            AuditService auditService,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            TenantRepository tenantRepository,
            PasswordValidatorUtil passwordValidatorUtil,
            OwnerMarkerRepository ownerMarkerRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.auditService = auditService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.passwordValidatorUtil = passwordValidatorUtil;
        this.ownerMarkerRepository = ownerMarkerRepository;
    }

    /**
     *
     * @param pageable pageable com as configs da página
     * @return page com os dados do user
     */
    public Page<UserAccountPageDTO> getAllAccounts(Pageable pageable) {
        logger.debug("REST request to get all Users");
        return userAccountRepository.findAllProjectedBy(pageable);
    }

    public Page<UserAccountPageDTO> getAllAccountsActive(Pageable pageable) {
        logger.debug("REST request to get all Users using a Pageable");
        return userAccountRepository.findAllActiveProjectedBy(pageable);
    }

    /**
     * Metod para criar um usuário
     * @param userDTO objeto referente ao usuário
     * @return usuário criado
     */
    public UserAccountDTO createUser(UserAccountDTO userDTO) {
        logger.debug("Creating new user: {}", userDTO.email());

        // Verifica se o email já existe
        if (userAccountRepository.findByEmail(userDTO.email()).isPresent()) {
            throw new BusinessException("E-mail já está em uso", HttpStatus.BAD_REQUEST);
        }

        // Vinculando o Tenant
        Tenant tenant;
        if (userDTO.tenantId() != null) {
            tenant = tenantRepository.findById(userDTO.tenantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant não encontrado"));
        } else {
            throw new BusinessException("O ID do Tenant é obrigatório para criar um usuário", HttpStatus.BAD_REQUEST);
        }

        // --> VALIDAÇÃO DE SENHA (Passay) <--
        // Passamos o texto plano (antes de encodar) e o nome do tenant
        passwordValidatorUtil.validatePassword(userDTO.passwordHash(), tenant.getName());

        UserAccount user = new UserAccount();
        user.setEmail(userDTO.email());
        user.setDisplayName(userDTO.displayName());
        user.setActive(true); // Usuário nasce ativo por padrão
        user.setFailedLoginAttempts(0);
        user.setCreatedDate(Instant.now());
        user.setTenant(tenant);

        // Criptografando a senha recebida
        user.setPasswordHash(passwordEncoder.encode(userDTO.passwordHash()));

        // Dados de auditoria
        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        user.setCreatedBy(currentUser.email());

        UserAccount savedUser = userAccountRepository.save(user);

        // Pegando o Correlation ID da requisição
        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.USER_CREATION, Constants.USER, savedUser.getId(), Constants.SUCCESS, null, correlationId);


        return userMapper.toUserAccountDTO(savedUser);
    }

    /**
     * Alterar Status do usuario
     * @param userId Id do User
     */
    public void updateUserStatusById(UUID userId){
        UserAccount userAccount = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.BAD_REQUEST));

        boolean existsOwnerMarker = ownerMarkerRepository.existsByUser_IdAndEnabledTrue(userId);
        if(existsOwnerMarker){
            throw new BusinessException(Constants.USER_HAS_OWNER_MARKER, HttpStatus.BAD_REQUEST);
        }

        userAccount.setActive(!userAccount.isActive());
        userAccountRepository.save(userAccount);

        UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.USER_UPDATE, Constants.USER, userId, Constants.SUCCESS, null, correlationId);
    }
}
