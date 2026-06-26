package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.TransportadoraDTO;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.Transportadora;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.repository.TransportadoraRepository;
import com.l.erp.cadastroservice.repository.filter.TenantContext;
import com.l.erp.common.util.Constants;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.exception.custom.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class TransportadoraService {
    private final Logger logger = LoggerFactory.getLogger(TransportadoraService.class);

    private final TransportadoraRepository transportadoraRepository;
    private final PessoaRepository pessoaRepository;
    private final AuditProducerService auditProducer;

    public TransportadoraService(TransportadoraRepository transportadoraRepository,
                                 PessoaRepository pessoaRepository,
                                 AuditProducerService auditProducer) {
        this.transportadoraRepository = transportadoraRepository;
        this.pessoaRepository = pessoaRepository;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<Transportadora> getAllTransportadoras(Pageable pageable) {
        logger.info("Buscando todas as transportadoras (Tenant filtrado por Aspecto)");
        return transportadoraRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Transportadora findById(UUID id) {
        Long tenantId = TenantContext.getTenantId();
        return transportadoraRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.TRANSPORTADORA_NOT_FOUND+" - id: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Transportadora save(TransportadoraDTO dto, UUID userId) {
        logger.info("Salvando nova transportadora");
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Long tenantId = TenantContext.getTenantId();

        if (transportadoraRepository.existsByPessoaId(dto.pessoaId())) {
            sendAuditEvent(Constants.TRANSPORTADORA_CREATION, userId, null, Constants.ERROR, "{ERROR: "+Constants.TRANSPORTADORA_ALREADY_EXISTS+"}", correlationID);
            throw new BusinessException("Transportadora já cadastrada para esta pessoa - pessoaId: " + dto.pessoaId(), HttpStatus.BAD_REQUEST);
        }

        Pessoa pessoa = pessoaRepository.findByIdAndTenantId(dto.pessoaId(), tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.BAD_REQUEST));

        Transportadora transportadora = Transportadora.builder()
                .pessoa(pessoa)
                .rntrc(dto.rntrc())
                .modal(dto.modal())
                .ativo(dto.ativo() != null ? dto.ativo() : true)
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();

        transportadora.setTenantId(tenantId);

        Transportadora saved = transportadoraRepository.save(transportadora);

        sendAuditEvent("TRANSPORTADORA_CREATION", userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public Transportadora update(UUID id, TransportadoraDTO dto, UUID userId) {
        logger.info("Atualizando transportadora {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Long tenantId = TenantContext.getTenantId();

        Transportadora transportadora = transportadoraRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.TRANSPORTADORA_UPDATE, userId, null, Constants.ERROR, "{ERROR: "+Constants.TRANSPORTADORA_NOT_FOUND+"}", correlationID);
                    return new BusinessException("Transportadora não encontrada - id: " + id, HttpStatus.NOT_FOUND);
                });

        if (!transportadora.getPessoa().getId().equals(dto.pessoaId())) {
            if (transportadoraRepository.existsByPessoaId(dto.pessoaId())) {
                sendAuditEvent(Constants.TRANSPORTADORA_UPDATE, userId, null, Constants.ERROR, "{ERROR: A nova Pessoa já possui cadastro de Transportadora}", correlationID);
                throw new BusinessException("A nova Pessoa já possui cadastro de Transportadora", HttpStatus.BAD_REQUEST);
            }
            Pessoa novaPessoa = pessoaRepository.findByIdAndTenantId(dto.pessoaId(), tenantId)
                    .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.BAD_REQUEST));
            transportadora.setPessoa(novaPessoa);
        }

        transportadora.setRntrc(dto.rntrc());
        transportadora.setModal(dto.modal());
        transportadora.setAtivo(dto.ativo() != null ? dto.ativo() : transportadora.getAtivo());
        transportadora.setUpdatedAt(Instant.now());
        transportadora.setLastUpdatedBy(userId);

        Transportadora updated = transportadoraRepository.save(transportadora);

        sendAuditEvent(Constants.TRANSPORTADORA_UPDATE, userId, updated.getId(), Constants.SUCCESS, null, correlationID);
        return updated;
    }

    @Transactional
    public void updateStatus(UUID id, UUID userId) {
        logger.info("Alterando status da transportadora {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Long tenantId = TenantContext.getTenantId();

        Transportadora transportadora = transportadoraRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.TRANSPORTADORA_UPDATE, userId, null, Constants.ERROR, "{ERROR: "+Constants.TRANSPORTADORA_NOT_FOUND+"}", correlationID);
                    return new BusinessException("Transportadora não encontrada - id: " + id, HttpStatus.NOT_FOUND);
                });

        transportadora.setAtivo(!transportadora.getAtivo());
        transportadora.setUpdatedAt(Instant.now());
        transportadora.setLastUpdatedBy(userId);

        transportadoraRepository.save(transportadora);

        sendAuditEvent(Constants.TRANSPORTADORA_UPDATE, userId, id, Constants.SUCCESS, "{\"status_alterado\": " + transportadora.getAtivo() + "}", correlationID);
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.TRANSPORTADORA, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }
}