package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** O tamanho do motivo (15 a 255) é regra do evento de cancelamento, validada no serviço com mensagem própria. */
public record CancelamentoNfseRequestDTO(@NotBlank String motivo) {
}
