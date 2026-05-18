package com.l.erp.partnerservice.infra.kafka;

import java.util.Map;

public record PartnerEmailNotificationEvent(
        String type,
        String to,
        String partnerName,
        String clientName,
        String clientCnpj,
        Map<String, Object> extraData
) {
    public static final String CLIENTE_ATIVOU        = "CLIENTE_ATIVOU";
    public static final String TRIAL_EXPIROU         = "TRIAL_EXPIROU";
    public static final String TRIAL_EXPIROU_CLIENTE = "TRIAL_EXPIROU_CLIENTE";
    public static final String FOLLOWUP_MENSAGEM     = "FOLLOWUP_MENSAGEM";
    public static final String PERDIDO               = "PERDIDO";
    public static final String RELATORIO_D10         = "RELATORIO_D10";
    public static final String CLIENTE_CONVERTEU     = "CLIENTE_CONVERTEU";
}