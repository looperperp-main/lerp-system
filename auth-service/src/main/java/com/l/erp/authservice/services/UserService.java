package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.UserAccountDTO;
import com.l.erp.authservice.api.dto.UserAccountPageDTO;
import com.l.erp.authservice.api.mappers.UserMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.PasswordValidatorUtil;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BussinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String ENTITY_NAME = "User";

    private final UserAccountRepository userAccountRepository;

    private final AuditService auditService;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final TenantRepository tenantRepository;

    private final PasswordValidatorUtil passwordValidatorUtil;

    public UserService(
            UserAccountRepository userAccountRepository,
            AuditService auditService,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            TenantRepository tenantRepository,
            PasswordValidatorUtil passwordValidatorUtil
    ) {
        this.userAccountRepository = userAccountRepository;
        this.auditService = auditService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.passwordValidatorUtil = passwordValidatorUtil;
    }

    /**
     *
     * @param pageable
     * @return
     */
    public Page<UserAccountPageDTO> getAllAccounts(Pageable pageable) {
        logger.debug("REST request to get all Users");
        return userAccountRepository.findAllProjectedBy(pageable);
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
            throw new BussinessException("E-mail já está em uso", HttpStatus.BAD_REQUEST);
        }

        // Vinculando o Tenant
        Tenant tenant;
        if (userDTO.tenantId() != null) {
            tenant = tenantRepository.findById(userDTO.tenantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant não encontrado"));
        } else {
            throw new BussinessException("O ID do Tenant é obrigatório para criar um usuário", HttpStatus.BAD_REQUEST);
        }

        // --> VALIDAÇÃO DE SENHA (Passay) <--
        // Passamos o texto plano (antes de encodar) e o nome do tenant
        passwordValidatorUtil.validatePassword(userDTO.passwordHash(), tenant.getName());

        UserAccount user = new UserAccount();
        user.setEmail(userDTO.email());
        user.setDisplayName(userDTO.displayName());
        user.setActive(true); // Usuário nasce ativo por padrão
        user.setCreatedDate(Instant.now());
        user.setTenant(tenant);

        // Criptografando a senha recebida
        user.setPasswordHash(passwordEncoder.encode(userDTO.passwordHash()));

        // Dados de auditoria
        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        user.setCreatedBy(currentUser.email());

        UserAccount savedUser = userAccountRepository.save(user);

        // Pegando o Correlation ID da requisição
        UUID correlationId = getCorrelationIdFromRequest();
        auditService.logAuditEvent("USER_INSERT", "UserAccount", savedUser.getId(), "SUCCESS", null, correlationId);


        return userMapper.toUserAccountDTO(savedUser);
    }

    /**
    * Métod auxiliar para extrair o Correlation ID do Header da Requisição atual.
    */
    private UUID getCorrelationIdFromRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            // Substitua "X-Correlation-ID" pelo nome do header que o seu Gateway ou Front-end está enviando
            String headerCorId = request.getHeader("X-Correlation-ID");

            if (headerCorId != null && !headerCorId.isBlank()) {
                try {
                    return UUID.fromString(headerCorId);
                } catch (IllegalArgumentException e) {
                    logger.warn("Formato de Correlation ID inválido recebido no header: {}", headerCorId);
                }
            }
        }
        // Se não houver contexto web (ex: rotina assíncrona/batch) ou o header não foi enviado,
        // gera um novo para não deixar a auditoria vazia.
        return UUID.randomUUID();
    }
}
