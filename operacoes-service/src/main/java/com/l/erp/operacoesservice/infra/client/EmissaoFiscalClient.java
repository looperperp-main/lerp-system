package com.l.erp.operacoesservice.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.common.util.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Client HTTP pro emissao-fiscal-service via Eureka: {@code POST /emissao/documentos} responde 202 com o
 * id do documento (a assinatura e a transmissão à SEFAZ são assíncronas, lado de lá). O
 * {@code Idempotency-Key} garante que reenviar o mesmo pedido não cria um segundo documento.
 *
 * <p>Os records do payload espelham o contrato do emissao-fiscal-service ({@code DocumentoFiscalRequestDTO}
 * e filhos) — o serviço é vendável separadamente, então o contrato é copiado, nunca compartilhado por classe.</p>
 */
@Component
public class EmissaoFiscalClient {

    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient restClient;

    @Value("${internal.gateway.secret}")
    private String internalSecret;

    public EmissaoFiscalClient(@LoadBalanced RestClient.Builder restClientBuilder,
                               @Value("${emissao-fiscal-service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /** @return id do documento criado na emissão. */
    public UUID criarDocumento(DocumentoRequest request, String idempotencyKey, Long tenantId, UUID userId) {
        DocumentoResposta resposta = restClient.post()
                .uri("/emissao/documentos")
                .headers(headers -> {
                    headers.add(Constants.HEADER_INTERNAL_SECRET, internalSecret);
                    headers.add(Constants.HEADER_TENANT_ID, String.valueOf(tenantId));
                    headers.add(Constants.HEADER_USER_ID, userId.toString());
                    headers.add(HEADER_IDEMPOTENCY_KEY, idempotencyKey);
                })
                .body(request)
                .retrieve()
                .body(DocumentoResposta.class);
        return resposta.id();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DocumentoResposta(UUID id) {
    }

    public record Endereco(String logradouro, String numero, String complemento, String bairro,
                           String codigoMunicipio, String municipio, String uf, String cep, String telefone) {
    }

    public record Emitente(String cnpj, String razaoSocial, String nomeFantasia, String inscricaoEstadual,
                           String crt, Endereco endereco) {
    }

    public record Destinatario(String documento, String nome, String indicadorIe, String inscricaoEstadual,
                               String email, Endereco endereco) {
    }

    public record SnapshotFiscal(String cfop, String cstIcms, String cstIbsCbs, String cClassTrib,
                                 BigDecimal baseCalculoIcms, BigDecimal percentualIcms, BigDecimal valorIcms,
                                 BigDecimal percentualReducaoBaseIcms, String modalidadeBaseCalculoIcms,
                                 BigDecimal baseCalculoIbsCbs, BigDecimal percentualIbsUf,
                                 BigDecimal percentualIbsMunicipal, BigDecimal percentualCbs,
                                 BigDecimal percentualReducaoAplicado, BigDecimal valorIbsEstadual,
                                 BigDecimal valorIbsMunicipal, BigDecimal valorCbs,
                                 String cstPis, BigDecimal baseCalculoPis, BigDecimal percentualPis, BigDecimal valorPis,
                                 String cstCofins, BigDecimal baseCalculoCofins, BigDecimal percentualCofins,
                                 BigDecimal valorCofins,
                                 BigDecimal valorIcmsSt, BigDecimal valorIpi, BigDecimal percentualFcp,
                                 BigDecimal valorFcp, BigDecimal valorIcmsUfDestino, BigDecimal valorFcpUfDestino) {
    }

    public record Item(String codigo, String descricao, String ncm, String origem, String unidadeComercial,
                       BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal, SnapshotFiscal fiscal) {
    }

    public record DocumentoRequest(UUID emitenteId, String documento, String modelo, String serie, String ambiente,
                                   String naturezaOperacao, Integer tipoOperacao, Integer indicadorFinal,
                                   Integer indicadorPresenca, Emitente emitente, Destinatario destinatario,
                                   BigDecimal valorTotal, List<Item> itens) {
    }
}
