package com.l.erp.operacoesservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Parcela calculada no faturamento (espelha PedidoService.ParcelaFaturamento). Aninhada só na
 * resposta de POST .../faturar — não persistida (Fase 5 cuida do evento venda.pedido.faturado, §8).
 */
public record ParcelaFaturamentoDTO(
        Integer numero,
        LocalDate dataVencimento,
        BigDecimal valor,
        String formaPagamento
) {
}
