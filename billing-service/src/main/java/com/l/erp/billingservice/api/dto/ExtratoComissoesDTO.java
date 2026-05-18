package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExtratoComissoesDTO(
        BigDecimal comissaoMesAtual,
        BigDecimal totalPago,
        List<ComissaoItemDTO> historico
) {}