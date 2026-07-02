package com.l.erp.partnerservice.infra.billing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BillingExtratoDTO(
        BigDecimal comissaoMesAtual,
        BigDecimal totalPago,
        BigDecimal ultimoRepasseValor,
        String ultimoRepassePeriodo,
        OffsetDateTime ultimoRepassePagoEm,
        int diasParaRepasse,
        List<BillingComissaoItemDTO> historico
) {}
