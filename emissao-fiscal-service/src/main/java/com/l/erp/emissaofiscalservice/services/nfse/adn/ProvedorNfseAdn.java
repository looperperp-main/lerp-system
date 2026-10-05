package com.l.erp.emissaofiscalservice.services.nfse.adn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.ConfiguracaoNfseRepository;
import com.l.erp.emissaofiscalservice.services.assinatura.XmlSignatureService;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse.Situacao;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Adaptador do ADN / Sefin Nacional NFS-e (REST/JSON, TLS mútuo com o A1). Único lugar que conhece o
 * formato da DPS, o GZip+Base64 e as URLs do ADN. Emissão e cancelamento são <b>síncronos</b> no
 * ADN: o desfecho vem na própria resposta. Depois de falha de comunicação o chamador usa
 * {@link #consultar} (que devolve {@code NAO_ENCONTRADA} quando é seguro reenviar) — nunca reenvia às cegas.
 *
 * <p>Ainda não confirmado em homologação: o corpo dos erros 400, o formato de {@code GET /dps/{id}} e
 * se emitir exige convênio do município no ambiente de produção restrita.</p>
 */
@Service
public class ProvedorNfseAdn implements ProvedorNfse {

    // ponytail: fuso fixo de São Paulo (Arcos/MG); o do município do prestador entra com o cadastro de estabelecimento
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ConfiguracaoNfseRepository configuracaoRepository;
    private final AdnXmlBuilder xmlBuilder;
    private final XmlSignatureService assinatura;
    private final NfseRestClient restClient;
    private final ObjectMapper objectMapper;
    private final String urlHomologacao;
    private final String urlProducao;

    public ProvedorNfseAdn(ConfiguracaoNfseRepository configuracaoRepository, AdnXmlBuilder xmlBuilder,
                           XmlSignatureService assinatura, NfseRestClient restClient, ObjectMapper objectMapper,
                           @Value("${emissao.nfse.adn.sefin-url-homologacao}") String urlHomologacao,
                           @Value("${emissao.nfse.adn.sefin-url-producao}") String urlProducao) {
        this.configuracaoRepository = configuracaoRepository;
        this.xmlBuilder = xmlBuilder;
        this.assinatura = assinatura;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.urlHomologacao = urlHomologacao;
        this.urlProducao = urlProducao;
    }

    @Override
    public CodigoProvedorNfse codigo() {
        return CodigoProvedorNfse.ADN;
    }

    @Override
    public ResultadoNfse emitir(DocumentoFiscal documento, SessaoCertificado sessao) {
        NfseRequestDTO pedido = lerPedido(documento);
        String municipio = municipioEmissor(documento);
        String idDps = AdnXmlBuilder.idDps(municipio, pedido.prestador().cnpj(), pedido.serie(), documento.getNumero());

        String dps = xmlBuilder.montarDps(pedido, municipio, documento.getNumero(), OffsetDateTime.now(FUSO));
        String assinado = XmlSignatureService.serializar(
                assinatura.assinar(XmlSignatureService.parse(dps), idDps, sessao.keyStore(), sessao.senha()));

        var resposta = chamar("POST", documento, "/nfse", sessao,
                objectMapper.writeValueAsString(Map.of("dpsXmlGZipB64", GzipBase64.compactar(assinado))));
        return switch (resposta.status()) {
            case 201 -> new ResultadoNfse(Situacao.AUTORIZADA, ler(resposta.corpo()).chaveAcesso(), null, null);
            case 400 -> new ResultadoNfse(Situacao.REJEITADA, null, null, truncar(resposta.corpo()));
            default -> falha(resposta.status());
        };
    }

    @Override
    public ResultadoNfse consultar(DocumentoFiscal documento, SessaoCertificado sessao) {
        String chave = documento.getChaveAcesso();
        if (chave == null) {
            // sem chave (ex. timeout no envio): pergunta pelo identificador da DPS, que é calculável
            NfseRequestDTO pedido = lerPedido(documento);
            String idDps = AdnXmlBuilder.idDps(municipioEmissor(documento), pedido.prestador().cnpj(),
                    pedido.serie(), documento.getNumero());
            var porDps = chamar("GET", documento, "/dps/" + idDps, sessao, null);
            if (porDps.status() == 404) {
                return new ResultadoNfse(Situacao.NAO_ENCONTRADA, null, null, null);
            }
            if (porDps.status() != 200) {
                return falha(porDps.status());
            }
            chave = ler(porDps.corpo()).chaveAcesso();
        }
        var resposta = chamar("GET", documento, "/nfse/" + chave, sessao, null);
        return switch (resposta.status()) {
            case 200 -> new ResultadoNfse(Situacao.AUTORIZADA, chave, null, null);
            case 404 -> new ResultadoNfse(Situacao.NAO_ENCONTRADA, chave, null, null);
            default -> falha(resposta.status());
        };
    }

    @Override
    public ResultadoNfse cancelar(DocumentoFiscal documento, SessaoCertificado sessao, String motivo) {
        if (documento.getChaveAcesso() == null) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_SEM_CHAVE, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (motivo == null || motivo.length() < Constants.NFSE_MOTIVO_CANCELAMENTO_MIN
                || motivo.length() > Constants.NFSE_MOTIVO_CANCELAMENTO_MAX) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_MOTIVO_CANCELAMENTO, HttpStatus.BAD_REQUEST);
        }
        String chave = documento.getChaveAcesso();
        String pedidoXml = xmlBuilder.montarCancelamento(documento.getAmbiente(),
                lerPedido(documento).prestador().cnpj(), chave, motivo, OffsetDateTime.now(FUSO));
        String assinado = XmlSignatureService.serializar(assinatura.assinar(
                XmlSignatureService.parse(pedidoXml), AdnXmlBuilder.idPedidoCancelamento(chave),
                sessao.keyStore(), sessao.senha()));

        var resposta = chamar("POST", documento, "/nfse/" + chave + "/eventos", sessao,
                objectMapper.writeValueAsString(Map.of("pedidoRegistroEventoXmlGZipB64", GzipBase64.compactar(assinado))));
        return switch (resposta.status()) {
            case 201 -> new ResultadoNfse(Situacao.CANCELADA, chave, null, null);
            case 400 -> new ResultadoNfse(Situacao.REJEITADA, chave, null, truncar(resposta.corpo()));
            default -> falha(resposta.status());
        };
    }

    @Override
    public byte[] representacaoGrafica(DocumentoFiscal documento, SessaoCertificado sessao) {
        // o serviço de DANFSe do ADN estava fora do ar em 02/10/2026 e ainda não temos o contrato dele
        throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_DANFSE_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ---- infra ----

    private NfseRestClient.Resposta chamar(String metodo, DocumentoFiscal documento, String caminho,
                                           SessaoCertificado sessao, String corpo) {
        var resposta = restClient.enviar(metodo, URI.create(urlBase(documento) + caminho), corpo,
                sessao.keyStore(), sessao.senha());
        if (resposta.status() == 401 || resposta.status() == 403) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_CERTIFICADO_RECUSADO.formatted(resposta.status()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return resposta;
    }

    private String urlBase(DocumentoFiscal documento) {
        boolean producao = AmbienteEmissao.PRODUCAO.name().equalsIgnoreCase(documento.getAmbiente());
        return producao ? urlProducao : urlHomologacao;
    }

    private String municipioEmissor(DocumentoFiscal documento) {
        return configuracaoRepository.findByTenantIdAndEmitenteId(documento.getTenantId(), documento.getEmitenteId())
                .orElseThrow(() -> new BusinessException(
                        Constants.EMISSAO_ERRO_NFSE_SEM_CONFIGURACAO, HttpStatus.UNPROCESSABLE_ENTITY))
                .getMunicipioIbge();
    }

    private NfseRequestDTO lerPedido(DocumentoFiscal documento) {
        return objectMapper.readValue(documento.getPayloadRecebido(), NfseRequestDTO.class);
    }

    private Corpo ler(String json) {
        try {
            return objectMapper.readValue(json, Corpo.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Resposta do ADN ilegível — " + e.getMessage(), e);
        }
    }

    /** Falha que não é desfecho de negócio: o chamador conta tentativa, espera e consulta antes de reenviar. */
    private static ResultadoNfse falha(int status) {
        throw new WebServiceComunicacaoException("ADN respondeu HTTP " + status);
    }

    private static String truncar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= Constants.NFSE_MENSAGEM_MAX ? texto : texto.substring(0, Constants.NFSE_MENSAGEM_MAX);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Corpo(String chaveAcesso) {
    }
}
