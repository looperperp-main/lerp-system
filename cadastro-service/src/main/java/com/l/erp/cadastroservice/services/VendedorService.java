package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.VendedorDTO;
import com.l.erp.cadastroservice.api.mappers.VendedorAssembler;
import com.l.erp.cadastroservice.api.mappers.VendedorMapper;
import com.l.erp.cadastroservice.domain.Vendedor;
import com.l.erp.cadastroservice.repository.VendedorRepository;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.common.api.dto.AuditEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class VendedorService {
    private final Logger logger = LoggerFactory.getLogger(VendedorService.class);
    private final VendedorRepository repository;
    private final VendedorMapper mapper;
    private final AuditProducerService auditProducer;

    public VendedorService(VendedorRepository repository, VendedorMapper mapper, AuditProducerService auditProducer) {
        this.repository = repository;
        this.mapper = mapper;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<Vendedor> getAllVendedores(Long tenantId, Pageable pageable) {
        logger.info("Buscando todos os vendedores do tenant {}", tenantId);
        return repository.findAllByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Vendedor findById(UUID id, Long tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException(Constants.VENDEDOR_NOT_FOUND +" - id: " + id));
    }

    @Transactional
    public Vendedor save(VendedorDTO dto, Long tenantId, UUID userId) {
        logger.info("Salvando novo vendedor no tenant {}", tenantId);

        UUID correlationID = getCorrelationIdFromRequest(logger);

        if (repository.existsByTenantIdAndNomeIgnoreCase(tenantId, dto.nome())) {
            sendAuditEvent(Constants.VENDEDOR_CREATION, userId, null, Constants.ERROR, "{ERROR: "+Constants.VENDEDOR_ALREADY_EXISTS+"}", correlationID);
            throw new RuntimeException(Constants.VENDEDOR_ALREADY_EXISTS);
        }

        Vendedor vendedor = Vendedor.builder()
                .tenantId(tenantId)
                .pessoaId(dto.pessoaId())
                .nome(dto.nome())
                .comissaoPercentual(dto.comissaoPercentual())
                .ativo(dto.ativo() != null ? dto.ativo() : true)
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();
        Vendedor saved = repository.save(vendedor);
        sendAuditEvent(Constants.VENDEDOR_CREATION, userId, saved.getId(), Constants.SUCCESS, null, correlationID);

        return saved;
    }

    @Transactional
    public Vendedor update(UUID id, VendedorDTO dto, Long tenantId, UUID userId) {
        logger.info("Atualizando vendedor com id {} no tenant {}", id, tenantId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        Vendedor vendedor = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.VENDEDOR_UPDATE, userId, null, Constants.ERROR, "{ERROR: "+Constants.VENDEDOR_NOT_FOUND+"}", correlationID);
                    return new RuntimeException(Constants.VENDEDOR_NOT_FOUND + " - id: " + id);
                });

        if (!vendedor.getNome().equalsIgnoreCase(dto.nome()) &&
                repository.existsByTenantIdAndNomeIgnoreCase(tenantId, dto.nome())) {
            sendAuditEvent(Constants.VENDEDOR_UPDATE, userId, null, Constants.ERROR, "{ERROR: "+Constants.VENDEDOR_ALREADY_EXISTS+"}", correlationID);
            throw new RuntimeException(Constants.VENDEDOR_ALREADY_EXISTS);
        }

        vendedor.setNome(dto.nome());
        vendedor.setPessoaId(dto.pessoaId());
        vendedor.setComissaoPercentual(dto.comissaoPercentual());
        vendedor.setAtivo(dto.ativo());
        vendedor.setUpdatedAt(Instant.now());
        vendedor.setLastUpdatedBy(userId);

        Vendedor updated = repository.save(vendedor);
        sendAuditEvent(Constants.VENDEDOR_UPDATE, userId, updated.getId(), Constants.SUCCESS, null, correlationID);
        return updated;
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.PESSOA, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }
}
