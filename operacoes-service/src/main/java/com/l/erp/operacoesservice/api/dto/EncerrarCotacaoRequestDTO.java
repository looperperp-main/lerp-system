package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corpo de encerramento de cotação (spec/p2p-compras.md, Fase 5) — a escolha do vencedor é
 * sempre manual, feita pelo usuário; {@code ordemSugerida} no response é só apoio à decisão.
 */
public record EncerrarCotacaoRequestDTO(@NotNull UUID cotacaoFornecedorVencedorId) {
}
