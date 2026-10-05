package com.l.erp.emissaofiscalservice.services.nfse.adn;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.ConfiguracaoNfseRepository;
import com.l.erp.emissaofiscalservice.services.assinatura.XmlSignatureService;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse.Situacao;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.SessaoCertificado;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.security.KeyStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Adaptador do ADN com um {@link NfseRestClient} falso: prova o que o serviço faz com cada status do
 * ADN (201/400/403/404/500/timeout) e, principalmente, que depois de falha ele consulta pelo
 * identificador da DPS antes de qualquer reenvio — sem tocar na rede.
 */
class ProvedorNfseAdnTest {

    private static final char[] SENHA = "teste123".toCharArray();
    private static final String MUNICIPIO = "3104007";
    private static final String CNPJ = "40152424000196";
    private static final String CHAVE = "31040072240152424000196000000000000126100123456789";
    private static final String ID_DPS = "DPS3104007240152424000196" + "00001" + "000000000000007";
    private static final UUID EMITENTE = UUID.randomUUID();

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Chamada> chamadas = new ArrayList<>();
    private Resposta proxima;
    private ProvedorNfseAdn provedor;
    private SessaoCertificado sessao;

    private record Chamada(String metodo, URI uri, String corpo) {
    }

    private sealed interface Resposta permits Status, Timeout {
    }

    private record Status(int codigo, String corpo) implements Resposta {
    }

    private record Timeout() implements Resposta {
    }

