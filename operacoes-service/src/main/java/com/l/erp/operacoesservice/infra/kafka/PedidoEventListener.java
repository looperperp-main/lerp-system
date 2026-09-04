package com.l.erp.operacoesservice.infra.kafka;

import com.l.erp.operacoesservice.services.vendas.PedidoCanceladoEvent;
import com.l.erp.operacoesservice.services.vendas.PedidoConfirmadoEvent;
import com.l.erp.operacoesservice.services.vendas.PedidoFaturadoEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica os eventos Kafka do pedido só depois do commit da transação (Fase 5,
 * spec/o2c-vendas.md §8) — evita publicar confirmação/faturamento/cancelamento de uma transação
 * que ainda pode dar rollback.
 */
@Component
public class PedidoEventListener {

    private final PedidoEventProducer producer;

    public PedidoEventListener(PedidoEventProducer producer) {
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmar(PedidoConfirmadoEvent event) {
        producer.publicarConfirmado(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoFaturar(PedidoFaturadoEvent event) {
        producer.publicarFaturado(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCancelar(PedidoCanceladoEvent event) {
        producer.publicarCancelado(event);
    }
}
