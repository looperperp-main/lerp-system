package com.l.erp.emissaofiscalservice.services.nfe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trava o que a homologação da SEFAZ confirmou (2 de outubro de 2026): lote de uma única NF-e só
 * é aceito em modo síncrono; com {@code indSinc=0} volta cStat 452.
 */
class NfeMensagensTest {

    @Test
    void envioDeLoteUsaIndSincSincronoPoisASefazRejeitaLoteDeUmaNfeAssincrono() {
        var mensagem = NfeMensagens.enviNFe(1L, "<NFe/>");

        assertTrue(mensagem.corpo().contains("<indSinc>1</indSinc>"));
    }

    @Test
    void envioDeLoteTrazOIdDoLoteENfeAssinadaDentroDoEnviNFe() {
        var mensagem = NfeMensagens.enviNFe(7L, "<NFe>x</NFe>");

        assertTrue(mensagem.corpo().contains("<idLote>7</idLote>"));
        assertTrue(mensagem.corpo().contains("<NFe>x</NFe></enviNFe>"));
    }
}
