package com.l.erp.partnerservice.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Listagem mestre-detalhe de indicações: o mestre é o contador (parceiro)
 * e o detalhe são os indicados. Identificados por nome, sem expor IDs.
 */
public record IndicacoesPorContadorDTO(
        String contador,
        String crc,
        String referralCode,
        BigDecimal commissionRate,
        long totalIndicacoes,
        long convertidas,
        List<IndicacaoDTO> indicacoes
) {
    public record IndicacaoDTO(
            String razaoSocial,
            String cnpj,
            String email,
            String status,
            String planoSugerido,
            OffsetDateTime invitedAt,
            OffsetDateTime activatedAt,
            OffsetDateTime convertedAt,
            OffsetDateTime trialExpiresAt
    ) {}
}
