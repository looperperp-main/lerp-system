package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.PessoaRequestDTO;
import com.l.erp.cadastroservice.api.mappers.PessoaMapper;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.cadastroservice.repository.PessoaRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class PessoaService {

    private final Logger logger = LoggerFactory.getLogger(PessoaService.class);
    private final PessoaMapper pessoaMapper;
    private final PessoaRepository pessoaRepository;
    private final AuditProducerService auditService;
    private final EstabelecimentoService estabelecimentoService;

    public PessoaService(PessoaMapper pessoaMapper, PessoaRepository pessoaRepository, AuditProducerService auditService, EstabelecimentoService estabelecimentoService) {
        this.pessoaMapper = pessoaMapper;
        this.pessoaRepository = pessoaRepository;
        this.auditService = auditService;
        this.estabelecimentoService = estabelecimentoService;
    }

    @Transactional(readOnly = true)
    public Page<Pessoa> findAllByTenant(Long tenantId, Pageable pageable) {
        logger.debug("Buscando pessoas para o tenant {}", tenantId);
        Page<Pessoa> page = pessoaRepository.findAllByTenantId(tenantId, pageable);
        popularIeImMatrizes(page.getContent(), tenantId);
        return page;
    }

    @Transactional(readOnly = true)
    public Pessoa findByIdAndTenant(UUID id, Long tenantId) {
        logger.debug("Buscando pessoa {} para tenant {}", id, tenantId);
        Pessoa pessoa = pessoaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (pessoa.getTipo() == TipoPessoa.PJ) {
            estabelecimentoService.buscarMatrizOpcionalPorPessoa(id, tenantId)
                    .ifPresent(matriz -> {
                        pessoa.setIe(matriz.getIe());
                        pessoa.setIm(matriz.getIm());
                    });
        }
        return pessoa;
    }

    @Transactional
    public Pessoa create(PessoaRequestDTO dto, Long tenantId, UUID userId) {
        logger.debug("Criando pessoa para o tenant {}", tenantId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        validateDocumento(dto.tipo(), dto.documento());

        boolean isPj = dto.tipo() == TipoPessoa.PJ;
        String cnpjRaiz = isPj ? extrairCnpjRaiz(dto.documento()) : null;
        boolean jaExiste = isPj
                ? pessoaRepository.existsByCnpjRaizAndTenantId(cnpjRaiz, tenantId)
                : pessoaRepository.existsByDocumentoAndTenantId(dto.documento(), tenantId);

        if (jaExiste) {
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
        entity.setCnpjRaiz(cnpjRaiz);

        Pessoa saved = pessoaRepository.save(entity);

        if (isPj) {
            estabelecimentoService.criarMatriz(saved, dto.ie(), dto.im(), userId);
            saved.setIe(dto.ie());
            saved.setIm(dto.im());
        }

        sendAuditEvent(Constants.PESSOA_CREATION, userId, saved.getId(), Constants.SUCCESS, null, correlationId);

        return saved;
    }

    public Pessoa update(UUID id, @Valid PessoaRequestDTO dto, Long tenantID, UUID userId) {
        logger.debug("Updating Pessoa: {}", id);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        validateDocumento(dto.tipo(), dto.documento());

        Pessoa oldPessoa = pessoaRepository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PESSOA_UPDATE, userId, id, Constants.ERROR, "{Error: Pessoa não encontrada}", correlationId);
                    return new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND);
                });

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

        boolean isPj = dto.tipo() == TipoPessoa.PJ;

        Pessoa pessoa = pessoaMapper.toEntityRequest(dto);
        pessoa.setId(id);
        pessoa.setCreatedBy(oldPessoa.getCreatedBy());
        pessoa.setCreatedAt(oldPessoa.getCreatedAt());
        pessoa.setUpdatedAt(Instant.now());
        pessoa.setLastUpdatedBy(userId);
        pessoa.setTenantId(tenantID);
        pessoa.setCnpjRaiz(isPj ? extrairCnpjRaiz(dto.documento()) : null);

        Pessoa saved = pessoaRepository.save(pessoa);

        if (isPj) {
            estabelecimentoService.atualizarIeImMatriz(id, tenantID, dto.ie(), dto.im(), userId);
            saved.setIe(dto.ie());
            saved.setIm(dto.im());
        }

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

    @Transactional
    public void updateStatus(UUID id, Long tenantId, UUID userId){
        UUID correlationId = getCorrelationIdFromRequest(logger);
        logger.info("Atualizando status da Pessoa ID: {}", id);
        Pessoa pessoa = pessoaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PESSOA_UPDATE, userId, id, Constants.ERROR, "{Error: Pessoa não encontrada}", correlationId);
                    return new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND);
                });
        pessoa.setAtivo(!pessoa.getAtivo());
        pessoa.setLastUpdatedBy(userId);
        pessoa.setUpdatedAt(Instant.now());
        pessoaRepository.save(pessoa);
        sendAuditEvent(Constants.PESSOA_UPDATE, userId, id, Constants.SUCCESS, "{Status Alterado: " + pessoa.getAtivo() + "}", correlationId);
    }

    private void validateDocumento(TipoPessoa tipo, String documento) {
        String digits = documento.replaceAll("[^0-9]", "");
        if (tipo == TipoPessoa.PF && digits.length() != 11) {
            throw new BusinessException("CPF inválido: deve conter 11 dígitos", HttpStatus.BAD_REQUEST);
        }
        if (tipo == TipoPessoa.PJ && digits.length() != 14) {
            throw new BusinessException("CNPJ inválido: deve conter 14 dígitos", HttpStatus.BAD_REQUEST);
        }
    }

    /** 8 primeiros chars do documento sem máscara, maiúsculo (CNPJ alfanumérico NT 2026.004 ready) — usado só para PJ. */
    private String extrairCnpjRaiz(String documento) {
        String semMascara = documento.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        return semMascara.length() > 8 ? semMascara.substring(0, 8) : semMascara;
    }

    private void popularIeImMatrizes(List<Pessoa> pessoas, Long tenantId) {
        List<UUID> pjIds = pessoas.stream()
                .filter(p -> p.getTipo() == TipoPessoa.PJ)
                .map(Pessoa::getId)
                .toList();
        if (pjIds.isEmpty()) {
            return;
        }
        Map<UUID, Estabelecimento> matrizPorPessoa = estabelecimentoService.buscarMatrizesPorPessoas(pjIds, tenantId).stream()
                .collect(Collectors.toMap(e -> e.getPessoa().getId(), Function.identity()));
        pessoas.forEach(p -> {
            Estabelecimento matriz = matrizPorPessoa.get(p.getId());
            if (matriz != null) {
                p.setIe(matriz.getIe());
                p.setIm(matriz.getIm());
            }
        });
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.PESSOA, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditService.sendAuditEvent(auditEvent);
    }
}
