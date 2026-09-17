package com.l.erp.operacoesservice.services.estoque;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnica;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnicaItem;
import com.l.erp.operacoesservice.domain.estoque.OrdemProducao;
import com.l.erp.operacoesservice.domain.estoque.enumerators.StatusOrdemProducao;
import com.l.erp.operacoesservice.repository.estoque.EstoqueSaldoRepository;
import com.l.erp.operacoesservice.repository.estoque.FichaTecnicaItemRepository;
import com.l.erp.operacoesservice.repository.estoque.FichaTecnicaRepository;
import com.l.erp.operacoesservice.repository.estoque.OrdemProducaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProducaoServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUTO_ACABADO_ID = UUID.randomUUID();
    private static final UUID DEPOSITO_ID = UUID.randomUUID();

    @Mock
    private FichaTecnicaRepository fichaTecnicaRepository;
    @Mock
    private FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Mock
    private OrdemProducaoRepository ordemProducaoRepository;
    @Mock
    private EstoqueSaldoRepository estoqueSaldoRepository;
    @Mock
    private EstoqueService estoqueService;

    @InjectMocks
    private ProducaoService producaoService;

    @Test
    void criarFichaTecnicaComProdutoProprioComoComponente_lanca400() {
        List<ProducaoService.ItemFicha> itens = List.of(new ProducaoService.ItemFicha(PRODUTO_ACABADO_ID, BigDecimal.ONE));

        assertThatThrownBy(() -> producaoService.criarFichaTecnica(TENANT_ID, USER_ID, PRODUTO_ACABADO_ID, itens))
                .isInstanceOf(BusinessException.class);
        verify(fichaTecnicaRepository, never()).save(any());
    }

    @Test
    void criarFichaTecnicaDesativaAnteriorEGravaItens() {
        FichaTecnica anterior = FichaTecnica.builder().id(UUID.randomUUID()).ativo(true).build();
        when(fichaTecnicaRepository.findByTenantIdAndProdutoAcabadoIdAndAtivoTrue(TENANT_ID, PRODUTO_ACABADO_ID))
                .thenReturn(Optional.of(anterior));
        when(fichaTecnicaRepository.save(any())).thenAnswer(inv -> {
            FichaTecnica f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
        UUID componenteId = UUID.randomUUID();

        producaoService.criarFichaTecnica(TENANT_ID, USER_ID, PRODUTO_ACABADO_ID,
                List.of(new ProducaoService.ItemFicha(componenteId, new BigDecimal("2"))));

        assertThat(anterior.getAtivo()).isFalse();
        verify(fichaTecnicaRepository, times(2)).save(any()); // desativa anterior + grava a nova
        verify(fichaTecnicaItemRepository).save(any(FichaTecnicaItem.class));
    }

    @Test
    void criarOrdemProducaoSemFichaAtiva_lanca400() {
        when(fichaTecnicaRepository.findByTenantIdAndProdutoAcabadoIdAndAtivoTrue(TENANT_ID, PRODUTO_ACABADO_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> producaoService.criarOrdemProducao(TENANT_ID, USER_ID, PRODUTO_ACABADO_ID,
                BigDecimal.TEN, DEPOSITO_ID))
                .isInstanceOf(BusinessException.class);
        verify(ordemProducaoRepository, never()).save(any());
    }

    @Test
    void apontarProducaoOrdemNaoAberta_lanca409() {
        OrdemProducao ordem = OrdemProducao.builder().id(UUID.randomUUID()).status(StatusOrdemProducao.CONCLUIDA).build();
        when(ordemProducaoRepository.findByIdAndTenantId(ordem.getId(), TENANT_ID)).thenReturn(Optional.of(ordem));

        assertThatThrownBy(() -> producaoService.apontarProducao(TENANT_ID, USER_ID, ordem.getId(), BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
        verify(estoqueService, never()).registrarMovimento(any());
    }

    @Test
    void apontarProducaoOrdemAberta_geraSaidaPorComponenteEUmaEntradaEConcluiOrdem() {
        UUID componenteId = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();
        OrdemProducao ordem = OrdemProducao.builder().id(UUID.randomUUID()).produtoAcabadoId(PRODUTO_ACABADO_ID)
                .depositoId(DEPOSITO_ID).status(StatusOrdemProducao.ABERTA).build();
        FichaTecnica ficha = FichaTecnica.builder().id(fichaId).produtoAcabadoId(PRODUTO_ACABADO_ID).ativo(true).build();
        FichaTecnicaItem item = FichaTecnicaItem.builder().fichaTecnicaId(fichaId).produtoComponenteId(componenteId)
                .quantidade(new BigDecimal("2")).build();
        EstoqueSaldo saldoComponente = EstoqueSaldo.builder().custoMedio(new BigDecimal("10")).build();

        when(ordemProducaoRepository.findByIdAndTenantId(ordem.getId(), TENANT_ID)).thenReturn(Optional.of(ordem));
        when(fichaTecnicaRepository.findByTenantIdAndProdutoAcabadoIdAndAtivoTrue(TENANT_ID, PRODUTO_ACABADO_ID))
                .thenReturn(Optional.of(ficha));
        when(fichaTecnicaItemRepository.findByFichaTecnicaId(fichaId)).thenReturn(List.of(item));
        when(estoqueSaldoRepository.findByTenantIdAndProdutoIdAndDepositoId(TENANT_ID, componenteId, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoComponente));

        producaoService.apontarProducao(TENANT_ID, USER_ID, ordem.getId(), new BigDecimal("5"));

        ArgumentCaptor<EstoqueService.MovimentoRequisicao> captor =
                ArgumentCaptor.forClass(EstoqueService.MovimentoRequisicao.class);
        verify(estoqueService, times(2)).registrarMovimento(captor.capture());
        assertThat(captor.getAllValues().get(0).linhas()).hasSize(1);
        assertThat(captor.getAllValues().get(0).linhas().get(0).quantidade())
                .isEqualByComparingTo(new BigDecimal("10")); // 2*5
        assertThat(captor.getAllValues().get(1).linhas().get(0).valorUnitario())
                .isEqualByComparingTo(new BigDecimal("20")); // (10*2*5)/5
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemProducao.CONCLUIDA);
        verify(ordemProducaoRepository).save(ordem);
    }
}
