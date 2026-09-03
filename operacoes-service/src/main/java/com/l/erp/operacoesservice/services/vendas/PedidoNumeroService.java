package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.PedidoSequencia;
import com.l.erp.operacoesservice.repository.vendas.PedidoSequenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numeração sequencial do pedido por tenant (spec/o2c-vendas.md §3.4/§16 Fase 3). Orçamento e
 * pedido compartilham a mesma numeração — o que muda é só o status.
 */
@Service
public class PedidoNumeroService {

    private final PedidoSequenciaRepository pedidoSequenciaRepository;

    public PedidoNumeroService(PedidoSequenciaRepository pedidoSequenciaRepository) {
        this.pedidoSequenciaRepository = pedidoSequenciaRepository;
    }

    @Transactional
    public Long proximoNumero(Long tenantId) {
        pedidoSequenciaRepository.inicializarSeNaoExiste(tenantId);
        PedidoSequencia sequencia = pedidoSequenciaRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        String.format(Constants.PEDIDO_NUMERACAO_FALHA, tenantId)));

        Long numero = sequencia.getProximoNumero();
        sequencia.setProximoNumero(numero + 1);
        return numero;
    }
}
