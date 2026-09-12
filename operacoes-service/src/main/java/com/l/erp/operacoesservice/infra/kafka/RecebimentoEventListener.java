package com.l.erp.operacoesservice.infra.kafka;

import com.l.erp.operacoesservice.services.compras.RecebimentoCanceladoEvent;
import com.l.erp.operacoesservice.services.compras.RecebimentoConfirmadoEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica os eventos Kafka do recebimento só depois do commit da transação (Fase 3,
 * spec/p2p-compras.md §"Integração com estoque") — evita publicar confirmação/cancelamento de
 * uma transação que ainda pode dar rollback. Mesmo padrão de PedidoEventListener (vendas).
 */
@Component
public class RecebimentoEventListener {

    private final RecebimentoEventProducer producer;

    public RecebimentoEventListener(RecebimentoEventProducer producer) {
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmar(RecebimentoConfirmadoEvent event) {
        producer.publicarConfirmado(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCancelar(RecebimentoCanceladoEvent event) {
        producer.publicarCancelado(event);
    }
}
