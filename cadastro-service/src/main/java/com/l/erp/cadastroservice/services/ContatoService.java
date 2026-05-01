package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.ContatoRequestDTO;
import com.l.erp.cadastroservice.domain.Contato;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.repository.ContatoRepository;
import com.l.erp.common.util.Constants;
import com.l.erp.cadastroservice.util.Utils;
import com.l.erp.common.exception.custom.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class ContatoService {
    private final Logger logger = LoggerFactory.getLogger(ContatoService.class);
    private final ContatoRepository contatoRepository;
    private final PessoaService pessoaService;
    private final Utils utils;

    public ContatoService(ContatoRepository contatoRepository, PessoaService pessoaService, Utils utils) {
        this.contatoRepository = contatoRepository;
        this.pessoaService = pessoaService;
        this.utils = utils;
    }

    @Transactional(readOnly = true)
    public List<Contato> findAllByPessoa(UUID pessoaId, Long tenantId) {
        logger.debug("Buscando contatos da pessoa {} para o tenant {}", pessoaId, tenantId);
        return contatoRepository.findAllByPessoaIdAndTenantId(pessoaId, tenantId);
    }

    @Transactional(readOnly = true)
    public Contato findById(UUID id, UUID pessoaId, Long tenantId) {
        return contatoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.CONTATO_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Contato create(UUID pessoaId, ContatoRequestDTO dto, Long tenantId, UUID userId) {
        logger.debug("Criando contato para a pessoa {}", pessoaId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        Pessoa pessoa = pessoaService.findByIdAndTenant(pessoaId, tenantId);

        if (pessoa == null) {
            utils.sendAuditEvent(
                    Constants.CONTATO_CREATION,
                    userId,
                    Constants.CONTATO,
                    pessoaId,
                    Constants.PESSOA_NOT_FOUND,
                    "{ERROR: Pessoa nao encontrada para o Tenant "+tenantId+" }",
                    correlationId
            );
            throw new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Contato entity = new Contato();
        entity.setPessoa(pessoa);
        entity.setTenantId(tenantId);
        setContatoData(dto, entity);

        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(userId);

        Contato saved = contatoRepository.save(entity);
        utils.sendAuditEvent(
                Constants.CONTATO_CREATION,
                userId,
                Constants.CONTATO,
                saved.getId(),
                Constants.SUCCESS,
                "",
                correlationId
        );
        return saved;
    }

    @Transactional
    public Contato update(UUID id, UUID pessoaId, ContatoRequestDTO dto, Long tenantId, UUID userId) {
        logger.debug("Atualizando contato {} da pessoa {} para o tenant {}", id, pessoaId, tenantId);

        Contato entity = contatoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, tenantId)
                .orElseThrow(() -> new BusinessException("Contato não encontrado", HttpStatus.NOT_FOUND));

        setContatoData(dto, entity);

        entity.setUpdatedAt(Instant.now());
        entity.setLastUpdatedBy(userId);

        return contatoRepository.save(entity);
    }

    private void setContatoData(ContatoRequestDTO dto, Contato entity) {
        entity.setNome(dto.nome());
        entity.setTipo(dto.tipo());
        entity.setCargo(dto.cargo());
        entity.setEmail(dto.email());
        entity.setTelefone(dto.telefone());
        entity.setPrincipal(dto.principal());
        entity.setAtivo(dto.ativo());
    }
}
