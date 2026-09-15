package com.l.erp.operacoesservice.api.dto;

/** Corpo de recusa de convite de cotação por um fornecedor (spec/p2p-compras.md, Fase 5). */
public record DeclinarCotacaoFornecedorRequestDTO(String motivo) {
}
