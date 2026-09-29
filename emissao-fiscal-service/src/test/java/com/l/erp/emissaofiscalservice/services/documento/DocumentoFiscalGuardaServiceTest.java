package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.api.dto.DestinatarioDTO;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.EmitenteDTO;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.ItemDocumentoDTO;
import com.l.erp.emissaofiscalservice.api.dto.SnapshotFiscalItemDTO;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Guardas do POST /emissao/documentos (spec §3 itens 2 e 9): bloquear em vez de emitir sem o valor
 * devido. Cada caso parte do payload válido e quebra um único ponto.
 */
@ExtendWith(MockitoExtension.class)
class DocumentoFiscalGuardaServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final UUID EMITENTE_ID = UUID.randomUUID();
    private static final String CNPJ = "11222333000181";
    private static final BigDecimal CEM = new BigDecimal("100.00");

    @Mock
    private CertificadoDigitalRepository certificadoRepository;

    private DocumentoFiscalGuardaService guarda;

    @BeforeEach
    void setUp() {
        guarda = new DocumentoFiscalGuardaService(certificadoRepository);
        certificadoDoTenant(certificado(CNPJ, OffsetDateTime.now().plusDays(90), true));
    }

    @Test
    void payloadValidoPassa() {
        assertDoesNotThrow(() -> guarda.validar(TENANT_ID, valido()));
    }

    @Test
    void rejeitaDocumentoQueNaoENfe() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, montar("NFCE", "HOMOLOGACAO", emitente("3", "123456789"),
                destinatario("1", "RJ"), 0, CEM, fiscalValido()));
    }

    @Test
    void rejeitaAmbienteInvalido() {
        assertStatus(HttpStatus.BAD_REQUEST, montar("NFE", "TESTE", emitente("3", "123456789"),
                destinatario("1", "RJ"), 0, CEM, fiscalValido()));
    }

    @Test
    void rejeitaEmitenteForaDoRegimeNormal() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, montar("NFE", "HOMOLOGACAO", emitente("1", "123456789"),
                destinatario("1", "RJ"), 0, CEM, fiscalValido()));
    }

    @Test
    void rejeitaEmitenteSemInscricaoEstadual() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, montar("NFE", "HOMOLOGACAO", emitente("3", null),
                destinatario("1", "RJ"), 0, CEM, fiscalValido()));
    }

    @Test
    void rejeitaTotalDivergenteDaSomaDosItens() {
        assertStatus(HttpStatus.BAD_REQUEST, montar("NFE", "HOMOLOGACAO", emitente("3", "123456789"),
                destinatario("1", "RJ"), 0, new BigDecimal("99.00"), fiscalValido()));
    }

    @Test
    void rejeitaItemSemPisCofins() {
        assertStatus(HttpStatus.BAD_REQUEST, comFiscal(fiscal("00", null, null, null, null)));
    }

    @Test
    void rejeitaIcmsComSubstituicaoTributaria() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, comFiscal(fiscal("10", null, null, null, "01")));
    }

    @Test
    void rejeitaIpiInformado() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, comFiscal(fiscal("00", new BigDecimal("5"), null, null, "01")));
    }

    @Test
    void rejeitaFcpInformado() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, comFiscal(fiscal("00", null, new BigDecimal("2"), null, "01")));
    }

    @Test
    void rejeitaDifalInformado() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, comFiscal(fiscal("00", null, null, new BigDecimal("3"), "01")));
    }

    @Test
    void rejeitaVendaInterestadualParaConsumidorFinalNaoContribuinte() {
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, montar("NFE", "HOMOLOGACAO", emitente("3", "123456789"),
                destinatario("9", "SP"), 1, CEM, fiscalValido()));
    }

    @Test
    void vendaInterestadualParaContribuinteNaoDisparaDifal() {
        assertDoesNotThrow(() -> guarda.validar(TENANT_ID, montar("NFE", "HOMOLOGACAO", emitente("3", "123456789"),
                destinatario("1", "SP"), 0, CEM, fiscalValido())));
    }

    @Test
    void rejeitaEmitenteSemCertificado() {
        lenient().when(certificadoRepository.findByTenantIdAndEmitenteId(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, valido());
    }

    @Test
    void rejeitaCertificadoVencido() {
        certificadoDoTenant(certificado(CNPJ, OffsetDateTime.now().minusDays(1), true));
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, valido());
    }

    @Test
    void rejeitaCertificadoInativo() {
        certificadoDoTenant(certificado(CNPJ, OffsetDateTime.now().plusDays(90), false));
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, valido());
    }

    @Test
    void rejeitaCertificadoDeOutroCnpj() {
        certificadoDoTenant(certificado("99888777000166", OffsetDateTime.now().plusDays(90), true));
        assertStatus(HttpStatus.BAD_REQUEST, valido());
    }

    // ---- helpers ----

    private void assertStatus(HttpStatus esperado, DocumentoFiscalRequestDTO request) {
        BusinessException e = assertThrows(BusinessException.class, () -> guarda.validar(TENANT_ID, request));
        assertEquals(esperado, e.getStatus());
        assertFalse(e.getMessage().isBlank());
    }

    private void certificadoDoTenant(CertificadoDigital certificado) {
        lenient().when(certificadoRepository.findByTenantIdAndEmitenteId(eq(TENANT_ID), any()))
                .thenReturn(Optional.of(certificado));
    }

    private static CertificadoDigital certificado(String cnpj, OffsetDateTime validoAte, boolean ativo) {
        CertificadoDigital c = new CertificadoDigital();
        c.setCnpjSubject(cnpj);
        c.setCertificadoValidoAte(validoAte);
        c.setAtivo(ativo);
        return c;
    }

    private static EnderecoDTO endereco(String uf) {
        return new EnderecoDTO("Rua A", "10", null, "Centro", "3304557", "Rio de Janeiro", uf, "20040020", null);
    }

    private static EmitenteDTO emitente(String crt, String ie) {
        return new EmitenteDTO(CNPJ, "Empresa Teste Ltda", null, ie, crt, endereco("RJ"));
    }

    private static DestinatarioDTO destinatario(String indicadorIe, String uf) {
        return new DestinatarioDTO("12345678909", "Cliente Teste", indicadorIe, null, null, endereco(uf));
    }

    /** Item de Regime Normal: ICMS 00, PIS/COFINS presentes, sem ST/IPI/FCP/DIFAL. */
    private static SnapshotFiscalItemDTO fiscalValido() {
        return fiscal("00", null, null, null, "01");
    }

    /** {@code cstPisCofins} nulo = item sem PIS/COFINS. */
    private static SnapshotFiscalItemDTO fiscal(String cstIcms, BigDecimal ipi, BigDecimal fcp, BigDecimal difal,
                                                String cstPisCofins) {
        BigDecimal valorPisCofins = cstPisCofins == null ? null : BigDecimal.ONE;
        return new SnapshotFiscalItemDTO("5102", cstIcms, "000", "000001",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                cstPisCofins, null, null, valorPisCofins,
                cstPisCofins, null, null, valorPisCofins,
                null, ipi, null, fcp, difal, null);
    }

    private static DocumentoFiscalRequestDTO valido() {
        return montar("NFE", "HOMOLOGACAO", emitente("3", "123456789"), destinatario("1", "RJ"), 0, CEM, fiscalValido());
    }

    private static DocumentoFiscalRequestDTO comFiscal(SnapshotFiscalItemDTO fiscal) {
        return montar("NFE", "HOMOLOGACAO", emitente("3", "123456789"), destinatario("1", "RJ"), 0, CEM, fiscal);
    }

    private static DocumentoFiscalRequestDTO montar(String documento, String ambiente, EmitenteDTO emitente,
                                                    DestinatarioDTO destinatario, int indicadorFinal,
                                                    BigDecimal valorTotal, SnapshotFiscalItemDTO fiscal) {
        ItemDocumentoDTO item = new ItemDocumentoDTO("P1", "Produto teste", "12345678", "0", "UN",
                BigDecimal.ONE, CEM, CEM, fiscal);
        return new DocumentoFiscalRequestDTO(EMITENTE_ID, documento, "55", "1", ambiente, "Venda de mercadoria",
                1, indicadorFinal, 1, emitente, destinatario, valorTotal, List.of(item));
    }
}
