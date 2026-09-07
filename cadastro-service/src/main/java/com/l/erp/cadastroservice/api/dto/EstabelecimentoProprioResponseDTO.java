package com.l.erp.cadastroservice.api.dto;

import java.util.UUID;

/**
 * Resposta mínima de GET /api/v1/estabelecimentos/proprio — só o necessário pro
 * operacoes-service buscar o endereço fiscal da pessoa emitente (Fase 6,
 * spec/estabelecimentos-filiais.md §6.1).
 */
public record EstabelecimentoProprioResponseDTO(UUID pessoaId) {
}
