package com.l.erp.billingservice.api.dto;

import java.time.OffsetDateTime;

/** Retorno do cancelamento manual: novo status e até quando o acesso permanece (§6/§7). */
public record CancelSubscriptionResponse(
        String status,
        OffsetDateTime acessoAte
) {}
