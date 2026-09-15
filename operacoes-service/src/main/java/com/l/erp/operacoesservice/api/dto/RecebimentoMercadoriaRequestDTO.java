package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoFiscal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de criação/edição de recebimento de mercadoria (spec/p2p-compras.md, Fase 3).
 * {@code depositoId}/{@code condicaoPagamentoId} nulos herdam o valor do pedido (default —
 * resolvido pelo service). {@code nfeSerie}/{@code nfeChave}/{@code nfseCodigoVerificacao} são
 * condicionais a {@code tipoDocumentoFiscal} (RN-P2P-07), validados no service.
 */
public record RecebimentoMercadoriaRequestDTO(
        UUID depositoId,
        @NotNull LocalDate dataRecebimento,
        @NotNull TipoDocumentoFiscal tipoDocumentoFiscal,
        @NotNull @Size(max = 20) String nfeNumero,
        @Size(max = 5) String nfeSerie,
        @Size(max = 44) String nfeChave,
        @Size(max = 50) String nfseCodigoVerificacao,
        @NotNull LocalDate nfeDataEmissao,
        @NotNull BigDecimal valorTotalNf,
        UUID condicaoPagamentoId,
        @Size(max = 500) String observacao,
        @NotEmpty @Valid List<RecebimentoMercadoriaItemRequestDTO> itens
) {
}
