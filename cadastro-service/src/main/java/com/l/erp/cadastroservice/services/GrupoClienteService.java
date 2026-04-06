package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.GrupoClienteDTO;
import com.l.erp.cadastroservice.api.mappers.GrupoClienteMapper;
import com.l.erp.cadastroservice.domain.GrupoCliente;
import com.l.erp.cadastroservice.repository.GrupoClienteRepository;
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
public class GrupoClienteService {

    private final Logger logger = LoggerFactory.getLogger(GrupoClienteService.class);

    private final GrupoClienteRepository repository;

    private final GrupoClienteMapper mapper;

    private final AuditProducerService auditProducer;

    public GrupoClienteService(
            GrupoClienteRepository repository,
            GrupoClienteMapper mapper, AuditProducerService auditProducer) {
        this.repository = repository;
        this.mapper = mapper;
        this.auditProducer = auditProducer;
    }

    /**
     * Retorna todos os grupos de clientes de um determinado tenant
     * @param tenantId
     * @param pageable
     * @return
     */
    @Transactional(readOnly = true)
    public Page<GrupoClienteDTO> getAllGroups(Long tenantId, Pageable pageable) {
        logger.debug("Buscando todos os grupos de clientes para o tenant {}", tenantId);
        return repository.findAllByTenantId(tenantId, pageable)
                .map(mapper::toDto);
    }

    /**
     * Retrieves a customer group by its identifier and tenant ID.
     *
     * @param id       The unique identifier of the customer group to retrieve.
     * @param tenantId The identifier of the tenant to which the customer group belongs.
     * @return A {@code GrupoClienteDTO} representing the customer group.
     * @throws BusinessException If no customer group is found for the given {@code id} and {@code tenantId}.
     */
    @Transactional(readOnly = true)
    public GrupoClienteDTO findById(UUID id, Long tenantId) {
        logger.debug("Buscando grupo de cliente pelo id {} para o tenant {}", id, tenantId);
        return repository.findByIdAndTenantId(id, tenantId)
                .map(mapper::toDto)
                .orElseThrow(() -> new BusinessException(Constants.GROUP_C_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /**
     * Saves a new customer group in the system if it does not already exist for the given tenant.
     *
     * @param dto      The data transfer object containing the details of the customer group to save.
     * @param tenantId The identifier of the tenant for which the customer group is being created.
     * @param userId   The identifier of the user creating the customer group.
     * @return The data transfer object representing the saved customer group entity.
     * @throws ResponseStatusException If a customer group with the same name already exists for the tenant.
     */
    @Transactional
    public GrupoClienteDTO save(GrupoClienteDTO dto, Long tenantId, UUID userId) {
        logger.debug("Salvando grupo de cliente para o tenant {}", tenantId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        if (repository.existsByTenantIdAndNomeIgnoreCase(tenantId, dto.nome())) {
            sendAuditEvent(
                    Constants.GROUP_C_CREATION,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR: "+Constants.GROUP_C_ALREADY_EXISTS+"}",
                    correlationId
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, Constants.GROUP_C_ALREADY_EXISTS);
        }

        GrupoCliente entity = GrupoCliente.builder()
                .tenantId(tenantId)
                .nome(dto.nome())
                .descricao(dto.descricao())
                .ativo(dto.ativo() != null ? dto.ativo() : true)
                .createdBy(userId)
                .build();

        GrupoCliente saved = repository.save(entity);
        sendAuditEvent(
                Constants.GROUP_C_CREATION,
                userId,
                saved.getId(),
                Constants.SUCCESS,
                null,
                correlationId
        );

        return mapper.toDto(saved);
    }

    public GrupoClienteDTO update(UUID id, @Valid GrupoClienteDTO dto, Long tenantID, UUID userId) {
        logger.debug("Updating Group Client: {}", id);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        GrupoCliente oldGrupoCliente = repository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.GROUP_C_NOT_FOUND, HttpStatus.NOT_FOUND));

        if(!oldGrupoCliente.getTenantId().equals(tenantID)){
            sendAuditEvent(
                    Constants.GROUP_C_UPDATE,
                    userId,
                    null,
                    Constants.ERROR,
                    "{ERROR:"+Constants.TENANT_ASSOC_ERROR+";\nValidar Segurança: Y;}",
                    correlationId
            );
            throw new BusinessException(Constants.TENANT_ASSOC_ERROR, HttpStatus.BAD_REQUEST);
        }

        GrupoCliente grupoCliente = mapper.toEntity(dto);
        grupoCliente.setId(id);
        grupoCliente.setUpdatedAt(Instant.now());
        grupoCliente.setLastUpdatedBy(userId);

        GrupoCliente saved = repository.save(grupoCliente);
        sendAuditEvent(
                Constants.GROUP_C_UPDATE,
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
