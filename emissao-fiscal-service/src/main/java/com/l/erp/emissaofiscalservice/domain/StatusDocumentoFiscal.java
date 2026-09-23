package com.l.erp.emissaofiscalservice.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados do documento fiscal — spec/modulos/emissao-fiscal/emissao-fiscal.md §3 item 10.
 *
 * <pre>
 * RASCUNHO → ASSINADO → TRANSMITIDO → AUTORIZADO | REJEITADO | DENEGADO | CONTINGENCIA
 * AUTORIZADO   → CANCELADO
 * RASCUNHO     → INUTILIZADO
 * REJEITADO    → RASCUNHO   (reaproveita número e Idempotency-Key)
 * TRANSMITIDO  → ERRO       (job de reconciliação, presa além da janela)
 * </pre>
 *
 * DENEGADO e CANCELADO e INUTILIZADO e ERRO são terminais.
 */
public enum StatusDocumentoFiscal {
    RASCUNHO,
    ASSINADO,
    TRANSMITIDO,
    AUTORIZADO,
    REJEITADO,
    DENEGADO,
    CONTINGENCIA,
    CANCELADO,
    INUTILIZADO,
    ERRO;

    private static final Map<StatusDocumentoFiscal, Set<StatusDocumentoFiscal>> TRANSICOES_PERMITIDAS = new EnumMap<>(StatusDocumentoFiscal.class);

    static {
        TRANSICOES_PERMITIDAS.put(RASCUNHO, EnumSet.of(ASSINADO, INUTILIZADO));
        TRANSICOES_PERMITIDAS.put(ASSINADO, EnumSet.of(TRANSMITIDO));
        TRANSICOES_PERMITIDAS.put(TRANSMITIDO, EnumSet.of(AUTORIZADO, REJEITADO, DENEGADO, CONTINGENCIA, ERRO));
        TRANSICOES_PERMITIDAS.put(CONTINGENCIA, EnumSet.of(ASSINADO));
        TRANSICOES_PERMITIDAS.put(AUTORIZADO, EnumSet.of(CANCELADO));
        TRANSICOES_PERMITIDAS.put(REJEITADO, EnumSet.of(RASCUNHO));
        TRANSICOES_PERMITIDAS.put(DENEGADO, EnumSet.noneOf(StatusDocumentoFiscal.class));
        TRANSICOES_PERMITIDAS.put(CANCELADO, EnumSet.noneOf(StatusDocumentoFiscal.class));
        TRANSICOES_PERMITIDAS.put(INUTILIZADO, EnumSet.noneOf(StatusDocumentoFiscal.class));
        TRANSICOES_PERMITIDAS.put(ERRO, EnumSet.noneOf(StatusDocumentoFiscal.class));
    }

    public boolean podeTransicionarPara(StatusDocumentoFiscal destino) {
        return TRANSICOES_PERMITIDAS.get(this).contains(destino);
    }

    public boolean isTerminal() {
        return TRANSICOES_PERMITIDAS.get(this).isEmpty();
    }
}
