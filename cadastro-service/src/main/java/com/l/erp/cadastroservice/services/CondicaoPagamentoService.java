package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoDTO;
import com.l.erp.cadastroservice.api.mappers.CondicaoPagamentoMapper;
import com.l.erp.cadastroservice.domain.CondicaoPagamento;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoRepository;
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
public class CondicaoPagamentoService {

    private final Logger logger = LoggerFactory.getLogger(CondicaoPagamentoService.class);

    private final CondicaoPagamentoRepository repository;

    private final CondicaoPagamentoMapper mapper;

    private final AuditProducerService auditProducer;

    public CondicaoPagamentoService(
            CondicaoPagamentoRepository repository,
            CondicaoPagamentoMapper mapper,
            AuditProducerService auditProducer) {
        this.repository = repository;
        this.mapper = mapper;
        this.auditProducer = auditProducer;
    }

    /**
     * Retorna todos as condições de pagamento de um determinado tenant
     * @param tenantId Tenant ID
     * @param pageable
     * @return
     */
    @Transactional(readOnly = true)
    public Page<CondicaoPagamentoDTO> getAllConditions(Long tenantId, Pageable pageable) {
        logger.debug("Buscando todos as condições de pagamento para o tenant {}", tenantId);
        return repository.findAllByTenantId(tenantId, pageable)
                .map(mapper::toDto);
    }

    /**
     * Retrieves a "CondicaoPagamentoDTO" (Payment Condition DTO) by its unique identifier and tenant ID.
     *
     * @param id       The unique identifier of the payment condition.
     * @param tenantId The tenant ID associated with the payment condition.
     * @return The data transfer object representing the payment condition.
     * @throws BusinessException If the payment condition is not found for the given ID and tenant ID.
     */
    @Transactional(readOnly = true)
    public CondicaoPagamentoDTO findById(UUID id, Long tenantId) {
        logger.debug("Buscando Condicao de Pagamento pelo id {} para o tenant {}", id, tenantId);
        return repository.findByIdAndTenantId(id, tenantId)
                .map(mapper::toDto)
                .orElseThrow(() -> new BusinessException(Constants.COND_PAG_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /**
     * Saves a payment condition for the given tenant.
     *
     * @param dto The data transfer object containing the details of the payment condition to be saved.
     * @param tenantId The ID of the tenant associated with the payment condition.
     * @param userId The UUID of the user performing the operation.
     * @return A {@code CondicaoPagamentoDTO} representing the saved payment condition.
     * @throws ResponseStatusException If a payment condition with the same name already exists for the tenant.
     */
    @Transactional
    public CondicaoPagamentoDTO save(CondicaoPagamentoDTO dto, Long tenantId, UUID userId) {
        logger.debug("Salvando condicao de pagamento para o tenant {}", tenantId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        if (repository.existsByTenantIdAndNomeIgnoreCase(tenantId, dto.nome())) {
            sendAuditEvent(
                    Constants.COND_PAG,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR: "+Constants.COND_PAG_ALREADY_EXISTS+"}",
                    correlationId
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, Constants.COND_PAG_ALREADY_EXISTS);
        }

        CondicaoPagamento entity = CondicaoPagamento.builder()
                .tenantId(tenantId)
                .nome(dto.nome())
                .descricao(dto.descricao())
                .ativo(dto.ativo())
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        CondicaoPagamento saved = repository.save(entity);
        sendAuditEvent(
                Constants.COND_PAG_CREATION,
                userId,
                saved.getId(),
                Constants.SUCCESS,
                null,
                correlationId
        );

        return mapper.toDto(saved);
    }

    public CondicaoPagamentoDTO update(UUID id, @Valid CondicaoPagamentoDTO dto, Long tenantID, UUID userId) {
        logger.debug("Updating Condition Payment: {}", id);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        CondicaoPagamento oldCP = repository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.COND_PAG_NOT_FOUND, HttpStatus.NOT_FOUND));

        if(!oldCP.getTenantId().equals(tenantID)){
            sendAuditEvent(
                    Constants.COND_PAG_UPDATE,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR:"+Constants.TENANT_ASSOC_ERROR+";\nValidar Segurança: Y;}",
                    correlationId
            );
            throw new BusinessException(Constants.TENANT_ASSOC_ERROR, HttpStatus.BAD_REQUEST);
        }

        CondicaoPagamento condicaoPagamento = mapper.toEntity(dto);
        condicaoPagamento.setId(id);
        condicaoPagamento.setCreatedBy(oldCP.getCreatedBy());
        condicaoPagamento.setCreatedAt(oldCP.getCreatedAt());
        condicaoPagamento.setUpdatedAt(Instant.now());
        condicaoPagamento.setLastUpdatedBy(userId);
        condicaoPagamento.setTenantId(tenantID);

        CondicaoPagamento saved = repository.save(condicaoPagamento);
        sendAuditEvent(
                Constants.COND_PAG_UPDATE,
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
