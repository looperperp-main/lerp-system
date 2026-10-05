package com.l.erp.emissaofiscalservice.services.nfse.adn;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GzipBase64Test {

    @Test
    void idaEVoltaPreservaAcentosEOTextoExato() {
        String xml = "<DPS><xDescServ>Desenvolvimento de software — manutenção</xDescServ></DPS>";

        assertEquals(xml, GzipBase64.descompactar(GzipBase64.compactar(xml)));
    }

    @Test
    void oGeradoEGzipDeVerdadeNaoZip() throws Exception {
        byte[] bytes = Base64.getDecoder().decode(GzipBase64.compactar("<DPS/>"));

        // cabeçalho GZip = 1f 8b; ZIP começaria com "PK"
        assertEquals((byte) 0x1f, bytes[0]);
        assertEquals((byte) 0x8b, bytes[1]);
        assertEquals("<DPS/>", new String(new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes()));
    }

    @Test
    void entradaQueNaoEGzipVirouErroClaro() {
        assertThrows(IllegalStateException.class, () -> GzipBase64.descompactar("naoebase64!!"));
        assertThrows(IllegalStateException.class,
                () -> GzipBase64.descompactar(Base64.getEncoder().encodeToString("texto puro".getBytes())));
    }
}