    @BeforeEach
    void preparar() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getResourceAsStream("/certificado-teste.p12")) {
            keyStore.load(is, SENHA);
        }
        sessao = new SessaoCertificado(keyStore, SENHA);

        ConfiguracaoNfseRepository configuracoes = mock(ConfiguracaoNfseRepository.class);
        ConfiguracaoNfse configuracao = new ConfiguracaoNfse();
        configuracao.setMunicipioIbge(MUNICIPIO);
        when(configuracoes.findByTenantIdAndEmitenteId(7L, EMITENTE)).thenReturn(Optional.of(configuracao));

        NfseRestClient falso = (metodo, uri, corpo, ks, senha) -> {
            chamadas.add(new Chamada(metodo, uri, corpo));
            return switch (proxima) {
                case Status s -> new NfseRestClient.Resposta(s.codigo(), s.corpo());
                case Timeout t -> throw new WebServiceComunicacaoException("timeout");
            };
        };
        provedor = new ProvedorNfseAdn(configuracoes, new AdnXmlBuilder(), new XmlSignatureService(), falso,
                json, "https://homologacao.exemplo/sefin", "https://producao.exemplo/sefin");
    }

    @Test
    void emitirComSucessoEnviaDpsAssinadaEmGzipEDevolveAChave() {
        proxima = new Status(201, "{\"chaveAcesso\":\"" + CHAVE + "\",\"idDps\":\"" + ID_DPS + "\"}");

        ResultadoNfse resultado = provedor.emitir(documento(null), sessao);

        assertEquals(Situacao.AUTORIZADA, resultado.situacao());
        assertEquals(CHAVE, resultado.chave());
        Chamada chamada = chamadas.getFirst();
        assertEquals("POST", chamada.metodo());
        assertEquals("https://homologacao.exemplo/sefin/nfse", chamada.uri().toString());
        String dps = GzipBase64.descompactar(campo(chamada.corpo(), "dpsXmlGZipB64"));
        assertTrue(dps.contains("<infDPS Id=\"" + ID_DPS + "\">"));
        assertTrue(dps.contains("Signature"), "a DPS tem que ir assinada");
    }

    @Test
    void emitirRejeitadoPeloAdnVirouRejeitadaComAMensagem() {
        proxima = new Status(400, "{\"erro\":[{\"codigo\":\"E0001\",\"descricao\":\"regra violada\"}]}");

        ResultadoNfse resultado = provedor.emitir(documento(null), sessao);

        assertEquals(Situacao.REJEITADA, resultado.situacao());
        assertTrue(resultado.mensagem().contains("regra violada"));
    }

    @Test
    void certificadoRecusadoVira422ComMensagemClara() {
        proxima = new Status(403, "{}");

        BusinessException e = assertThrows(BusinessException.class, () -> provedor.emitir(documento(null), sessao));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertTrue(e.getMessage().contains("certificado"));
    }

    @Test
    void erro500ETimeoutSaemComoFalhaDeComunicacaoParaOChamadorConsultarAntesDeReenviar() {
        proxima = new Status(500, "{}");
        assertThrows(WebServiceComunicacaoException.class, () -> provedor.emitir(documento(null), sessao));

        proxima = new Timeout();
        assertThrows(WebServiceComunicacaoException.class, () -> provedor.emitir(documento(null), sessao));
    }

    @Test
    void consultarSemChaveUsaOIdDaDpsENaoEncontradoLiberaOReenvio() {
        proxima = new Status(404, "{}");

        ResultadoNfse resultado = provedor.consultar(documento(null), sessao);

        assertEquals(Situacao.NAO_ENCONTRADA, resultado.situacao());
        assertNull(resultado.chave());
        assertEquals("GET", chamadas.getFirst().metodo());
        assertEquals("https://homologacao.exemplo/sefin/dps/" + ID_DPS, chamadas.getFirst().uri().toString());
    }

    @Test
    void consultarSemChaveQueAchaADpsBuscaANotaPelaChaveRecebida() {
        proxima = new Status(200, "{\"chaveAcesso\":\"" + CHAVE + "\"}");

        ResultadoNfse resultado = provedor.consultar(documento(null), sessao);

        assertEquals(Situacao.AUTORIZADA, resultado.situacao());
        assertEquals(CHAVE, resultado.chave());
        assertEquals(2, chamadas.size());
        assertEquals("https://homologacao.exemplo/sefin/nfse/" + CHAVE, chamadas.get(1).uri().toString());
    }

    @Test
    void cancelarEnviaOEventoAssinadoNaChaveECancelaNo201() {
        proxima = new Status(201, "{\"eventoXmlGZipB64\":\"x\"}");

        ResultadoNfse resultado = provedor.cancelar(documento(CHAVE), sessao, "Erro na emissao do valor do servico");

        assertEquals(Situacao.CANCELADA, resultado.situacao());
        Chamada chamada = chamadas.getFirst();
        assertEquals("https://homologacao.exemplo/sefin/nfse/" + CHAVE + "/eventos", chamada.uri().toString());
        String evento = GzipBase64.descompactar(campo(chamada.corpo(), "pedidoRegistroEventoXmlGZipB64"));
        assertTrue(evento.contains("<e101101>"));
        assertTrue(evento.contains("Signature"));
    }

    @Test
    void cancelarSemChaveOuComMotivoCurtoNemChamaOAdn() {
        assertThrows(BusinessException.class, () -> provedor.cancelar(documento(null), sessao, "Erro na emissao do valor"));
        assertThrows(BusinessException.class, () -> provedor.cancelar(documento(CHAVE), sessao, "curto"));
        assertTrue(chamadas.isEmpty());
    }

    @Test
    void danfseIndisponivelEAvisadoNaoSimulado() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> provedor.representacaoGrafica(documento(CHAVE), sessao));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }

    // ---- apoio ----

    @SuppressWarnings("unchecked")
    private String campo(String corpoJson, String nome) {
        return (String) json.readValue(corpoJson, Map.class).get(nome);
    }

    private DocumentoFiscal documento(String chave) {
        DocumentoFiscal documento = new DocumentoFiscal();
        documento.setTenantId(7L);
        documento.setEmitenteId(EMITENTE);
        documento.setAmbiente("HOMOLOGACAO");
        documento.setNumero(7);
        documento.setChaveAcesso(chave);
        documento.setPayloadRecebido(json.writeValueAsString(pedido()));
        return documento;
    }

    private static NfseRequestDTO pedido() {
        EnderecoDTO endereco = new EnderecoDTO("Rua das Flores", "100", null, "Centro", MUNICIPIO, "Arcos", "MG",
                "35588000", null);
        return new NfseRequestDTO(EMITENTE, "HOMOLOGACAO", "1", LocalDate.of(2026, 10, 3),
                new NfseRequestDTO.Prestador(CNPJ, null, "ARCOS TECNOLOGIA LTDA", endereco, null, "3", "1", "0"),
                null,
                new NfseRequestDTO.Servico("010101", null, "Desenvolvimento de software sob encomenda", MUNICIPIO),
                new NfseRequestDTO.Valores(new BigDecimal("100.00"), "1", null, "1"));
    }
}
