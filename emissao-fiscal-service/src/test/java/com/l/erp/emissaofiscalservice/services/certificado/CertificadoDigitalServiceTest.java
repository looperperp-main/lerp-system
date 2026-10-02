package com.l.erp.emissaofiscalservice.services.certificado;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.services.crypto.EnvelopeEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Consulta do certificado atual (alimenta a tela de Emissão Fiscal) e a validação do upload: arquivo
 * que não abre como PKCS12 nunca chega a ser cifrado nem gravado.
 */
@ExtendWith(MockitoExtension.class)
class CertificadoDigitalServiceTest {

    private static final UUID EMITENTE_ID = UUID.randomUUID();

    @Mock
    private CertificadoDigitalRepository repository;

    @Mock
    private EnvelopeEncryptionService envelopeEncryptionService;

    private CertificadoDigitalService service() {
        return new CertificadoDigitalService(repository, envelopeEncryptionService);
    }

    private void comTenant(String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Constants.HEADER_TENANT_ID, tenantId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limparContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void buscarDevolveOCertificadoDoTenantEEmitente() {
        comTenant("1");
        CertificadoDigital cert = new CertificadoDigital();
        when(repository.findByTenantIdAndEmitenteId(1L, EMITENTE_ID)).thenReturn(Optional.of(cert));

        assertSame(cert, service().buscar(EMITENTE_ID).orElseThrow());
    }

    @Test
    void buscarDevolveVazioQuandoOEmitenteNaoEnviouCertificado() {
        comTenant("1");
        when(repository.findByTenantIdAndEmitenteId(1L, EMITENTE_ID)).thenReturn(Optional.empty());

        assertTrue(service().buscar(EMITENTE_ID).isEmpty());
    }

    @Test
    void buscarSemTenantNaoConsultaORepositorio() {
        // sem RequestContext, SecurityUtils.getCurrentTenantId() vem vazio
        assertThrows(BusinessException.class, () -> service().buscar(EMITENTE_ID));
        verifyNoInteractions(repository);
    }

    @Test
    void uploadComArquivoInvalidoNaoCifraNemGrava() {
        comTenant("1");
        var naoEPfx = new MockMultipartFile("arquivo", "x.pfx", "application/octet-stream", "lixo".getBytes());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service().upload(EMITENTE_ID, "40152424000196", naoEPfx, "senha"));

        assertEquals("Não foi possível abrir o certificado A1 — verifique o arquivo .pfx e a senha.", e.getMessage());
        verifyNoInteractions(envelopeEncryptionService);
        verify(repository, never()).save(any());
    }
}
