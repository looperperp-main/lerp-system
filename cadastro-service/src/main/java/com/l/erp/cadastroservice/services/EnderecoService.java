package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.EnderecoRequestDTO;
import com.l.erp.cadastroservice.domain.Endereco;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.repository.EnderecoRepository;
import com.l.erp.cadastroservice.util.Constants;
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
public class EnderecoService {
    private final Logger logger = LoggerFactory.getLogger(EnderecoService.class);
    private final EnderecoRepository enderecoRepository;
    private final PessoaService pessoaService;
    private final Utils utils;

    public EnderecoService(EnderecoRepository enderecoRepository, PessoaService pessoaService, Utils utils) {
        this.enderecoRepository = enderecoRepository;
        this.pessoaService = pessoaService;
        this.utils = utils;
    }

    @Transactional(readOnly = true)
    public List<Endereco> findAllByPessoa(UUID pessoaId, Long tenantId) {
        logger.debug("Buscando endereços da pessoa {} para o tenant {}", pessoaId, tenantId);
        return enderecoRepository.findAllByPessoaIdAndTenantId(pessoaId, tenantId);
    }

    @Transactional(readOnly = true)
    public Endereco findById(UUID id, UUID pessoaId, Long tenantId) {
        return enderecoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, tenantId)
                .orElseThrow(() -> new BusinessException("Endereço não encontrado", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Endereco create(UUID pessoaId, EnderecoRequestDTO dto, Long tenantId, UUID userId) {
        logger.debug("Criando endereço para a pessoa {}", pessoaId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        // Verifica se a pessoa existe e pertence ao tenant
        Pessoa pessoa = pessoaService.findByIdAndTenant(pessoaId, tenantId);

        if (pessoa == null) {
            utils.sendAuditEvent(
                    Constants.END_CREATION,
                    userId,
                    Constants.END,
                    pessoaId,
                    Constants.PESSOA_NOT_FOUND,
                    "{ERROR: Pessoa nao encontrada para o Tenant "+tenantId+" }",
                    correlationId
            );
            throw new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Endereco entity = new Endereco();
        entity.setPessoa(pessoa);
        entity.setTenantId(tenantId);
        createEndereco(dto, entity);

        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(userId);

        Endereco saved = enderecoRepository.save(entity);
        utils.sendAuditEvent(
                Constants.END_CREATION,
                userId,
                Constants.END,
                saved.getId(),
                Constants.SUCCESS,
                "",
                correlationId
        );
        return saved;
    }


    @Transactional
    public Endereco update(UUID id, UUID pessoaId, EnderecoRequestDTO dto, Long tenantId, UUID userId) {
        logger.debug("Atualizando endereço {} da pessoa {} para o tenant {}", id, pessoaId, tenantId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        // Busca o endereço existente garantindo que pertence à pessoa e ao tenant
        Endereco entity = enderecoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.END_NOT_FOUND, HttpStatus.NOT_FOUND));

        createEndereco(dto, entity);

        entity.setUpdatedAt(Instant.now());
        entity.setLastUpdatedBy(userId);

        utils.sendAuditEvent(
                Constants.END_CREATION,
                userId,
                Constants.END,
                id,
                Constants.SUCCESS,
                "",
                correlationId
        );

        return enderecoRepository.save(entity);
    }

    private void createEndereco(EnderecoRequestDTO dto, Endereco entity) {
        entity.setTipo(dto.tipo());
        entity.setLogradouro(dto.logradouro());
        entity.setNumero(dto.numero());
        entity.setComplemento(dto.complemento());
        entity.setBairro(dto.bairro());
        entity.setCidade(dto.cidade());
        entity.setUf(dto.uf());
        entity.setCep(dto.cep());
        entity.setIbgeCodigo(dto.ibgeCodigo());
        entity.setPais(dto.pais() != null ? dto.pais() : "Brasil");
        entity.setPrincipal(dto.principal());
    }
}
