package com.l.erp.operacoesservice.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Client HTTP pro fiscal-service via Eureka (D4, spec/o2c-vendas.md §8): calcula IBS/CBS/IS/ISS
 * de saída por item do pedido no momento do faturamento (POST /fiscal/calcular).
 *
 * ponytail: cfop/regimeEmpresa/tipoDocumento vêm de defaults (Constants.PEDIDO_FISCAL_CFOP_*,
 * Constants.REGIME_LUCRO_PRESUMIDO) — Tenant ainda não modela regime tributário real, e
 * ufOrigem fica sempre null (não há estabelecimento emitente modelado — depende do modelo de
 * Estabelecimento, spec/estabelecimentos-filiais.md). cClassTrib (Produto) e UF/IBGE de destino
 * (Endereco do cliente) já vêm de dado real desde P1/P2.
 */
@Component
public class FiscalServiceClient {

    private final RestClient restClient;

    public FiscalServiceClient(RestClient.Builder restClientBuilder,
                                @Value("${fiscal-service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public ResultadoFiscalItem calcularItem(PedidoItem item, CadastroServiceClient.ProdutoRef produto,
                                             LocalDate dataCompetencia, Long tenantId,
                                             CadastroServiceClient.EnderecoFiscalRef endereco) {
        boolean servico = item.getTipoItem() == TipoItemPedido.SERVICO;
        String ibge = endereco != null ? endereco.ibgeCodigo() : null;
        String uf = endereco != null ? endereco.uf() : null;
        MotorFiscalRequestLocal req = new MotorFiscalRequestLocal(
                servico ? Constants.PEDIDO_FISCAL_CFOP_SERVICO_DEFAULT : Constants.PEDIDO_FISCAL_CFOP_MERCADORIA_DEFAULT,
                servico ? null : produto.ncm(),
                servico ? produto.codigoServico() : null,
                servico ? produto.classTrib() : null,
                // ponytail: local da prestação = endereço do cliente (não há campo dedicado de
                // "local da prestação" no pedido); sobe pra dado real se serviço prestado alhures.
                servico ? null : ibge,
                servico ? ibge : null,
                item.getValorTotal(),
                dataCompetencia,
                Constants.REGIME_LUCRO_PRESUMIDO,
                servico ? "NFSe" : "NFe",
                uf);
        try {
            OperacaoFiscalResultado resultado = restClient.post()
                    .uri("/fiscal/calcular")
                    .headers(headers -> headers.add(Constants.HEADER_TENANT_ID, String.valueOf(tenantId)))
                    .body(req)
                    .retrieve()
                    .body(OperacaoFiscalResultado.class);
            return ResultadoFiscalItem.from(resultado);
        } catch (HttpClientErrorException e) {
            throw new BusinessException(
                    String.format(Constants.PEDIDO_FISCAL_CALCULO_REJEITADO, item.getProdutoId(), e.getStatusText()),
                    HttpStatus.BAD_REQUEST);
        } catch (HttpServerErrorException e) {
            throw new BusinessException(Constants.FISCAL_SERVICE_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private record MotorFiscalRequestLocal(String cfop, String ncm, String codigoServico, String cClassTrib,
                                            String ibgeDestino, String ibgeLocalPrestacao,
                                            BigDecimal valorOperacao, LocalDate dataCompetencia,
                                            String regimeEmpresa, String tipoDocumento, String ufDestino) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OperacaoFiscalResultado(BigDecimal valorIbs, BigDecimal valorCbs, BigDecimal valorIs,
                                            BigDecimal valorIss, BigDecimal valorIssRetido, BigDecimal valorIrrf,
                                            BigDecimal valorCsrf, BigDecimal valorInss) {
    }

    public record ResultadoFiscalItem(BigDecimal valorIbs, BigDecimal valorCbs, BigDecimal valorIs,
                                       BigDecimal valorIss, BigDecimal valorRetencoes) {
        private static BigDecimal ou0(BigDecimal v) {
            return v != null ? v : BigDecimal.ZERO;
        }

        static ResultadoFiscalItem from(OperacaoFiscalResultado r) {
            if (r == null) {
                return new ResultadoFiscalItem(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            BigDecimal retencoes = ou0(r.valorIssRetido()).add(ou0(r.valorIrrf())).add(ou0(r.valorCsrf())).add(ou0(r.valorInss()));
            return new ResultadoFiscalItem(ou0(r.valorIbs()), ou0(r.valorCbs()), ou0(r.valorIs()), ou0(r.valorIss()), retencoes);
        }
    }
}
