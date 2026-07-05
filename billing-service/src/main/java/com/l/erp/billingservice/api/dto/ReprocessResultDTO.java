package com.l.erp.billingservice.api.dto;

/** Resultado do reprocessamento manual de ativação de uma assinatura. */
public record ReprocessResultDTO(boolean ativada, String status, String mensagem) {
}
