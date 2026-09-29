package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code POST /emissao/documentos} — spec §3 itens 9/10/11. Autocontido de propósito: o
 * emissao-fiscal-service nunca busca emitente/destinatário/cálculo fiscal em outro serviço (spec §2).
 * {@code emitenteId} é um identificador neutro fornecido por quem chama — nunca "estabelecimentoId"
 * do erp-vsd, pra não acoplar o contrato a um conceito que um chamador de fora do reator não teria
 * (spec §2, "vendável separadamente").
 *
 * <p>{@code tipoOperacao}: 0=entrada, 1=saída (tpNF). {@code indicadorFinal}: 0=normal, 1=consumidor
 * final (indFinal). {@code indicadorPresenca}: indPres (1=presencial, 2=internet, ...).</p>
 */
public record DocumentoFiscalRequestDTO(
        @NotNull UUID emitenteId,
        @NotBlank String documento,
        String modelo,
        @NotBlank String serie,
        @NotBlank String ambiente,
        @NotBlank @Size(max = 60) String naturezaOperacao,
        @NotNull @Min(0) @Max(1) Integer tipoOperacao,
        @NotNull @Min(0) @Max(1) Integer indicadorFinal,
        @NotNull @Min(0) @Max(9) Integer indicadorPresenca,
        @NotNull @Valid EmitenteDTO emitente,
        @NotNull @Valid DestinatarioDTO destinatario,
        @NotNull @PositiveOrZero BigDecimal valorTotal,
        @NotEmpty @Valid List<ItemDocumentoDTO> itens
) {
}
