package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.TabelaPrecoDTO;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.repository.filter.TenantContext;
import com.l.erp.cadastroservice.util.Constants;
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
public class TabelaPrecoService {
    private final Logger logger = LoggerFactory.getLogger(TabelaPrecoService.class);

    private final TabelaPrecoRepository repository;
    private final AuditProducerService auditProducer;

    public TabelaPrecoService(TabelaPrecoRepository repository, AuditProducerService auditProducer) {
        this.repository = repository;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<TabelaPreco> getAll(Pageable pageable) {
        logger.info("Buscando todas as tabelas de preço");
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public TabelaPreco findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("Tabela de Preço não encontrada - id: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public TabelaPreco save(TabelaPrecoDTO dto, UUID userId) {
        logger.info("Salvando nova tabela de preço: {}", dto.nome());
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Long tenantId = TenantContext.getTenantId();

        if (repository.existsByNomeIgnoreCaseAndTenantId(dto.nome(), tenantId)) {
            sendAuditEvent(Constants.TABELA_PRECO_CREATION, userId, null, Constants.ERROR, "{ERROR: "+Constants.TABELA_PRECO_ALREADY_EXISTS+"}", correlationID);
            throw new BusinessException(Constants.TABELA_PRECO_ALREADY_EXISTS+": " + dto.nome(), HttpStatus.BAD_REQUEST);
        }

        if (Boolean.TRUE.equals(dto.padrao()) && repository.existsByPadraoIsTrueAndTenantId(tenantId)) {
            sendAuditEvent(Constants.TABELA_PRECO_CREATION, userId, null, Constants.ERROR, "{ERROR: "+Constants.TABELA_PRECO_PADRAO_ALREADY_EXISTS+"}", correlationID);
            throw new BusinessException(Constants.TABELA_PRECO_PADRAO_ALREADY_EXISTS+": " + dto.nome(), HttpStatus.BAD_REQUEST);
        }

        TabelaPreco tabelaPreco = TabelaPreco.builder()
                .nome(dto.nome())
                .moeda(dto.moeda() != null ? dto.moeda() : "BRL")
                .ativa(dto.ativa() != null ? dto.ativa() : true)
                .padrao(dto.padrao() != null && dto.padrao())
                .inicioVigencia(dto.inicioVigencia())
                .fimVigencia(dto.fimVigencia())
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();

        tabelaPreco.setTenantId(tenantId);

        TabelaPreco saved = repository.save(tabelaPreco);

        sendAuditEvent(Constants.TABELA_PRECO_CREATION, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public TabelaPreco update(UUID id, TabelaPrecoDTO dto, UUID userId) {
        logger.info("Atualizando tabela de preço {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Long tenantId = TenantContext.getTenantId();

        TabelaPreco tabelaPreco = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.TABELA_PRECO_UPDATE, userId, null, Constants.ERROR, "{ERROR: "+Constants.TABELA_PRECO_NOT_FOUND+"}", correlationID);
                    return new BusinessException(Constants.TABELA_PRECO_NOT_FOUND + " - id: " + id, HttpStatus.NOT_FOUND);
                });

        if (!tabelaPreco.getNome().equalsIgnoreCase(dto.nome()) && repository.existsByNomeIgnoreCaseAndTenantId(dto.nome(), tenantId)) {
            sendAuditEvent(Constants.TABELA_PRECO_UPDATE, userId, null, Constants.ERROR, "{ERROR: "+Constants.TABELA_PRECO_ALREADY_EXISTS+"}", correlationID);
            throw new BusinessException(Constants.TABELA_PRECO_ALREADY_EXISTS+": " + dto.nome(), HttpStatus.BAD_REQUEST);
        }

        if (Boolean.TRUE.equals(dto.padrao()) && repository.existsByPadraoIsTrueAndTenantId(tenantId)) {
            sendAuditEvent(Constants.TABELA_PRECO_CREATION, userId, null, Constants.ERROR, "{ERROR: "+Constants.TABELA_PRECO_PADRAO_ALREADY_EXISTS+"}", correlationID);
            throw new BusinessException(Constants.TABELA_PRECO_PADRAO_ALREADY_EXISTS+": " + dto.nome(), HttpStatus.BAD_REQUEST);
        }

        tabelaPreco.setNome(dto.nome());
        tabelaPreco.setMoeda(dto.moeda());
        tabelaPreco.setAtiva(dto.ativa() != null ? dto.ativa() : tabelaPreco.getAtiva());
        tabelaPreco.setPadrao(dto.padrao());
        tabelaPreco.setInicioVigencia(dto.inicioVigencia());
        tabelaPreco.setFimVigencia(dto.fimVigencia());
        tabelaPreco.setUpdatedAt(Instant.now());
        tabelaPreco.setLastUpdatedBy(userId);

        TabelaPreco updated = repository.save(tabelaPreco);

        sendAuditEvent(Constants.TABELA_PRECO_UPDATE, userId, updated.getId(), Constants.SUCCESS, null, correlationID);
        return updated;
    }

    @Transactional
    public void updateStatus(UUID id, UUID userId) {
        logger.info("Alterando status da tabela de preço {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        TabelaPreco tabelaPreco = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.TABELA_PRECO_UPDATE, userId, null, Constants.ERROR, "{ERROR: "+Constants.TABELA_PRECO_NOT_FOUND+"}", correlationID);
                    return new BusinessException(Constants.TABELA_PRECO_NOT_FOUND+"- id: " + id, HttpStatus.NOT_FOUND);
                });

        tabelaPreco.setAtiva(!tabelaPreco.getAtiva());
        tabelaPreco.setUpdatedAt(Instant.now());
        tabelaPreco.setLastUpdatedBy(userId);

        repository.save(tabelaPreco);

        sendAuditEvent(Constants.TABELA_PRECO_UPDATE, userId, id, Constants.SUCCESS, "{\"status_alterado\": " + tabelaPreco.getAtiva() + "}", correlationID);
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.TABELA_PRECO, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }
}