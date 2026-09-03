package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;

import java.time.Instant;
import java.util.UUID;

/** Linha do histórico de transição de status (aninhado em PedidoResponseDTO.historico, §3.3/§5). */
public record PedidoStatusHistoricoDTO(
        UUID id,
        StatusPedido statusDe,
        StatusPedido statusPara,
        String motivo,
        Instant createdAt,
        UUID createdBy
) {
}
