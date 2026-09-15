package com.l.erp.operacoesservice.services.compras;

import com.l.erp.operacoesservice.domain.compras.CompraNumeracao;
import com.l.erp.operacoesservice.domain.compras.CompraNumeracaoId;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.repository.compras.CompraNumeracaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/** Mesmo padrão de PedidoNumeroServiceTest (vendas): upsert→lock→incrementa. */
@ExtendWith(MockitoExtension.class)
class CompraNumeroServiceTest {

    @Mock
    private CompraNumeracaoRepository compraNumeracaoRepository;

    @InjectMocks
    private CompraNumeroService compraNumeroService;

    private static final Long TENANT_ID = 1L;

    @Test
    void deveInicializarAntesDeTravarERetornarONumeroAtualIncrementandoOProximo() {
        CompraNumeracao numeracao = CompraNumeracao.builder()
                .id(new CompraNumeracaoId(TENANT_ID, TipoDocumentoCompra.REQUISICAO))
                .proximoNumero(1L)
                .build();
        when(compraNumeracaoRepository.findByIdForUpdate(TENANT_ID, TipoDocumentoCompra.REQUISICAO))
                .thenReturn(Optional.of(numeracao));

        Long numero = compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.REQUISICAO);

        assertThat(numero).isEqualTo(1L);
        assertThat(numeracao.getProximoNumero()).isEqualTo(2L);
        var ordem = inOrder(compraNumeracaoRepository);
        ordem.verify(compraNumeracaoRepository).inicializarSeNaoExiste(TENANT_ID, "REQUISICAO");
        ordem.verify(compraNumeracaoRepository).findByIdForUpdate(TENANT_ID, TipoDocumentoCompra.REQUISICAO);
    }

    @Test
    void deveLancarSeLinhaNaoExisteAposOUpsert() {
        when(compraNumeracaoRepository.findByIdForUpdate(TENANT_ID, TipoDocumentoCompra.REQUISICAO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.REQUISICAO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(TENANT_ID));
    }

    // ponytail: concorrência real (duas transações disputando o mesmo tenant/tipo) exige Testcontainers —
    // fora do escopo de um teste unitário; os dois casos acima cobrem a lógica upsert→lock→incrementa.
}
