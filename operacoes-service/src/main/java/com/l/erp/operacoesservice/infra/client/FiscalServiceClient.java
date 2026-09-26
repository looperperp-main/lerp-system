package com.l.erp.operacoesservice.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Client HTTP pro fiscal-service via Eureka (D4, spec/modulos/o2c-vendas/o2c-vendas.md §8): calcula IBS/CBS/IS/ISS
 * de saída por item do pedido no momento do faturamento (POST /fiscal/calcular).
 *
 * Mercadoria não manda cfop pronto: manda naturezaOperacao=VENDA e deixa o fiscal-service resolver
 * o CFOP real por UF (fiscal.cfop_regra, Etapa 0 — spec/modulos/emissao-fiscal/emissao-fiscal.md
 * §11), corrigindo o '5102' fixo que antes saía errado em toda venda interestadual. Serviço
 * continua com cfop fixo (Constants.PEDIDO_FISCAL_CFOP_SERVICO_DEFAULT): NFS-e não tem CFOP no
 * XML, o valor só serve de sinal interno de SAÍDA para o motor — não precisa de resolução real.
 *
 * ponytail: regimeEmpresa/tipoDocumento seguem de default (Constants.REGIME_LUCRO_PRESUMIDO) —
 * Tenant ainda não modela regime tributário real no pedido (Estabelecimento.crt existe desde
 * d313921, falta o fio até aqui). ufOrigem (Fase 6, spec/estabelecimentos-filiais.md §6.1) já vem
 * do endereço fiscal do estabelecimento "próprio" do tenant. cClassTrib (Produto) e UF/IBGE de
 * destino (Endereco do cliente) já vêm de dado real desde P1/P2.
 *
 * <p>indFinal/indIEDest (issue #103, DIFAL/FCP): o pedido/Cliente ainda não modela se o
 * destinatário é consumidor final não contribuinte — manda o par fixo "0"/"1" (não é consumidor
 * final / contribuinte), que preserva o comportamento de antes do #103 (só ICMS interestadual,
 * sem DIFAL) sem travar com o 400 novo de indicador obrigatório. Upgrade: subir dado real do
 * cliente quando o cadastro de Cliente ganhar esse campo.
 */
@Component
public class FiscalServiceClient {

    private final RestClient restClient;

    public FiscalServiceClient(@LoadBalanced RestClient.Builder restClientBuilder,
                                @Value("${fiscal-service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public ResultadoFiscalItem calcularItem(PedidoItem item, CadastroServiceClient.ProdutoRef produto,
                                             LocalDate dataCompetencia, Long tenantId,
                                             CadastroServiceClient.EnderecoFiscalRef endereco, String ufOrigem) {
        boolean servico = item.getTipoItem() == TipoItemPedido.SERVICO;
        String ibge = endereco != null ? endereco.ibgeCodigo() : null;
        String uf = endereco != null ? endereco.uf() : null;
        MotorFiscalRequestLocal req = new MotorFiscalRequestLocal(
                servico ? Constants.PEDIDO_FISCAL_CFOP_SERVICO_DEFAULT : null,
                servico ? null : Constants.NATUREZA_OPERACAO_VENDA,
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
                uf,
                ufOrigem,
                Constants.FISCAL_IND_FINAL_NORMAL,
                Constants.FISCAL_IND_IE_DEST_CONTRIBUINTE);
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

    private record MotorFiscalRequestLocal(String cfop, String naturezaOperacao, String ncm, String codigoServico,
                                            String cClassTrib, String ibgeDestino, String ibgeLocalPrestacao,
                                            BigDecimal valorOperacao, LocalDate dataCompetencia,
                                            String regimeEmpresa, String tipoDocumento, String ufDestino,
                                            String ufOrigem, String indFinal, String indIEDest) {
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
