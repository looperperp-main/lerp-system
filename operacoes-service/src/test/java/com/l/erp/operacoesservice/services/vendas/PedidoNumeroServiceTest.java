package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.operacoesservice.domain.vendas.PedidoSequencia;
import com.l.erp.operacoesservice.repository.vendas.PedidoSequenciaRepository;
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

@ExtendWith(MockitoExtension.class)
class PedidoNumeroServiceTest {

    @Mock
    private PedidoSequenciaRepository pedidoSequenciaRepository;

    @InjectMocks
    private PedidoNumeroService pedidoNumeroService;

    @Test
    void deveInicializarAntesDeTravarERetornarONumeroAtualIncrementandoOProximo() {
        Long tenantId = 1L;
        PedidoSequencia sequencia = PedidoSequencia.builder().tenantId(tenantId).proximoNumero(1L).build();
        when(pedidoSequenciaRepository.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(sequencia));

        Long numero = pedidoNumeroService.proximoNumero(tenantId);

        assertThat(numero).isEqualTo(1L);
        assertThat(sequencia.getProximoNumero()).isEqualTo(2L);
        var ordem = inOrder(pedidoSequenciaRepository);
        ordem.verify(pedidoSequenciaRepository).inicializarSeNaoExiste(tenantId);
        ordem.verify(pedidoSequenciaRepository).findByTenantIdForUpdate(tenantId);
    }

    @Test
    void deveLancarSeLinhaNaoExisteAposOUpsert() {
        Long tenantId = 1L;
        when(pedidoSequenciaRepository.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoNumeroService.proximoNumero(tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(tenantId));
    }

    // ponytail: concorrência real (duas transações disputando o mesmo tenant) exige Testcontainers —
    // fora do escopo de um teste unitário; os dois casos acima cobrem a lógica upsert→lock→incrementa.
}
