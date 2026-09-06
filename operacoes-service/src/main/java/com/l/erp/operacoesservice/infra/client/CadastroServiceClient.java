package com.l.erp.operacoesservice.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.services.vendas.PedidoService.ParcelaDefinicao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
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

    public CadastroServiceClient(@LoadBalanced RestClient.Builder restClientBuilder,
                                  @Value("${cadastro-service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public BigDecimal buscarLimiteCredito(UUID clienteId, Long tenantId, UUID userId) {
        ClienteRef cliente = buscarCliente(clienteId, tenantId, userId);
        return cliente != null && cliente.limiteCredito() != null ? cliente.limiteCredito() : BigDecimal.ZERO;
    }

    // Fase 5: cliente_pessoa_id do payload do evento venda.pedido.faturado (spec/o2c-vendas.md §8).
    public UUID buscarClientePessoaId(UUID clienteId, Long tenantId, UUID userId) {
        ClienteRef cliente = buscarCliente(clienteId, tenantId, userId);
        return cliente != null ? cliente.pessoaId() : null;
    }

    private ClienteRef buscarCliente(UUID clienteId, Long tenantId, UUID userId) {
        try {
            return restClient.get()
                    .uri("/api/v1/clientes/{id}", clienteId)
                    .headers(headers -> headersInternos(headers, tenantId, userId))
                    .retrieve()
                    .body(ClienteRef.class);
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

    // Motor de preço (spec/motor-resolucao-preco.md) — cascata CLIENTE→GRUPO→PADRAO. clienteId nulo
    // é válido (pedido sem cliente ainda não deveria chegar aqui, mas a cascata cai direto pro PADRAO).
    public PrecoResolvidoRef resolverPreco(UUID produtoId, UUID clienteId, Long tenantId, UUID userId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        var uri = uriBuilder.path("/api/v1/precos/resolver").queryParam("produtoId", produtoId);
                        if (clienteId != null) {
                            uri = uri.queryParam("clienteId", clienteId);
                        }
                        return uri.build();
                    })
                    .headers(headers -> headersInternos(headers, tenantId, userId))
                    .retrieve()
                    .body(PrecoResolvidoRef.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrecoResolvidoRef(UUID tabelaPrecoId, String origem, BigDecimal preco) {
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
    private record ClienteRef(BigDecimal limiteCredito, UUID pessoaId) {
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

    // Público: PedidoController usa o tipo do produto pra montar o item do pedido, e o nome pra
    // mensagem de erro de produto inativo (em vez do UUID cru, ilegível pro usuário).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProdutoRef(String tipo, String codigoServico, Boolean ativo, String ncm, String classTrib, String nome) {
    }

    // P2 (spec/o2c-vendas.md, gaps do D4) — UF/IBGE do cliente pro MotorFiscalRequest.
    public EnderecoFiscalRef buscarEnderecoFiscal(UUID pessoaId, Long tenantId, UUID userId) {
        try {
            EnderecoEnvelope envelope = restClient.get()
                    .uri("/api/v1/pessoas/{pessoaId}/enderecos", pessoaId)
                    .headers(headers -> headersInternos(headers, tenantId, userId))
                    .retrieve()
                    .body(EnderecoEnvelope.class);
            List<EnderecoRef> enderecos = envelope != null && envelope._embedded() != null
                    ? envelope._embedded().enderecos() : List.of();
            // ponytail: cadastro-service não tem endpoint dedicado de endereço fiscal — prioriza
            // tipo=FISCAL, senão o principal, senão o primeiro da lista. Sobe pra endpoint dedicado
            // (ou pro modelo de Estabelecimento, spec/estabelecimentos-filiais.md) se isso não bastar.
            return enderecos.stream()
                    .filter(e -> "FISCAL".equals(e.tipo()))
                    .findFirst()
                    .or(() -> enderecos.stream().filter(e -> Boolean.TRUE.equals(e.principal())).findFirst())
                    .or(() -> enderecos.stream().findFirst())
                    .map(e -> new EnderecoFiscalRef(e.uf(), e.ibgeCodigo()))
                    .orElse(null);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnderecoEnvelope(EnderecoEmbedded _embedded) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnderecoEmbedded(List<EnderecoRef> enderecos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnderecoRef(String tipo, String uf, String ibgeCodigo, Boolean principal) {
    }

    public record EnderecoFiscalRef(String uf, String ibgeCodigo) {
    }

    // Fase 6 (spec/estabelecimentos-filiais.md §6.1) — pessoaId do estabelecimento emitente do
    // tenant, usado pra buscar o endereço fiscal de origem (ufOrigem) no faturamento.
    public UUID buscarPessoaIdEstabelecimentoProprio(Long tenantId, UUID userId) {
        try {
            EstabelecimentoProprioRef proprio = restClient.get()
                    .uri("/api/v1/estabelecimentos/proprio")
                    .headers(headers -> headersInternos(headers, tenantId, userId))
                    .retrieve()
                    .body(EstabelecimentoProprioRef.class);
            return proprio != null ? proprio.pessoaId() : null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EstabelecimentoProprioRef(UUID pessoaId) {
    }
}
