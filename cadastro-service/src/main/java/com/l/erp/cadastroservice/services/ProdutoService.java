package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.ProdutoDTO;
import com.l.erp.cadastroservice.api.mappers.ProdutoMapper;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.ProdutoEstoqueConfig;
import com.l.erp.cadastroservice.domain.ProdutoFornecedor;
import com.l.erp.cadastroservice.domain.ProdutoPreco;
import com.l.erp.cadastroservice.repository.DepositoRepository;
import com.l.erp.cadastroservice.repository.FornecedorRepository;
import com.l.erp.cadastroservice.repository.ProdutoCategoriaRepository;
import com.l.erp.cadastroservice.repository.ProdutoRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
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
import java.util.stream.Collectors;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class ProdutoService {

    private final Logger logger = LoggerFactory.getLogger(ProdutoService.class);

    private final ProdutoRepository produtoRepository;
    private final ProdutoCategoriaRepository categoriaRepository;
    private final FornecedorRepository fornecedorRepository;
    private final TabelaPrecoRepository tabelaPrecoRepository;
    private final ProdutoMapper mapper;
    private final DepositoRepository depositoRepository;
    private final AuditProducerService auditProducer;

    public ProdutoService(ProdutoRepository produtoRepository,
                          ProdutoCategoriaRepository categoriaRepository,
                          FornecedorRepository fornecedorRepository,
                          TabelaPrecoRepository tabelaPrecoRepository,
                          ProdutoMapper mapper, DepositoRepository depositoRepository, AuditProducerService auditProducer) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.tabelaPrecoRepository = tabelaPrecoRepository;
        this.mapper = mapper;
        this.depositoRepository = depositoRepository;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<Produto> findAll(Long tenantId, Pageable pageable) {
        return produtoRepository.findAllByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Produto findById(UUID id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Constants.PRODUTO_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Produto create(Long tenantId, UUID userId, ProdutoDTO dto) {
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Produto produto = mapper.toEntity(dto);
        produto.setTenantId(tenantId);
        produto.setCreatedAt(Instant.now());
        produto.setCreatedBy(userId);

        if (dto.categoriaId() != null) {
            produto.setCategoria(categoriaRepository.findById(dto.categoriaId()).orElse(null));
        }

        // No Hibernate, antes de popularmos coleções filhas em Cascade onde o pai tem UUID manual (ou Identity),
        // costuma ser seguro salvar o Pai primeiro, garantindo a existência dele, e depois vincular as filhas.
        Produto savedProduto = produtoRepository.save(produto);

        processarSubEntidades(savedProduto, dto, tenantId, userId, true);

        // Depois de adicionar às coleções, a gente dá um save() final no agregado.
        Produto finalSaved = produtoRepository.save(savedProduto);

        sendAuditEvent(Constants.PRODUTO_CREATION, userId, finalSaved.getId(), Constants.SUCCESS, null, correlationID);
        return finalSaved;
    }

    @Transactional
    public Produto update(UUID id, UUID userId, ProdutoDTO dto, Long tenantId) {
        UUID correlationID = getCorrelationIdFromRequest(logger);
        Produto produto = produtoRepository.findById(id).orElseThrow(() -> new BusinessException(Constants.PRODUTO_NOT_FOUND, HttpStatus.NOT_FOUND));

        mapper.updateEntityFromDto(dto, produto);

        produto.setUpdatedAt(Instant.now());
        produto.setLastUpdatedBy(userId);

        if (dto.categoriaId() != null) {
            produto.setCategoria(categoriaRepository.findById(dto.categoriaId()).orElse(null));
        } else {
            produto.setCategoria(null);
        }

        // Limpa as listas atuais e engatilha a deleção no banco IMEDIATAMENTE
        if(produto.getProdutoPrecos() != null) produto.getProdutoPrecos().clear();
        if(produto.getProdutoFornecedors() != null) produto.getProdutoFornecedors().clear();
        if(produto.getProdutoEstoqueConfigs() != null) produto.getProdutoEstoqueConfigs().clear();

        produtoRepository.saveAndFlush(produto);

        processarSubEntidades(produto, dto, tenantId, userId, false);

        Produto saved = produtoRepository.save(produto);
        sendAuditEvent(Constants.PRODUTO_UPDATE, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        UUID correlationID = getCorrelationIdFromRequest(logger);
        produtoRepository.deleteById(id);
        sendAuditEvent(Constants.PRODUTO_DELETE, userId, id, Constants.SUCCESS, null, correlationID);
    }

    private void processarSubEntidades(Produto produto, ProdutoDTO dto, Long tenantId, UUID userId, boolean isCreate) {
        processProducts(produto, dto, tenantId, userId, isCreate);

        processSuppliers(produto, dto, tenantId, userId, isCreate);

        processEstoque(produto, dto, tenantId, userId, isCreate);
    }

    private void processEstoque(Produto produto, ProdutoDTO dto, Long tenantId, UUID userId, boolean isCreate) {
        if (dto.estoqueConfigs() != null) {
            produto.getProdutoEstoqueConfigs().addAll(dto.estoqueConfigs().stream().map(configDto -> {
                ProdutoEstoqueConfig config = new ProdutoEstoqueConfig();
                config.setTenantId(tenantId);
                config.setProduto(produto);
                // VINVULA O DEPÓSITO (Obrigatório)
                if(configDto.depositoId() != null) {
                    config.setDeposito(depositoRepository.findById(configDto.depositoId()).orElseThrow(() -> new BusinessException("Depósito não encontrado", HttpStatus.BAD_REQUEST)));
                } else {
                    throw new BusinessException("Depósito é obrigatório na configuração de estoque", HttpStatus.BAD_REQUEST);
                }
                // Precedência: fornecedorPreferencial explícito no config > flag preferencial no ProdutoFornecedor
                if (configDto.fornecedorPreferencialId() != null) {
                    config.setFornecedorPreferencial(produto.getProdutoFornecedors().stream()
                            .filter(f -> f.getFornecedor() != null && f.getFornecedor().getId().equals(configDto.fornecedorPreferencialId()))
                            .findFirst().orElse(null));
                } else {
                    config.setFornecedorPreferencial(produto.getProdutoFornecedors().stream()
                            .filter(f -> Boolean.TRUE.equals(f.getPreferencial()))
                            .findFirst().orElse(null));
                }
                config.setEstoqueMinimo(configDto.estoqueMinimo());
                config.setEstoqueMaximo(configDto.estoqueMaximo());
                config.setPontoReposicao(configDto.pontoReposicao());
                config.setLeadTimeDias(configDto.leadTimeDias());
                if(isCreate){
                    config.setCreatedAt(Instant.now());
                    config.setCreatedBy(userId);
                }else{
                    config.setCreatedAt(configDto.createdAt());
                    config.setCreatedBy(configDto.createdBy());
                    config.setUpdatedAt(Instant.now());
                    config.setLastUpdatedBy(userId);
                }
                return config;
            }).collect(Collectors.toSet()));
        }
    }

    private void processSuppliers(Produto produto, ProdutoDTO dto, Long tenantId, UUID userId, boolean isCreate) {
        if (dto.fornecedores() != null) {
            produto.getProdutoFornecedors().addAll(dto.fornecedores().stream().map(fornDto -> {
                ProdutoFornecedor fornecedor = new ProdutoFornecedor();
                fornecedor.setTenantId(tenantId);
                fornecedor.setProduto(produto);
                fornecedor.setFornecedor(fornecedorRepository.findById(fornDto.fornecedorId()).orElse(null));
                fornecedor.setCodigoProdutoFornecedor(fornDto.codigoProdutoFornecedor());
                fornecedor.setPrecoCusto(fornDto.precoCusto());
                fornecedor.setLeadTimeDias(fornDto.leadTimeDias());
                fornecedor.setPreferencial(fornDto.preferencial());
                fornecedor.setAtivo(fornDto.ativo());
                fornecedor.setCreatedAt(isCreate ? Instant.now() : fornDto.createdAt());
                fornecedor.setCreatedBy(isCreate ? userId : fornDto.createdBy());
                if (!isCreate) {
                    fornecedor.setUpdatedAt(Instant.now());
                    fornecedor.setLastUpdatedBy(userId);
                }
                return fornecedor;
            }).collect(Collectors.toSet()));
        }
    }

    private void processProducts(Produto produto, ProdutoDTO dto, Long tenantId, UUID userId, boolean isCreate) {
        if (dto.precos() != null) {
            produto.getProdutoPrecos().addAll(dto.precos().stream().map(precoDto -> {
                ProdutoPreco preco = new ProdutoPreco();
                preco.setTenantId(tenantId);
                preco.setProduto(produto);
                preco.setTabelaPreco(tabelaPrecoRepository.findById(precoDto.tabelaPrecoId()).orElse(null));
                preco.setPreco(precoDto.preco());
                preco.setInicioVigencia(precoDto.inicioVigencia());
                preco.setFimVigencia(precoDto.fimVigencia());
                preco.setCreatedAt(isCreate ? Instant.now() : precoDto.createdAt());
                preco.setCreatedBy(isCreate ? userId : precoDto.createdBy());
                if (!isCreate) {
                    preco.setUpdatedAt(Instant.now());
                    preco.setLastUpdatedBy(userId);
                }
                return preco;
            }).collect(Collectors.toSet()));
        }
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.PRODUTO, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }
}
