package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.EstabelecimentoRequestDTO;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.cadastroservice.repository.EstabelecimentoRepository;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.util.Utils;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD de filiais + gestão da matriz (criada/atualizada a partir de PessoaService,
 * nunca via EstabelecimentoController — ver §4.1/§5.1 spec/estabelecimentos-filiais.md).
 * Depende de PessoaRepository (não PessoaService) para evitar ciclo de DI, já que
 * PessoaService também depende deste service para gerir a matriz.
 */
@Service
public class EstabelecimentoService {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final PessoaRepository pessoaRepository;
    private final Utils utils;

    public EstabelecimentoService(EstabelecimentoRepository estabelecimentoRepository, PessoaRepository pessoaRepository, Utils utils) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.pessoaRepository = pessoaRepository;
        this.utils = utils;
    }

    public List<Estabelecimento> findAllByPessoa(UUID pessoaId, Long tenantId) {
        return estabelecimentoRepository.findAllByPessoaIdAndTenantId(pessoaId, tenantId);
    }

    public Estabelecimento findById(UUID id, UUID pessoaId, Long tenantId) {
        return estabelecimentoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.ESTABELECIMENTO_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** Busca a matriz de uma pessoa PJ; lança se não existir (uso interno de Endereco/ContatoService). */
    public Estabelecimento buscarMatrizPorPessoa(UUID pessoaId, Long tenantId) {
        return estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.ESTABELECIMENTO_MATRIZ_NAO_ENCONTRADA, HttpStatus.NOT_FOUND));
    }

    /**
     * Estabelecimento próprio do tenant (emitente) — usado pelo operacoes-service pra achar a UF
     * de origem no faturamento (Fase 6, spec/estabelecimentos-filiais.md §6.1).
     */
    public Estabelecimento buscarProprio(Long tenantId) {
        return estabelecimentoRepository.findByTenantIdAndProprioTrue(tenantId)
                .orElseThrow(() -> new BusinessException(Constants.ESTABELECIMENTO_PROPRIO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
    }

    /** Variante opcional (uso interno de PessoaService.findByIdAndTenant, que não deve 404). */
    public Optional<Estabelecimento> buscarMatrizOpcionalPorPessoa(UUID pessoaId, Long tenantId) {
        return estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, tenantId);
    }

    /** Batch p/ popular ie/im transiente de uma página de pessoas PJ (PessoaService.findAllByTenant). */
    public List<Estabelecimento> buscarMatrizesPorPessoas(List<UUID> pessoaIds, Long tenantId) {
        return estabelecimentoRepository.findAllByPessoaIdInAndMatrizTrueAndTenantId(pessoaIds, tenantId);
    }

    @Transactional
    public Estabelecimento create(UUID pessoaId, EstabelecimentoRequestDTO dto, Long tenantId, UUID userId) {
        Pessoa pessoa = pessoaRepository.findByIdAndTenantId(pessoaId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (pessoa.getTipo() != TipoPessoa.PJ) {
            throw new BusinessException(Constants.ESTABELECIMENTO_APENAS_PJ, HttpStatus.BAD_REQUEST);
        }

        Estabelecimento entity = new Estabelecimento();
        entity.setTenantId(tenantId);
        entity.setPessoa(pessoa);
        entity.setCnpjCompleto(dto.cnpjCompleto());
        entity.setOrdem(proximaOrdem(pessoaId, tenantId));
        entity.setMatriz(false);
        entity.setProprio(false);
        entity.setIe(dto.ie());
        entity.setIm(dto.im());
        entity.setAtivo(dto.ativo() != null ? dto.ativo() : Boolean.TRUE);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(userId);

        Estabelecimento salvo = estabelecimentoRepository.save(entity);
        utils.sendAuditEvent(Constants.ESTABELECIMENTO_CREATION, userId, Constants.ESTABELECIMENTO, salvo.getId(), Constants.SUCCESS, null, null);
        return salvo;
    }

    @Transactional
    public Estabelecimento update(UUID id, UUID pessoaId, EstabelecimentoRequestDTO dto, Long tenantId, UUID userId) {
        Estabelecimento entity = findById(id, pessoaId, tenantId);

        if (Boolean.TRUE.equals(entity.getMatriz()) && Boolean.FALSE.equals(dto.ativo())) {
            throw new BusinessException(Constants.ESTABELECIMENTO_MATRIZ_NAO_PODE_SER_INATIVADA, HttpStatus.BAD_REQUEST);
        }

        entity.setCnpjCompleto(dto.cnpjCompleto());
        entity.setIe(dto.ie());
        entity.setIm(dto.im());
        if (dto.ativo() != null) {
            entity.setAtivo(dto.ativo());
        }
        entity.setUpdatedAt(Instant.now());
        entity.setLastUpdatedBy(userId);

        Estabelecimento salvo = estabelecimentoRepository.save(entity);
        utils.sendAuditEvent(Constants.ESTABELECIMENTO_UPDATE, userId, Constants.ESTABELECIMENTO, salvo.getId(), Constants.SUCCESS, null, null);
        return salvo;
    }

    /** Cria a matriz (ordem 0001) de uma Pessoa PJ recém-criada — chamado por PessoaService.create. */
    @Transactional
    public Estabelecimento criarMatriz(Pessoa pessoa, String ie, String im, UUID userId) {
        Estabelecimento matriz = new Estabelecimento();
        matriz.setTenantId(pessoa.getTenantId());
        matriz.setPessoa(pessoa);
        matriz.setCnpjCompleto(pessoa.getDocumento());
        matriz.setOrdem("0001");
        matriz.setMatriz(true);
        matriz.setProprio(false);
        matriz.setIe(ie);
        matriz.setIm(im);
        matriz.setAtivo(Boolean.TRUE);
        matriz.setCreatedAt(Instant.now());
        matriz.setCreatedBy(userId);
        return estabelecimentoRepository.save(matriz);
    }

    /**
     * Marca a matriz de uma Pessoa como o estabelecimento "próprio" do tenant (onboarding,
     * spec/estabelecimentos-filiais.md §6) — idempotente: rejeita se o tenant já tem um
     * proprio=true (índice único parcial uq_estab_proprio_matriz reforça isso no banco).
     */
    @Transactional
    public Estabelecimento marcarProprio(UUID pessoaId, Long tenantId, UUID userId) {
        if (estabelecimentoRepository.existsByTenantIdAndProprioTrue(tenantId)) {
            throw new BusinessException(Constants.ESTABELECIMENTO_PROPRIO_JA_DEFINIDO, HttpStatus.CONFLICT);
        }
        Estabelecimento matriz = buscarMatrizPorPessoa(pessoaId, tenantId);
        matriz.setProprio(true);
        matriz.setUpdatedAt(Instant.now());
        matriz.setLastUpdatedBy(userId);
        Estabelecimento salvo = estabelecimentoRepository.save(matriz);
        utils.sendAuditEvent(Constants.ESTABELECIMENTO_UPDATE, userId, Constants.ESTABELECIMENTO, salvo.getId(), Constants.SUCCESS, null, null);
        return salvo;
    }

    /** Atualiza ie/im da matriz de uma Pessoa PJ — chamado por PessoaService.update. */
    @Transactional
    public void atualizarIeImMatriz(UUID pessoaId, Long tenantId, String ie, String im, UUID userId) {
        Estabelecimento matriz = buscarMatrizPorPessoa(pessoaId, tenantId);
        matriz.setIe(ie);
        matriz.setIm(im);
        matriz.setUpdatedAt(Instant.now());
        matriz.setLastUpdatedBy(userId);
        estabelecimentoRepository.save(matriz);
    }

    private String proximaOrdem(UUID pessoaId, Long tenantId) {
        int count = estabelecimentoRepository.findAllByPessoaIdAndTenantId(pessoaId, tenantId).size();
        return String.format("%04d", count + 1);
    }
}
