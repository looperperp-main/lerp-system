package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Snapshot fiscal imutável do item, já calculado e gravado por quem chama no faturamento (spec §3
 * item 11, opção b) — este serviço nunca recalcula. Campos espelham o snapshot do faturamento com
 * nomes neutros. PIS/COFINS são opcionais aqui, mas a guarda exige quando a nota for de produto em
 * Regime Normal (spec §3 item 9). Campos de FCP/DIFAL/ICMS-ST/IPI existem só para a guarda detectar
 * o caso e bloquear — ainda não viram XML.
 */
public record SnapshotFiscalItemDTO(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "CFOP deve ter 4 dígitos") String cfop,
        @NotBlank String cstIcms,
        @NotBlank String cstIbsCbs,
        @NotBlank String cClassTrib,
        BigDecimal baseCalculoIcms,
        BigDecimal percentualIcms,
        BigDecimal valorIcms,
        BigDecimal percentualReducaoBaseIcms,
        String modalidadeBaseCalculoIcms,
        BigDecimal baseCalculoIbsCbs,
        BigDecimal percentualIbsUf,
        BigDecimal percentualIbsMunicipal,
        BigDecimal percentualCbs,
        BigDecimal percentualReducaoAplicado,
        BigDecimal valorIbsEstadual,
        BigDecimal valorIbsMunicipal,
        BigDecimal valorCbs,
        String cstPis,
        BigDecimal baseCalculoPis,
        BigDecimal percentualPis,
        BigDecimal valorPis,
        String cstCofins,
        BigDecimal baseCalculoCofins,
        BigDecimal percentualCofins,
        BigDecimal valorCofins,
        BigDecimal valorIcmsSt,
        BigDecimal valorIpi,
        BigDecimal percentualFcp,
        BigDecimal valorFcp,
        BigDecimal valorIcmsUfDestino,
        BigDecimal valorFcpUfDestino
) {
}
