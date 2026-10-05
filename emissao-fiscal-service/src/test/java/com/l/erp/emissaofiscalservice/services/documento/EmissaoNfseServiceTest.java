package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse.Situacao;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfseResolver;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Emissão da NFS-e: grava TRANSMITIDO antes de chamar o provedor, nunca reenvia às cegas (depois de
 * qualquer incerteza consulta primeiro; só NAO_ENCONTRADA libera o reenvio) e traduz o desfecho em
 * estado. Provedor mockado — nada de rede.
 */
@ExtendWith(MockitoExtension.class)
class EmissaoNfseServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String CHAVE = "31040072240152424000196000000000000126100123456789";

    @Mock
    private DocumentoFiscalRepository repository;
    @Mock
    private CertificadoDigitalRepository certificadoRepository;
    @Mock
    private CertificadoDigitalService certificadoService;
    @Mock
    private DocumentoFiscalService documentoService;
    @Mock
    private AlertaOperacionalService alerta;
    @Mock
    private ProvedorNfseResolver resolver;
    @Mock
    private ProvedorNfse provedor;

    private EmissaoNfseService service;

    @BeforeEach
    void setUp() {
        service = new EmissaoNfseService(repository, certificadoRepository, certificadoService, documentoService,
                alerta, resolver);
    }

    @Test
    void iniciarGravaTransmitidoAntesDeQualquerChamadaAoProvedor() {
        DocumentoFiscal documento = documento(StatusDocumentoFiscal.RASCUNHO);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.RASCUNHO)).thenReturn(Optional.of(documento));

        assertTrue(service.iniciar(ID));

        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.TRANSMITIDO);
        verifyNoInteractions(provedor);
    }

    @Test
    void iniciarIgnoraDocumentoQueJaNaoEstaEmRascunho() {
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.RASCUNHO)).thenReturn(Optional.empty());

        assertFalse(service.iniciar(ID));
    }

    @Test
    void primeiroEnvioAutorizadoGravaAChaveENaoConsultaAntes() {
        DocumentoFiscal documento = prepararTransmitido();
        when(provedor.emitir(any(), any())).thenReturn(new ResultadoNfse(Situacao.AUTORIZADA, CHAVE, null, null));

        service.processar(ID, true);

        assertEquals(CHAVE, documento.getChaveAcesso());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.AUTORIZADO);
        verify(provedor, never()).consultar(any(), any());
    }

    @Test
    void rejeitadaPeloProvedorVaiParaRejeitadoComAMensagem() {
        DocumentoFiscal documento = prepararTransmitido();
        when(provedor.emitir(any(), any())).thenReturn(new ResultadoNfse(Situacao.REJEITADA, null, null, "regra violada"));

        service.processar(ID, true);

        assertEquals("regra violada", documento.getUltimaMensagemSefaz());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.REJEITADO);
    }

    @Test
    void retomadaConsultaPrimeiroEAutorizaSemReenviarQuandoOProvedorJaTemANota() {
        DocumentoFiscal documento = prepararTransmitido();
        when(provedor.consultar(any(), any())).thenReturn(new ResultadoNfse(Situacao.AUTORIZADA, CHAVE, null, null));

        service.processar(ID, false);

        assertEquals(CHAVE, documento.getChaveAcesso());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.AUTORIZADO);
        verify(provedor, never()).emitir(any(), any());
    }

    @Test
    void retomadaSoReenviaQuandoOProvedorConfirmaQueNuncaRecebeu() {
        DocumentoFiscal documento = prepararTransmitido();
        when(provedor.consultar(any(), any())).thenReturn(new ResultadoNfse(Situacao.NAO_ENCONTRADA, null, null, null));
        when(provedor.emitir(any(), any())).thenReturn(new ResultadoNfse(Situacao.AUTORIZADA, CHAVE, null, null));

        service.processar(ID, false);

        verify(provedor).emitir(any(), any());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.AUTORIZADO);
    }

    @Test
    void pendenteAgendaNovaConsultaSemMudarDeEstado() {
        DocumentoFiscal documento = prepararTransmitido();
        when(provedor.consultar(any(), any())).thenReturn(new ResultadoNfse(Situacao.PENDENTE, null, null, null));

        service.processar(ID, false);

        assertNotNull(documento.getProximaTentativaEm());
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void timeoutNoEnvioContaTentativaEAgendaBackoffSemMudarDeEstado() {
        DocumentoFiscal documento = prepararTransmitido();
        when(provedor.emitir(any(), any())).thenThrow(new WebServiceComunicacaoException("timeout"));

        service.processar(ID, true);

        assertEquals(1, documento.getTentativasTransmissao());
        assertNotNull(documento.getProximaTentativaEm());
        verify(repository).save(documento);
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void esgotadasAsTentativasVaiParaErroEAvisaOResponsavel() {
        DocumentoFiscal documento = prepararTransmitido();
        documento.setTentativasTransmissao((short) (Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS - 1));
        when(provedor.consultar(any(), any())).thenThrow(new WebServiceComunicacaoException("timeout"));

        service.processar(ID, false);

        assertEquals(Constants.EMISSAO_ERRO_TRANSMISSAO_SEM_RESPOSTA, documento.getUltimoErro());
        assertNull(documento.getProximaTentativaEm());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        verify(alerta).alertarDocumentoEmErro(documento);
    }

    @Test
    void recusaDoProvedorOuConfiguracaoInvalidaVaiDiretoParaErroSemRetentar() {
        DocumentoFiscal documento = prepararTransmitido();
        when(provedor.emitir(any(), any()))
                .thenThrow(new BusinessException("certificado recusado", HttpStatus.UNPROCESSABLE_ENTITY));

        service.processar(ID, true);

        assertEquals("certificado recusado", documento.getUltimoErro());
        assertEquals(0, documento.getTentativasTransmissao());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
    }

    @Test
    void documentoSemProvedorGravadoVaiParaErroSemTocarNoCertificado() {
        DocumentoFiscal documento = documento(StatusDocumentoFiscal.TRANSMITIDO);
        documento.setProvedor(null);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.of(documento));
        when(resolver.porCodigo(null)).thenThrow(new BusinessException("sem provedor", HttpStatus.UNPROCESSABLE_ENTITY));

        service.processar(ID, true);

        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        verifyNoInteractions(certificadoRepository);
    }

    // ---- apoio ----

    private DocumentoFiscal prepararTransmitido() {
        DocumentoFiscal documento = documento(StatusDocumentoFiscal.TRANSMITIDO);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.of(documento));
        when(resolver.porCodigo(CodigoProvedorNfse.ADN)).thenReturn(provedor);
        CertificadoDigital certificado = mock(CertificadoDigital.class);
        when(certificado.isAtivo()).thenReturn(true);
        when(certificado.getCertificadoValidoAte()).thenReturn(OffsetDateTime.now().plusDays(90));
        when(certificadoRepository.findByTenantIdAndEmitenteId(any(), any())).thenReturn(Optional.of(certificado));
        when(certificadoService.decifrar(any(), any())).thenReturn("senha".getBytes());
        return documento;
    }

    private static DocumentoFiscal documento(StatusDocumentoFiscal status) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setTenantId(9L);
        d.setEmitenteId(UUID.randomUUID());
        d.setDocumento("NFSE");
        d.setProvedor(CodigoProvedorNfse.ADN);
        d.setAmbiente("HOMOLOGACAO");
        d.setSerie("1");
        d.setNumero(7L);
        d.setStatus(status);
        d.setPayloadRecebido("{}");
        return d;
    }
}
