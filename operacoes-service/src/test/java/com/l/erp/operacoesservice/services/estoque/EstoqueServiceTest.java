package com.l.erp.operacoesservice.services.estoque;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import com.l.erp.operacoesservice.repository.estoque.EstoqueSaldoRepository;
import com.l.erp.operacoesservice.repository.estoque.MovimentoEstoqueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock
    private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Mock
    private EstoqueSaldoRepository estoqueSaldoRepository;

    @InjectMocks
    private EstoqueService estoqueService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUTO_ID = UUID.randomUUID();
    private static final UUID DEPOSITO_ID = UUID.randomUUID();

    private EstoqueSaldo saldoExistente(BigDecimal quantidade) {
        EstoqueSaldo saldo = EstoqueSaldo.builder()
                .produtoId(PRODUTO_ID)
                .depositoId(DEPOSITO_ID)
                .quantidade(quantidade)
                .createdAt(Instant.now())
                .createdBy(USER_ID)
                .build();
        saldo.setTenantId(TENANT_ID);
        return saldo;
    }

    private EstoqueService.MovimentoRequisicao requisicao(TipoMovimentoEstoque tipo, BigDecimal quantidade) {
        return new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID, tipo, OrigemMovimentoEstoque.PEDIDO_VENDA,
                UUID.randomUUID(), DEPOSITO_ID, Instant.now(), null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, quantidade, BigDecimal.TEN)));
    }

    // ---------------------------------------------------------------- registrarMovimento

    @Test
    void entradaEmProdutoSemSaldo_criaSaldoComAQuantidadeEGrava1Movimento() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.empty());

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.ENTRADA_COMPRA, BigDecimal.TEN));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void entradaEmProdutoComSaldo_somaENaoDuplicaLinhaDeSaldo() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.ENTRADA_COMPRA, BigDecimal.TEN));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("15"));
    }

    @Test
    void saida_subtrai() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("20"))));

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.TEN));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void saidaMaiorQueSaldoComFlagOff_permiteEDeixaSaldoNegativo() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.TEN));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("-5"));
    }

    @Test
    void saidaMaiorQueSaldoComFlagOn_lancaBusinessException400ENadaGravado() {
        ReflectionTestUtils.setField(estoqueService, "bloquearSaida", true);
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        assertThatThrownBy(() -> estoqueService.registrarMovimento(
                requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class);

        verify(estoqueSaldoRepository, never()).save(any());
        verify(movimentoEstoqueRepository, never()).saveAndFlush(any());
    }

    @Test
    void estornoSaidaVendaComSaldoNegativoFlagOn_passaPoisEstornoNuncaEBloqueado() {
        ReflectionTestUtils.setField(estoqueService, "bloquearSaida", true);
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("-5"))));

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.ESTORNO_SAIDA_VENDA, BigDecimal.TEN));

        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void duasLinhasDoMesmoProduto_agregaEm1MovimentoComASoma() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.empty());
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, UUID.randomUUID(),
                DEPOSITO_ID, Instant.now(), null, List.of(
                        new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, new BigDecimal("3"), BigDecimal.TEN),
                        new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, new BigDecimal("7"), BigDecimal.ONE)));

        estoqueService.registrarMovimento(req);

        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
    }

    // ---------------------------------------------------------------- ajustar

    @Test
    void ajusteComQuantidadeContadaMaiorQueSaldo_gravaAjusteEntradaComODelta() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID, PRODUTO_ID, DEPOSITO_ID,
                new BigDecimal("8"), OrigemMovimentoEstoque.INVENTARIO, "contagem cíclica", null));

        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void ajusteComQuantidadeContadaMenorQueSaldo_gravaAjusteSaidaComOModuloDoDelta() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("10"))));

        estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID, PRODUTO_ID, DEPOSITO_ID,
                new BigDecimal("3"), OrigemMovimentoEstoque.AJUSTE, "avaria", null));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void ajusteComQuantidadeContadaIgualAoSaldo_naoGravaNadaNoOp() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("10"))));

        estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID, PRODUTO_ID, DEPOSITO_ID,
                BigDecimal.TEN, OrigemMovimentoEstoque.AJUSTE, "confirmação", null));

        verifyNoInteractions(movimentoEstoqueRepository);
        verify(estoqueSaldoRepository, never()).save(any());
    }

    @Test
    void ajusteSemMotivo_lanca400() {
        assertThatThrownBy(() -> estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID,
                PRODUTO_ID, DEPOSITO_ID, BigDecimal.TEN, OrigemMovimentoEstoque.AJUSTE, "", null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(movimentoEstoqueRepository);
    }

    // ---------------------------------------------------------------- validação

    @Test
    void quantidadeMenorOuIgualAZeroNaRequisicao_lanca400() {
        assertThatThrownBy(() -> estoqueService.registrarMovimento(
                requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(movimentoEstoqueRepository);
        verifyNoInteractions(estoqueSaldoRepository);
    }

    @Test
    void movimentoComOrigemDocumentalEOrigemIdNull_lanca400() {
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.SAIDA_VENDA, OrigemMovimentoEstoque.PEDIDO_VENDA, null, DEPOSITO_ID,
                Instant.now(), null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, BigDecimal.TEN, null)));

        assertThatThrownBy(() -> estoqueService.registrarMovimento(req))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(movimentoEstoqueRepository);
        verifyNoInteractions(estoqueSaldoRepository);
    }
}
