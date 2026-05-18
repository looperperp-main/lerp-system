package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.FornecedorDto;
import com.l.erp.cadastroservice.domain.Fornecedor;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.repository.FornecedorRepository;
import com.l.erp.cadastroservice.repository.PessoaRepository;
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
public class FornecedorService {
    private final Logger logger = LoggerFactory.getLogger(FornecedorService.class);

    private final FornecedorRepository fornecedorRepository;
    private final PessoaRepository pessoaRepository;
    private final AuditProducerService auditProducer;

    public FornecedorService(FornecedorRepository fornecedorRepository,
                             PessoaRepository pessoaRepository,
                             AuditProducerService auditProducer) {
        this.fornecedorRepository = fornecedorRepository;
        this.pessoaRepository = pessoaRepository;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<Fornecedor> getAllFornecedores(Pageable pageable) {
        logger.info("Buscando todos os fornecedores (Tenant filtrado por Aspecto)");
        return fornecedorRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Fornecedor findById(UUID id) {
        return fornecedorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.FORNECEDORES_NOT_FOUND + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Fornecedor save(FornecedorDto dto, UUID userId) {
        logger.info("Salvando novo fornecedor");
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Long tenantId = TenantContext.getTenantId();

        if (fornecedorRepository.existsByPessoaId(dto.pessoaId())) {
            sendAuditEvent(Constants.FORNECEDORES_CREATION, userId, null, Constants.ERROR, "{ERROR: Fornecedor já existe para esta Pessoa}", correlationID);
            throw new BusinessException("Fornecedor já cadastrado para esta pessoa - pessoaId: " + dto.pessoaId(), HttpStatus.BAD_REQUEST);
        }

        // TODO: Supondo que PessoaRepository ainda use métodos com AndTenantId na sua base antiga, se tiver refatorado PessoaRepository, pode usar findById(id) também.
        Pessoa pessoa = pessoaRepository.findByIdAndTenantId(dto.pessoaId(), tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.BAD_REQUEST));

        Fornecedor fornecedor = Fornecedor.builder()
                .pessoa(pessoa)
                .ativo(dto.ativo())
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();

        fornecedor.setTenantId(tenantId); // Setando propriedade vinda do BaseTenantEntity

        Fornecedor saved = fornecedorRepository.save(fornecedor);

        sendAuditEvent("FORNECEDOR_CREATION", userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public Fornecedor update(UUID id, FornecedorDto dto, UUID userId) {
        logger.info("Atualizando fornecedor {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Long tenantId = TenantContext.getTenantId();

        Fornecedor fornecedor = fornecedorRepository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.FORNECEDORES_UPDATE, userId, null, Constants.ERROR, "{ERROR: Fornecedor não encontrado}", correlationID);
                    return new BusinessException(Constants.FORNECEDORES_NOT_FOUND + id, HttpStatus.NOT_FOUND);
                });

        if (!fornecedor.getPessoa().getId().equals(dto.pessoaId())) {
            if (fornecedorRepository.existsByPessoaId(dto.pessoaId())) {
                sendAuditEvent(Constants.FORNECEDORES_UPDATE, userId, null, Constants.ERROR, "{ERROR: A nova Pessoa já possui cadastro de Fornecedor}", correlationID);
                throw new BusinessException("A nova Pessoa já possui cadastro de Fornecedor", HttpStatus.BAD_REQUEST);
            }
            Pessoa novaPessoa = pessoaRepository.findByIdAndTenantId(dto.pessoaId(), tenantId)
                    .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.BAD_REQUEST));
            fornecedor.setPessoa(novaPessoa);
        }

        fornecedor.setAtivo(dto.ativo());
        fornecedor.setUpdatedAt(Instant.now());
        fornecedor.setLastUpdatedBy(userId);

        Fornecedor updated = fornecedorRepository.save(fornecedor);

        sendAuditEvent(Constants.FORNECEDORES_UPDATE, userId, updated.getId(), Constants.SUCCESS, null, correlationID);
        return updated;
    }

    @Transactional
    public void updateStatus(UUID id, UUID userId) {
        logger.info("Alterando status do fornecedor {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        Fornecedor fornecedor = fornecedorRepository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.FORNECEDORES_UPDATE, userId, null, Constants.ERROR, "{ERROR: Fornecedor não encontrado}", correlationID);
                    return new BusinessException(Constants.FORNECEDORES_NOT_FOUND + id, HttpStatus.NOT_FOUND);
                });

        fornecedor.setAtivo(!fornecedor.getAtivo());
        fornecedor.setUpdatedAt(Instant.now());
        fornecedor.setLastUpdatedBy(userId);

        fornecedorRepository.save(fornecedor);

        sendAuditEvent(Constants.FORNECEDORES_UPDATE, userId, id, Constants.SUCCESS, "{\"status_alterado\": " + fornecedor.getAtivo() + "}", correlationID);
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.FORNECEDORES, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }
}
