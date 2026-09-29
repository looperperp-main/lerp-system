package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;

import java.util.UUID;

/**
 * Corpo do evento de outbox publicado no Kafka a cada transição de estado (spec §3 item 10).
 * {@code mensagem} e {@code tentativasAssinatura} só têm valor quando houve falha (status {@code ERRO});
 * o texto é seguro pro consumidor — o detalhe técnico fica só no log.
 */
record DocumentoFiscalEventoPayload(
        UUID documentoId,
        Long tenantId,
        UUID emitenteId,
        String documento,
        String serie,
        long numero,
        StatusDocumentoFiscal status,
        String mensagem,
        short tentativasAssinatura
) {
    static DocumentoFiscalEventoPayload from(DocumentoFiscal entidade) {
        return new DocumentoFiscalEventoPayload(
                entidade.getId(),
                entidade.getTenantId(),
                entidade.getEmitenteId(),
                entidade.getDocumento(),
                entidade.getSerie(),
                entidade.getNumero(),
                entidade.getStatus(),
                entidade.getUltimoErro(),
                entidade.getTentativasAssinatura());
    }
}
