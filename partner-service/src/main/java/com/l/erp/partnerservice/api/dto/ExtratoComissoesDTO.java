package com.l.erp.partnerservice.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ExtratoComissoesDTO(
        BigDecimal comissaoMesAtual,
        BigDecimal totalPago,
        BigDecimal ultimoRepasseValor,
        String ultimoRepassePeriodo,
        OffsetDateTime ultimoRepassePagoEm,
        int diasParaRepasse,
        List<ComissaoItemDTO> historico
) {}
