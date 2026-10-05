package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfseResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guarda da NFS-e: o que é recusado na criação (4xx) em vez de virar nota rejeitada depois de gastar número. */
@ExtendWith(MockitoExtension.class)
class GuardaNfseServiceTest {

    private static final Long TENANT = 9L;
    private static final UUID EMITENTE = UUID.randomUUID();
    private static final String CNPJ = "40152424000196";
    private static final String MUNICIPIO = "3104007";

    @Mock
    private CertificadoDigitalRepository certificados;
    @Mock
    private ProvedorNfseResolver resolver;

    private GuardaNfseService guarda;

    @BeforeEach
    void setUp() {
        guarda = new GuardaNfseService(certificados, resolver);
    }

    @Test
    void pedidoValidoDeMeEppPassaEConfereAConfiguracaoDoEmitente() {
        certificadoAtivo(CNPJ, 90);

        assertDoesNotThrow(() -> guarda.validar(TENANT, pedido("HOMOLOGACAO", "3", "1", "1", null, LocalDate.now())));

        verify(resolver).configuracaoDe(TENANT, EMITENTE);
    }

    @Test
    void producaoAindaEhRecusadaPorqueADanfseNaoExiste() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> guarda.validar(TENANT, pedido("PRODUCAO", "3", "1", "1", null, LocalDate.now())));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(Constants.EMISSAO_ERRO_NFSE_PRODUCAO_SEM_DANFSE, e.getMessage());
    }

    @Test
    void ambienteInvalidoEh400() {
        assertEquals(HttpStatus.BAD_REQUEST, status("TESTE", "3", "1", "1", null, LocalDate.now()));
    }

    @Test
    void competenciaFuturaEh400() {
        assertEquals(HttpStatus.BAD_REQUEST, status("HOMOLOGACAO", "3", "1", "1", null, LocalDate.now().plusDays(1)));
    }

    @Test
    void meiAindaNaoEhSuportadoEh422() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, status("HOMOLOGACAO", "2", null, "1", null, LocalDate.now()));
    }

    @Test
    void meEppSemRegimeDeApuracaoEh400() {
        assertEquals(HttpStatus.BAD_REQUEST, status("HOMOLOGACAO", "3", null, "1", null, LocalDate.now()));
    }

    @Test
    void foraDoSimplesComServicoTributavelExigeAliquota() {
        assertEquals(HttpStatus.BAD_REQUEST, status("HOMOLOGACAO", "1", null, "1", null, LocalDate.now()));
    }

    @Test
    void foraDoSimplesComAliquotaPassa() {
        certificadoAtivo(CNPJ, 90);

        assertDoesNotThrow(() -> guarda.validar(TENANT,
                pedido("HOMOLOGACAO", "1", null, "1", new BigDecimal("2.00"), LocalDate.now())));
    }

    @Test
    void semCertificadoAtivoEh422() {
        when(certificados.findByTenantIdAndEmitenteId(any(), any())).thenReturn(Optional.empty());

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, status("HOMOLOGACAO", "3", "1", "1", null, LocalDate.now()));
    }

    @Test
    void certificadoVencidoEh422() {
        certificadoAtivo(CNPJ, -1);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, status("HOMOLOGACAO", "3", "1", "1", null, LocalDate.now()));
    }

    @Test
    void certificadoDeOutroCnpjEh400() {
        certificadoAtivo("11222333000181", 90);

        BusinessException e = assertThrows(BusinessException.class,
                () -> guarda.validar(TENANT, pedido("HOMOLOGACAO", "3", "1", "1", null, LocalDate.now())));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals(Constants.EMISSAO_ERRO_CERTIFICADO_CNPJ_DIVERGENTE.formatted("11222333000181"), e.getMessage());
    }

    // ---- apoio ----

    private HttpStatus status(String ambiente, String opcao, String regime, String tributacao, BigDecimal aliquota,
                              LocalDate competencia) {
        return assertThrows(BusinessException.class,
                () -> guarda.validar(TENANT, pedido(ambiente, opcao, regime, tributacao, aliquota, competencia)))
                .getStatus();
    }

    /** CNPJ com stub tolerante: só é lido quando o certificado está vigente. */
    private void certificadoAtivo(String cnpj, int diasDeValidade) {
        CertificadoDigital certificado = mock(CertificadoDigital.class);
        when(certificado.isAtivo()).thenReturn(true);
        when(certificado.getCertificadoValidoAte()).thenReturn(OffsetDateTime.now().plusDays(diasDeValidade));
        lenient().when(certificado.getCnpjSubject()).thenReturn(cnpj);
        when(certificados.findByTenantIdAndEmitenteId(any(), any())).thenReturn(Optional.of(certificado));
    }

    private static NfseRequestDTO pedido(String ambiente, String opcao, String regime, String tributacao,
                                         BigDecimal aliquota, LocalDate competencia) {
        EnderecoDTO endereco = new EnderecoDTO("Rua das Flores", "100", null, "Centro", MUNICIPIO, "Arcos", "MG",
                "35588000", null);
        return new NfseRequestDTO(EMITENTE, ambiente, "1", competencia,
                new NfseRequestDTO.Prestador(CNPJ, null, "ARCOS TECNOLOGIA LTDA", endereco, null, opcao, regime, "0"),
                null,
                new NfseRequestDTO.Servico("010101", null, "Desenvolvimento de software", MUNICIPIO),
                new NfseRequestDTO.Valores(new BigDecimal("100.00"), tributacao, aliquota, "1"));
    }
}
