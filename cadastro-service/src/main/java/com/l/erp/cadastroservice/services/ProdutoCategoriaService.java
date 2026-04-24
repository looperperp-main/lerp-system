package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.ProdutoCategoriaDTO;
import com.l.erp.cadastroservice.domain.ProdutoCategoria;
import com.l.erp.cadastroservice.repository.ProdutoCategoriaRepository;
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
public class ProdutoCategoriaService {
    private final Logger logger = LoggerFactory.getLogger(ProdutoCategoriaService.class);
    private final ProdutoCategoriaRepository repository;
    private final AuditProducerService auditProducer;

    public ProdutoCategoriaService(ProdutoCategoriaRepository repository, AuditProducerService auditProducer) {
        this.repository = repository;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<ProdutoCategoria> getAllProdCategorias(Long tenantId, Pageable pageable) {
        logger.info("Buscando todos as categorias de Produto do tenant {}", tenantId);
        return repository.findAllByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoCategoria> getAllProdCategoriasEnabled(Long tenantId, Pageable pageable) {
        logger.info("Buscando todos as categorias Ativas de Produto do tenant {}", tenantId);
        return repository.findAllByTenantIdAndAtivaIsTrue(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public ProdutoCategoria findById(UUID id, Long tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException(Constants.PROD_CAT_NOT_FOUND+" - id: " + id));
    }

    @Transactional
    public ProdutoCategoria save(ProdutoCategoriaDTO dto, Long tenantId, UUID userId) {
        logger.info("Salvando nova Categoria de Produto no tenant {}", tenantId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        // 1. Validação de Unicidade: Uma categoria de produto por tenant
        if (repository.existsByTenantIdAndNome(tenantId, dto.nome())) {
            sendAuditEvent(Constants.PROD_CAT_CREATION, userId, null, Constants.ERROR, "{ERROR: Categoria de Produto já existe}", correlationID);
            throw new BusinessException(Constants.PROD_CAT_ALREADY_EXISTS + " - pessoaId: " + dto.id(), HttpStatus.BAD_REQUEST);
        }



        // 3. Monta a entidade
        ProdutoCategoria prodCat = ProdutoCategoria.builder()
                .tenantId(tenantId)
                .nome(dto.nome())
                .descricao(dto.descricao())
                .ativa(dto.ativa())
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();

        ProdutoCategoria saved = repository.save(prodCat);

        sendAuditEvent(Constants.PROD_CAT_CREATION, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public ProdutoCategoria update(UUID id, ProdutoCategoriaDTO dto, Long tenantId, UUID userId) {
        logger.info("Atualizando Categoria de Produto {} no tenant {}", id, tenantId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        ProdutoCategoria prodCat = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PROD_CAT_UPDATE, userId, null, Constants.ERROR, "{ERROR: Categoria não encontrado}", correlationID);
                    return new BusinessException(Constants.PROD_CAT_NOT_FOUND+" - id: " + id,HttpStatus.BAD_REQUEST);
                });

        // Atualiza campos
        prodCat.setNome(dto.nome());
        prodCat.setDescricao(dto.descricao());
        prodCat.setAtiva(dto.ativa() != null ? dto.ativa() : prodCat.getAtiva());
        prodCat.setUpdatedAt(Instant.now());
        prodCat.setLastUpdatedBy(userId);

        ProdutoCategoria updated = repository.save(prodCat);

        sendAuditEvent(Constants.PROD_CAT_UPDATE, userId, updated.getId(), Constants.SUCCESS, null, correlationID);
        return updated;
    }

    @Transactional
    public void updateStatus(UUID id, Long tenantId, UUID userId) {
        logger.info("Alterando status da categoria do Produto {} no tenant {}", id, tenantId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        ProdutoCategoria produtoCategoria = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PROD_CAT_UPDATE, userId, null, Constants.ERROR, "{ERROR: Categoria de Produto não encontrado}", correlationID);
                    return new BusinessException(Constants.PROD_CAT_NOT_FOUND + " - id: " + id, HttpStatus.BAD_REQUEST);
                });

        produtoCategoria.setAtiva(!produtoCategoria.getAtiva());
        produtoCategoria.setUpdatedAt(Instant.now());
        produtoCategoria.setLastUpdatedBy(userId);

        repository.save(produtoCategoria);

        sendAuditEvent(Constants.PROD_CAT_UPDATE, userId, id, Constants.SUCCESS, "{\"status_alterado\": " + produtoCategoria.getAtiva() + "}", correlationID);
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.PROD_CAT, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }

}
