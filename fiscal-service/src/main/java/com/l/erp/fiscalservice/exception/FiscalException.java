package com.l.erp.fiscalservice.exception;

import com.l.erp.common.exception.custom.BusinessException;

/**
 * Erro de cálculo fiscal. O {@code codigo} é um dos códigos do §1.4.9
 * (ver {@code Constants.FISCAL_*}) e vira a mensagem do erro.
 *
 * <p>Estende {@link BusinessException} para que o {@code GlobalExceptionHandler} (common)
 * já o trate como erro de negócio (HTTP 400) — input fiscal inválido nunca vira 500.
 */
public class FiscalException extends BusinessException {

    private final String codigo;

    public FiscalException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}