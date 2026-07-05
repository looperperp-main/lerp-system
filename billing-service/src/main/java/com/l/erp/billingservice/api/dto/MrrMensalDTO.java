package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;

/** Receita recorrente vigente no fim de cada mês (yyyy-MM). */
public record MrrMensalDTO(String mes, BigDecimal valor) {
}
