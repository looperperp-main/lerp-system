package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;

import java.time.Instant;
import java.util.UUID;

/** Linha do histórico de transição de status (aninhado em RequisicaoCompraResponseDTO.historico). */
public record CompraStatusHistoricoDTO(
        UUID id,
        TipoDocumentoCompra documentoTipo,
        UUID documentoId,
        String statusAnterior,
        String statusNovo,
        String motivo,
        UUID usuarioId,
        Instant ocorridoEm
) {
}
