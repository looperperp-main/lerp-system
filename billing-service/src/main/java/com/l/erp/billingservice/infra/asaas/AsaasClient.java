package com.l.erp.billingservice.infra.asaas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Operações de money-out para o Asaas (repasse de comissão via PIX/TED).
 *
 * <p>As chamadas de entrada/criação de assinatura migraram para {@link AsaasGateway} +
 * sub-clients HTTP Interface (Fase 3). O payout real ({@code POST /v3/transfers} com
 * check-then-act §28.4 e chave PIX do parceiro) é da Fase 6 — por ora o método é um stub.</p>
 */
@Component
public class AsaasClient {

    private static final Logger log = LoggerFactory.getLogger(AsaasClient.class);

    public String transferPix(UUID partnerId, BigDecimal amount) {
        log.info("Iniciando transferência PIX para parceiro={} amount={}", partnerId, amount);
        // TODO Fase 6: buscar pixKey real do partner-service e chamar POST /v3/transfers (sem retry — §28.4)
        log.warn("PIX key não configurada para parceiro={} — transferência simulada em sandbox", partnerId);
        return "SIMULADO-" + UUID.randomUUID();
    }
}