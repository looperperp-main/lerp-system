package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.UserAccountPageDTO;
import com.l.erp.authservice.api.mappers.UserMapper;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.services.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String ENTITY_NAME = "User";

    private final UserAccountRepository userAccountRepository;

    private final AuditService auditService;

    private final UserMapper userMapper;

    public UserService(
            UserAccountRepository userAccountRepository,
            AuditService auditService,
            UserMapper userMapper
    ) {
        this.userAccountRepository = userAccountRepository;
        this.auditService = auditService;
        this.userMapper = userMapper;
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
}
