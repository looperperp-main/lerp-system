package com.l.erp.partnerservice.infra.billing;

import java.math.BigDecimal;
import java.util.List;

public record BillingExtratoDTO(
        BigDecimal comissaoMesAtual,
        BigDecimal totalPago,
        List<BillingComissaoItemDTO> historico
) {}