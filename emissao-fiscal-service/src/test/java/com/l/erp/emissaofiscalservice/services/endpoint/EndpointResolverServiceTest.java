package com.l.erp.emissaofiscalservice.services.endpoint;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.ServicoWebservice;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.UfAutorizador;
import com.l.erp.emissaofiscalservice.domain.WebserviceEndpoint;
import com.l.erp.emissaofiscalservice.repository.UfAutorizadorRepository;
import com.l.erp.emissaofiscalservice.repository.WebserviceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EndpointResolverServiceTest {

    @Mock
    private UfAutorizadorRepository ufAutorizadorRepository;
    @Mock
    private WebserviceEndpointRepository webserviceEndpointRepository;

    private EndpointResolverService service;

    @BeforeEach
    void setUp() {
        service = new EndpointResolverService(ufAutorizadorRepository, webserviceEndpointRepository);
    }

    @Test
    void resolveUrlDoAutorizadorNormalQuandoNaoEstaEmContingencia() {
        UfAutorizador config = ufAutorizador("RJ", "SVRS", "SVC-AN");
        when(ufAutorizadorRepository.buscarVigentes(eq("RJ"), eq(TipoDocumentoFiscal.NFE), any(LocalDate.class)))
                .thenReturn(List.of(config));
        when(webserviceEndpointRepository.buscarVigentes(eq(TipoDocumentoFiscal.NFE), eq("SVRS"),
                eq(ServicoWebservice.NFE_AUTORIZACAO), eq(AmbienteEmissao.PRODUCAO), any(LocalDate.class)))
                .thenReturn(List.of(endpoint("https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx")));

        String url = service.resolverUrl("RJ", TipoDocumentoFiscal.NFE, ServicoWebservice.NFE_AUTORIZACAO,
                AmbienteEmissao.PRODUCAO, false);

        assertEquals("https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx", url);
    }

    @Test
    void resolveUrlDoAutorizadorDeContingenciaQuandoEmContingencia() {
        UfAutorizador config = ufAutorizador("RJ", "SVRS", "SVC-AN");
        when(ufAutorizadorRepository.buscarVigentes(eq("RJ"), eq(TipoDocumentoFiscal.NFE), any(LocalDate.class)))
                .thenReturn(List.of(config));
        when(webserviceEndpointRepository.buscarVigentes(eq(TipoDocumentoFiscal.NFE), eq("SVC-AN"),
                eq(ServicoWebservice.NFE_AUTORIZACAO), eq(AmbienteEmissao.PRODUCAO), any(LocalDate.class)))
                .thenReturn(List.of(endpoint("https://www.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx")));

        String url = service.resolverUrl("RJ", TipoDocumentoFiscal.NFE, ServicoWebservice.NFE_AUTORIZACAO,
                AmbienteEmissao.PRODUCAO, true);

        assertEquals("https://www.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx", url);
    }

    @Test
    void lancaErroDeNegocioQuandoUfSemAutorizadorConfigurado() {
        when(ufAutorizadorRepository.buscarVigentes(eq("XX"), eq(TipoDocumentoFiscal.NFE), any(LocalDate.class)))
                .thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.resolverUrl(
                "XX", TipoDocumentoFiscal.NFE, ServicoWebservice.NFE_AUTORIZACAO, AmbienteEmissao.PRODUCAO, false));
    }

    @Test
    void lancaErroDeNegocioQuandoAutorizadorSemEndpointConfigurado() {
        UfAutorizador config = ufAutorizador("RJ", "SVRS", "SVC-AN");
        when(ufAutorizadorRepository.buscarVigentes(eq("RJ"), eq(TipoDocumentoFiscal.NFE), any(LocalDate.class)))
                .thenReturn(List.of(config));
        when(webserviceEndpointRepository.buscarVigentes(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.resolverUrl(
                "RJ", TipoDocumentoFiscal.NFE, ServicoWebservice.NFE_AUTORIZACAO, AmbienteEmissao.PRODUCAO, false));
    }

    private UfAutorizador ufAutorizador(String uf, String normal, String contingencia) {
        UfAutorizador config = new UfAutorizador();
        config.setUf(uf);
        config.setDocumento(TipoDocumentoFiscal.NFE);
        config.setAutorizadorNormal(normal);
        config.setAutorizadorContingencia(contingencia);
        config.setPrazoCancelamentoHoras(24);
        config.setVigenteDe(LocalDate.of(2026, 1, 1));
        return config;
    }

    private WebserviceEndpoint endpoint(String url) {
        WebserviceEndpoint endpoint = new WebserviceEndpoint();
        endpoint.setDocumento(TipoDocumentoFiscal.NFE);
        endpoint.setServico(ServicoWebservice.NFE_AUTORIZACAO);
        endpoint.setAmbiente(AmbienteEmissao.PRODUCAO);
        endpoint.setVersao("4.00");
        endpoint.setUrl(url);
        endpoint.setVigenteDe(LocalDate.of(2026, 1, 1));
        return endpoint;
    }
}
