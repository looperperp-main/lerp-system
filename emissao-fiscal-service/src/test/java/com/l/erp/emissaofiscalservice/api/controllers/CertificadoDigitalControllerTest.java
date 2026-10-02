package com.l.erp.emissaofiscalservice.api.controllers;

import com.l.erp.emissaofiscalservice.api.dto.CertificadoDigitalResponseDTO;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * GET do certificado atual: 200 com os metadados quando existe, 204 quando o emitente ainda não
 * enviou. Teste unitário puro (service mockado) — o módulo não tem slice MVC configurado.
 */
@ExtendWith(MockitoExtension.class)
class CertificadoDigitalControllerTest {

    private static final UUID EMITENTE_ID = UUID.randomUUID();

    @Mock
    private CertificadoDigitalService service;

    @InjectMocks
    private CertificadoDigitalController controller;

    @Test
    void buscarDevolve200ComOsMetadadosQuandoExisteCertificado() {
        CertificadoDigital cert = new CertificadoDigital();
        cert.setCnpjSubject("40152424000196");
        when(service.buscar(EMITENTE_ID)).thenReturn(Optional.of(cert));

        ResponseEntity<CertificadoDigitalResponseDTO> resposta = controller.buscar(EMITENTE_ID);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertEquals("40152424000196", resposta.getBody().cnpjSubject());
    }

    @Test
    void buscarDevolve204SemCorpoQuandoNaoHaCertificado() {
        when(service.buscar(EMITENTE_ID)).thenReturn(Optional.empty());

        ResponseEntity<CertificadoDigitalResponseDTO> resposta = controller.buscar(EMITENTE_ID);

        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
        assertNull(resposta.getBody());
    }
}
