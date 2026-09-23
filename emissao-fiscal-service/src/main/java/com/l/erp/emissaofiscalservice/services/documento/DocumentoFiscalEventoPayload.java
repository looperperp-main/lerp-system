package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;

import java.util.UUID;

/** Corpo do evento de outbox publicado no Kafka a cada transição de estado (spec §3 item 10). */
record DocumentoFiscalEventoPayload(
        UUID documentoId,
        Long tenantId,
        UUID estabelecimentoId,
        String documento,
        String serie,
        long numero,
        StatusDocumentoFiscal status
) {
    static DocumentoFiscalEventoPayload from(DocumentoFiscal entidade) {
        return new DocumentoFiscalEventoPayload(
                entidade.getId(),
                entidade.getTenantId(),
                entidade.getEstabelecimentoId(),
                entidade.getDocumento(),
                entidade.getSerie(),
                entidade.getNumero(),
                entidade.getStatus());
    }
}
