package com.l.erp.operacoesservice.services.estoque;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnica;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnicaItem;
import com.l.erp.operacoesservice.domain.estoque.OrdemProducao;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.StatusOrdemProducao;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import com.l.erp.operacoesservice.repository.estoque.EstoqueSaldoRepository;
import com.l.erp.operacoesservice.repository.estoque.FichaTecnicaItemRepository;
import com.l.erp.operacoesservice.repository.estoque.FichaTecnicaRepository;
import com.l.erp.operacoesservice.repository.estoque.OrdemProducaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Produção própria (spec/modulos/estoque/estoque.md §12, D11, Fase 2): ficha técnica + ordem de produção.
 * Apontar produção gera N {@code SAIDA_PRODUCAO} (um por componente da ficha técnica ativa) + 1
 * {@code ENTRADA_PRODUCAO}, sempre via {@link EstoqueService#registrarMovimento} — nunca duplica a lógica
 * de saldo/pendência/custo médio, que continua concentrada lá.
 */
@Service
public class ProducaoService {

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final OrdemProducaoRepository ordemProducaoRepository;
    private final EstoqueSaldoRepository estoqueSaldoRepository;
    private final EstoqueService estoqueService;

    public ProducaoService(FichaTecnicaRepository fichaTecnicaRepository,
                            FichaTecnicaItemRepository fichaTecnicaItemRepository,
                            OrdemProducaoRepository ordemProducaoRepository,
                            EstoqueSaldoRepository estoqueSaldoRepository,
                            EstoqueService estoqueService) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.fichaTecnicaItemRepository = fichaTecnicaItemRepository;
        this.ordemProducaoRepository = ordemProducaoRepository;
        this.estoqueSaldoRepository = estoqueSaldoRepository;
        this.estoqueService = estoqueService;
    }

    @Transactional
    public FichaTecnica criarFichaTecnica(Long tenantId, UUID userId, UUID produtoAcabadoId,
                                           List<ItemFicha> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new BusinessException(Constants.PRODUCAO_FICHA_TECNICA_SEM_ITENS, HttpStatus.BAD_REQUEST);
        }
        if (itens.stream().anyMatch(i -> i.produtoComponenteId().equals(produtoAcabadoId))) {
            throw new BusinessException(Constants.PRODUCAO_FICHA_TECNICA_PRODUTO_PROPRIO_COMPONENTE, HttpStatus.BAD_REQUEST);
        }
        fichaTecnicaRepository.findByTenantIdAndProdutoAcabadoIdAndAtivoTrue(tenantId, produtoAcabadoId)
                .ifPresent(anterior -> {
                    anterior.setAtivo(false);
                    anterior.setUpdatedAt(Instant.now());
                    anterior.setLastUpdatedBy(userId);
                    fichaTecnicaRepository.save(anterior);
                });

        Instant agora = Instant.now();
        FichaTecnica nova = FichaTecnica.builder()
                .produtoAcabadoId(produtoAcabadoId)
                .ativo(true)
                .createdAt(agora)
                .createdBy(userId)
                .build();
        nova.setTenantId(tenantId);
        nova = fichaTecnicaRepository.save(nova);

        for (ItemFicha item : itens) {
            FichaTecnicaItem entidade = FichaTecnicaItem.builder()
                    .fichaTecnicaId(nova.getId())
                    .produtoComponenteId(item.produtoComponenteId())
                    .quantidade(item.quantidade())
                    .build();
            entidade.setTenantId(tenantId);
            fichaTecnicaItemRepository.save(entidade);
        }
        return nova;
    }

    public Page<FichaTecnica> buscarFichasTecnicas(Long tenantId, Pageable pageable) {
        return fichaTecnicaRepository.findByTenantId(tenantId, pageable);
    }

    public List<FichaTecnicaItem> buscarItens(UUID fichaTecnicaId) {
        return fichaTecnicaItemRepository.findByFichaTecnicaId(fichaTecnicaId);
    }

    @Transactional
    public OrdemProducao criarOrdemProducao(Long tenantId, UUID userId, UUID produtoAcabadoId,
                                             BigDecimal quantidadePlanejada, UUID depositoId) {
        fichaTecnicaAtivaOu400(tenantId, produtoAcabadoId);
        Instant agora = Instant.now();
        OrdemProducao ordem = OrdemProducao.builder()
                .produtoAcabadoId(produtoAcabadoId)
                .depositoId(depositoId)
                .quantidadePlanejada(quantidadePlanejada)
                .status(StatusOrdemProducao.ABERTA)
                .createdAt(agora)
                .createdBy(userId)
                .build();
        ordem.setTenantId(tenantId);
        return ordemProducaoRepository.save(ordem);
    }

    public Page<OrdemProducao> buscarOrdens(Long tenantId, Pageable pageable) {
        return ordemProducaoRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * Apontamento de produção: consome os componentes da ficha técnica ativa na proporção da
     * quantidade produzida (N {@code SAIDA_PRODUCAO}) e dá entrada no produto acabado (1
     * {@code ENTRADA_PRODUCAO}) ao custo médio ponderado dos componentes consumidos.
     */
    @Transactional
    public void apontarProducao(Long tenantId, UUID userId, UUID ordemId, BigDecimal quantidadeProduzida) {
        if (quantidadeProduzida == null || quantidadeProduzida.signum() <= 0) {
            throw new BusinessException(Constants.PRODUCAO_QUANTIDADE_PRODUZIDA_INVALIDA, HttpStatus.BAD_REQUEST);
        }
        OrdemProducao ordem = ordemProducaoRepository.findByIdAndTenantId(ordemId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PRODUCAO_ORDEM_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (ordem.getStatus() != StatusOrdemProducao.ABERTA) {
            throw new BusinessException(Constants.PRODUCAO_ORDEM_STATUS_INVALIDO, HttpStatus.CONFLICT);
        }
        FichaTecnica ficha = fichaTecnicaAtivaOu400(tenantId, ordem.getProdutoAcabadoId());
        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByFichaTecnicaId(ficha.getId());

        Instant agora = Instant.now();
        BigDecimal custoTotalComponentes = BigDecimal.ZERO;
        List<EstoqueService.MovimentoRequisicao.Linha> linhasSaida = new ArrayList<>();
        for (FichaTecnicaItem item : itens) {
            BigDecimal quantidadeConsumida = item.getQuantidade().multiply(quantidadeProduzida);
            BigDecimal custoMedio = estoqueSaldoRepository
                    .findByTenantIdAndProdutoIdAndDepositoId(tenantId, item.getProdutoComponenteId(), ordem.getDepositoId())
                    .map(EstoqueSaldo::getCustoMedio)
                    .orElse(BigDecimal.ZERO);
            custoTotalComponentes = custoTotalComponentes.add(quantidadeConsumida.multiply(custoMedio));
            linhasSaida.add(new EstoqueService.MovimentoRequisicao.Linha(item.getProdutoComponenteId(), quantidadeConsumida, null));
        }

        estoqueService.registrarMovimento(new EstoqueService.MovimentoRequisicao(tenantId, userId,
                TipoMovimentoEstoque.SAIDA_PRODUCAO, OrigemMovimentoEstoque.PRODUCAO, ordemId, ordem.getDepositoId(),
                agora, null, null, null, null, linhasSaida));

        BigDecimal custoUnitario = custoTotalComponentes.divide(quantidadeProduzida, 4, RoundingMode.HALF_UP);
        estoqueService.registrarMovimento(new EstoqueService.MovimentoRequisicao(tenantId, userId,
                TipoMovimentoEstoque.ENTRADA_PRODUCAO, OrigemMovimentoEstoque.PRODUCAO, ordemId, ordem.getDepositoId(),
                agora, null, null, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(ordem.getProdutoAcabadoId(), quantidadeProduzida, custoUnitario))));

        ordem.setStatus(StatusOrdemProducao.CONCLUIDA);
        ordem.setConcluidaEm(agora);
        ordem.setUpdatedAt(agora);
        ordem.setLastUpdatedBy(userId);
        ordemProducaoRepository.save(ordem);
    }

    private FichaTecnica fichaTecnicaAtivaOu400(Long tenantId, UUID produtoAcabadoId) {
        return fichaTecnicaRepository.findByTenantIdAndProdutoAcabadoIdAndAtivoTrue(tenantId, produtoAcabadoId)
                .orElseThrow(() -> new BusinessException(Constants.PRODUCAO_FICHA_TECNICA_INEXISTENTE_PARA_PRODUTO, HttpStatus.BAD_REQUEST));
    }

    public record ItemFicha(UUID produtoComponenteId, BigDecimal quantidade) {
    }
}
