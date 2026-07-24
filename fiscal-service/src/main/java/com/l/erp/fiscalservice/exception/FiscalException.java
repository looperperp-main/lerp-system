package com.l.erp.fiscalservice.exception;

/**
 * Erro de cálculo fiscal. O {@code codigo} é um dos códigos do §1.4.9
 * (ver {@code Constants.FISCAL_*}). O mapeamento HTTP (via GlobalExceptionHandler)
 * entra na fatia com endpoint.
 */
public class FiscalException extends RuntimeException {

    private final String codigo;

    public FiscalException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}