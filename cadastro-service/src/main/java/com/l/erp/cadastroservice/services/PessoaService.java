package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.PessoaRequestDTO;
import com.l.erp.cadastroservice.api.mappers.PessoaMapper;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.exception.custom.BusinessException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class PessoaService {

    private final Logger logger = LoggerFactory.getLogger(PessoaService.class);
    private final PessoaMapper pessoaMapper;
    private final PessoaRepository pessoaRepository;
    private final AuditProducerService auditService;

    public PessoaService(PessoaMapper pessoaMapper, PessoaRepository pessoaRepository, AuditProducerService auditService) {
        this.pessoaMapper = pessoaMapper;
        this.pessoaRepository = pessoaRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Pessoa> findAllByTenant(Long tenantId, Pageable pageable) {
        logger.debug("Buscando pessoas para o tenant {}", tenantId);
        return pessoaRepository.findAllByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Pessoa findByIdAndTenant(UUID id, Long tenantId) {
        logger.debug("Buscando pessoa {} para tenant {}", id, tenantId);
        return pessoaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Pessoa create(PessoaRequestDTO dto, Long tenantId, UUID userId) {
        logger.debug("Criando pessoa para o tenant {}", tenantId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        if (pessoaRepository.existsByDocumentoAndNomeRazaoAndTenantId(dto.documento(),dto.nomeRazao(), tenantId)) {
            sendAuditEvent(
                    Constants.PESSOA_CREATION,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR: "+Constants.PESSOA_ALREADY_EXISTS+"}",
                    correlationId
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, Constants.PESSOA_ALREADY_EXISTS);
        }

        Pessoa entity = pessoaMapper.toEntityRequest(dto);
        entity.setTenantId(tenantId);
        entity.setCreatedBy(userId);
        entity.setCreatedAt(Instant.now());

        Pessoa saved = pessoaRepository.save(entity);
        sendAuditEvent("PESSOA_CREATION", userId, saved.getId(), Constants.SUCCESS, null, correlationId);

        return saved;
    }

    public Pessoa update(UUID id, @Valid PessoaRequestDTO dto, Long tenantID, UUID userId) {
        logger.debug("Updating Pessoa: {}", id);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        Pessoa oldPessoa = pessoaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND));

        if(!oldPessoa.getTenantId().equals(tenantID)){
            sendAuditEvent(
                    Constants.PESSOA_UPDATE,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR:"+Constants.TENANT_ASSOC_ERROR+";\nValidar Segurança: Y;}",
                    correlationId
            );
            throw new BusinessException(Constants.TENANT_ASSOC_ERROR, HttpStatus.BAD_REQUEST);
        }

        Pessoa pessoa = pessoaMapper.toEntityRequest(dto);
        pessoa.setId(id);
        pessoa.setCreatedBy(oldPessoa.getCreatedBy());
        pessoa.setCreatedAt(oldPessoa.getCreatedAt());
        pessoa.setUpdatedAt(Instant.now());
        pessoa.setLastUpdatedBy(userId);
        pessoa.setTenantId(tenantID);

        Pessoa saved = pessoaRepository.save(pessoa);
        sendAuditEvent(
                Constants.PESSOA_UPDATE,
                userId,
                id,
                Constants.SUCCESS,
                null,
                correlationId
        );

        return saved;
    }


    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.PESSOA, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditService.sendAuditEvent(auditEvent);
    }
}
