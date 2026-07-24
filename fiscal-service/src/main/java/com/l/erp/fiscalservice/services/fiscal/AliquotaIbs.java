package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;

/** Alíquota IBS decomposta em parcela estadual e municipal (percentuais). */
public record AliquotaIbs(BigDecimal estadual, BigDecimal municipal) {}