package com.l.erp.operacoesservice.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.services.vendas.PedidoService.ParcelaDefinicao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Client HTTP pro cadastro-service via Eureka (RestClientConfig, spec/o2c-vendas.md §2/§6):
 * limite de crédito do cliente (usado em confirmar()) e parcelas da condição de pagamento
 * (usadas em faturar()). Repassa os mesmos headers internos que o gateway injeta.
 */
@Component
public class CadastroServiceClient {

    private final RestClient restClient;

    @Value("${internal.gateway.secret}")
    private String internalSecret;

    public CadastroServiceClient(RestClient.Builder restClientBuilder,
                                  @Value("${cadastro-service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public BigDecimal buscarLimiteCredito(UUID clienteId, Long tenantId, UUID userId) {
        try {
            ClienteRef cliente = restClient.get()
                    .uri("/api/v1/clientes/{id}", clienteId)
                    .headers(headers -> headersInternos(headers, tenantId, userId))
                    .retrieve()
                    .body(ClienteRef.class);
            return cliente != null && cliente.limiteCredito() != null ? cliente.limiteCredito() : BigDecimal.ZERO;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(Constants.CLIENTE_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
    }

    public List<ParcelaDefinicao> buscarParcelas(UUID condicaoPagamentoId, Long tenantId, UUID userId) {
        try {
            ParcelasEnvelope envelope = restClient.get()
                    .uri("/api/v1/cond-pagamentos/{id}/parcelas", condicaoPagamentoId)
                    .headers(headers -> headersInternos(headers, tenantId, userId))
                    .retrieve()
                    .body(ParcelasEnvelope.class);
            List<ParcelaRef> parcelas = envelope != null && envelope._embedded() != null
                    ? envelope._embedded().parcelas() : List.of();
            return parcelas.stream()
                    .map(p -> new ParcelaDefinicao(p.numeroParcela(), p.diasPrazo(), p.percentual(), p.formaPagamento()))
                    .toList();
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(Constants.COND_PAG_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
    }

    public ProdutoRef buscarProduto(UUID produtoId, Long tenantId, UUID userId) {
        try {
            return restClient.get()
                    .uri("/api/v1/produtos/{id}", produtoId)
                    .headers(headers -> headersInternos(headers, tenantId, userId))
                    .retrieve()
                    .body(ProdutoRef.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(String.format(Constants.PEDIDO_PRODUTO_NAO_ENCONTRADO, produtoId), HttpStatus.BAD_REQUEST);
        } catch (HttpServerErrorException e) {
            throw new BusinessException(Constants.CADASTRO_SERVICE_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void headersInternos(HttpHeaders headers, Long tenantId, UUID userId) {
        headers.add(Constants.HEADER_INTERNAL_SECRET, internalSecret);
        headers.add(Constants.HEADER_TENANT_ID, String.valueOf(tenantId));
        headers.add(Constants.HEADER_USER_ID, userId.toString());
    }

    // Records locais só com os campos que este client usa — @JsonIgnoreProperties porque a
    // resposta real do cadastro-service traz outros campos (id, createdAt, _links etc).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClienteRef(BigDecimal limiteCredito) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParcelasEnvelope(Embedded _embedded) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Embedded(List<ParcelaRef> parcelas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParcelaRef(Integer numeroParcela, Integer diasPrazo, BigDecimal percentual, String formaPagamento) {
    }

    // Público: PedidoController usa o tipo do produto pra montar o item do pedido.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProdutoRef(String tipo, String codigoServico, Boolean ativo) {
    }
}
