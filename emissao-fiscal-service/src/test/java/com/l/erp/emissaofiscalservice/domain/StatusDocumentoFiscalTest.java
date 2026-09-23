package com.l.erp.emissaofiscalservice.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Máquina de estados do documento fiscal — spec §3 item 10. */
class StatusDocumentoFiscalTest {

    @ParameterizedTest
    @CsvSource({
            "RASCUNHO, ASSINADO, true",
            "RASCUNHO, INUTILIZADO, true",
            "RASCUNHO, AUTORIZADO, false",
            "ASSINADO, TRANSMITIDO, true",
            "ASSINADO, RASCUNHO, false",
            "TRANSMITIDO, AUTORIZADO, true",
            "TRANSMITIDO, REJEITADO, true",
            "TRANSMITIDO, DENEGADO, true",
            "TRANSMITIDO, CONTINGENCIA, true",
            "TRANSMITIDO, ERRO, true",
            "CONTINGENCIA, ASSINADO, true",
            "AUTORIZADO, CANCELADO, true",
            "AUTORIZADO, TRANSMITIDO, false",
            "REJEITADO, RASCUNHO, true",
            "DENEGADO, RASCUNHO, false",
            "CANCELADO, RASCUNHO, false",
    })
    void transicoes(StatusDocumentoFiscal origem, StatusDocumentoFiscal destino, boolean permitida) {
        assertThat(origem.podeTransicionarPara(destino)).isEqualTo(permitida);
    }

    @Test
    void estadosTerminaisNaoTemSaida() {
        assertThat(DENEGADO.isTerminal()).isTrue();
        assertThat(CANCELADO.isTerminal()).isTrue();
        assertThat(INUTILIZADO.isTerminal()).isTrue();
        assertThat(ERRO.isTerminal()).isTrue();
    }

    @Test
    void rascunhoNaoEhTerminal() {
        assertThat(RASCUNHO.isTerminal()).isFalse();
    }
}
