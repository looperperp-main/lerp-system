package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.DepositoDTO;
import com.l.erp.cadastroservice.api.mappers.DepositoMapper;
import com.l.erp.cadastroservice.domain.Deposito;
import com.l.erp.cadastroservice.repository.DepositoRepository;
import com.l.erp.common.util.Constants;
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
public class DepositoService {

    private final Logger logger = LoggerFactory.getLogger(DepositoService.class);

    private final AuditProducerService auditProducer;

    private final DepositoMapper mapper;

    private final DepositoRepository repository;

    public DepositoService(AuditProducerService auditProducer, DepositoMapper mapper, DepositoRepository repository) {
        this.auditProducer = auditProducer;
        this.mapper = mapper;
        this.repository = repository;
    }

    /**
     * Retrieves a paginated list of deposit records associated with the specified tenant.
     *
     * @param tenantId the ID of the tenant whose deposit records are to be retrieved
     * @param pageable the pagination information, including page number and size
     * @return a {@code Page} containing {@code DepositoDTO} objects representing the deposit records
     */
    @Transactional(readOnly = true)
    public Page<DepositoDTO> findAllDepositos(Long tenantId, Pageable pageable) {
        logger.debug("Buscando todos os depositos para o tenant {}", tenantId);
        return repository.findAllByTenantId(tenantId, pageable)
                .map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public DepositoDTO findById(UUID id, Long tenantId) {
        logger.debug("Buscando {} pelo id {} para o tenant {}", Deposito.class.getSimpleName() , id, tenantId);
        return repository.findByTenantIdAndId( tenantId, id)
                .map(mapper::toDto)
                .orElseThrow(() -> new BusinessException(Constants.DEPOSITO_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public DepositoDTO save(DepositoDTO dto, Long tenantId, UUID userId) {
        logger.debug("Salvando {} para o tenant {}", Deposito.class.getSimpleName(), tenantId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        if (repository.existsByTenantIdAndNomeIgnoreCase(tenantId, dto.nome())) {
            sendAuditEvent(
                    Constants.DEPOSITO_CREATION,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR: "+Constants.DEPOSITO_ALREADY_EXISTS+"}",
                    correlationId
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, Constants.DEPOSITO_CREATION);
        }

        Deposito entity = Deposito.builder()
                .tenantId(tenantId)
                .nome(dto.nome())
                .descricao(dto.descricao())
                .tipo(dto.tipo())
                .ativo(dto.ativo() != null ? dto.ativo() : true)
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();

        Deposito saved = repository.save(entity);
        sendAuditEvent(
                Constants.DEPOSITO_CREATION,
                userId,
                saved.getId(),
                Constants.SUCCESS,
                null,
                correlationId
        );

        return mapper.toDto(saved);
    }

    public DepositoDTO update(UUID id, @Valid DepositoDTO dto, Long tenantID, UUID userId) {
        logger.debug("Updating {}: {}",Deposito.class.getSimpleName(), id);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        Deposito oldDeposito = repository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.DEPOSITO_NOT_FOUND, HttpStatus.NOT_FOUND));

        if(!oldDeposito.getTenantId().equals(tenantID)){
            sendAuditEvent(
                    Constants.DEPOSITO_UPDATE,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR:"+Constants.TENANT_ASSOC_ERROR+";\nValidar Segurança: Y;}",
                    correlationId
            );
            throw new BusinessException(Constants.TENANT_ASSOC_ERROR, HttpStatus.BAD_REQUEST);
        }

        Deposito deposito = mapper.toEntity(dto);
        deposito.setId(id);
        deposito.setCreatedBy(oldDeposito.getCreatedBy());
        deposito.setCreatedAt(oldDeposito.getCreatedAt());
        deposito.setUpdatedAt(Instant.now());
        deposito.setLastUpdatedBy(userId);
        deposito.setTenantId(tenantID);

        Deposito saved = repository.save(deposito);
        sendAuditEvent(
                Constants.DEPOSITO_UPDATE,
                userId,
                id,
                Constants.SUCCESS,
                null,
                correlationId
        );

        return mapper.toDto(saved);
    }

    /**
     * Sends an audit event to the audit producer, containing information about an action performed in the system.
     *
     * @param actorId       The unique identifier of the actor performing the action.
     * @param targetId      The unique identifier of the target entity involved in the action (can be null).
     * @param result        The result of the action, typically represented as a string (e.g., "SUCCESS", "ERROR").
     * @param detailsJson   A JSON string containing additional details about the event (can be null).
     * @param correlationId A unique identifier used to correlate logs and events for tracing purposes.
     */
    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent;
        auditEvent = new AuditEventDTO(

                action,
                actorId,
                Constants.GROUP_C,
                targetId,
                result,
                detailsJson,
                correlationId,
                Instant.now()
        );

        auditProducer.sendAuditEvent(auditEvent);
    }
}
