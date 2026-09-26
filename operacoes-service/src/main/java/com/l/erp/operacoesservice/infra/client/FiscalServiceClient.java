package com.l.erp.operacoesservice.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import lombok.Builder;
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

    /**
     * Espelha {@code OperacaoFiscalDTO} (fiscal-service) campo a campo — deixou de truncar em 8
     * valores (spec/modulos/emissao-fiscal/emissao-fiscal.md §3 item 11, opção b): é este resultado,
     * sem recálculo, que alimenta {@code PedidoItemFiscalSnapshot} no faturamento.
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)} cobre só {@code memoriaCalculo} (lista de
     * auditoria, não consumida pelo snapshot).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OperacaoFiscalResultado(BigDecimal baseCalculo, BigDecimal valorIs, BigDecimal valorIbsEstadual,
                                            BigDecimal valorIbsMunicipal, BigDecimal valorIbs, BigDecimal valorCbs,
                                            BigDecimal valorSplitIbs, BigDecimal valorSplitCbs, BigDecimal valorIcms,
                                            BigDecimal valorIss, BigDecimal valorIssRetido, BigDecimal valorIrrf,
                                            BigDecimal valorCsrf, BigDecimal valorInss, BigDecimal valorCreditoIbs,
                                            BigDecimal valorCreditoCbs, String regimeAplicado, String cClassTrib,
                                            BigDecimal percentualIbsUf, BigDecimal percentualIbsMunicipal,
                                            BigDecimal percentualCbs, BigDecimal percentualReducaoAplicado,
                                            String cst, String cstIcms, String csosn,
                                            BigDecimal percentualIcmsNominal, BigDecimal percentualReducaoBaseIcms,
                                            String modalidadeBaseCalculoIcms, BigDecimal percentualFcp,
                                            BigDecimal valorFcp, BigDecimal percentualIcmsInterestadual,
                                            BigDecimal baseCalculoUfDestino, BigDecimal baseCalculoFcpUfDestino,
                                            BigDecimal percentualIcmsUfDestino, BigDecimal percentualFcpUfDestino,
                                            BigDecimal percentualPartilhaDestino, BigDecimal valorIcmsUfDestino,
                                            BigDecimal valorFcpUfDestino, BigDecimal valorIcmsUfRemetente) {
    }

    @Builder
    public record ResultadoFiscalItem(BigDecimal baseCalculo, BigDecimal valorIs, BigDecimal valorIbsEstadual,
                                       BigDecimal valorIbsMunicipal, BigDecimal valorIbs, BigDecimal valorCbs,
                                       BigDecimal valorSplitIbs, BigDecimal valorSplitCbs, BigDecimal valorIcms,
                                       BigDecimal valorIss, BigDecimal valorIssRetido, BigDecimal valorIrrf,
                                       BigDecimal valorCsrf, BigDecimal valorInss, BigDecimal valorCreditoIbs,
                                       BigDecimal valorCreditoCbs, String regimeAplicado, String cClassTrib,
                                       BigDecimal percentualIbsUf, BigDecimal percentualIbsMunicipal,
                                       BigDecimal percentualCbs, BigDecimal percentualReducaoAplicado,
                                       String cst, String cstIcms, String csosn,
                                       BigDecimal percentualIcmsNominal, BigDecimal percentualReducaoBaseIcms,
                                       String modalidadeBaseCalculoIcms, BigDecimal percentualFcp,
                                       BigDecimal valorFcp, BigDecimal percentualIcmsInterestadual,
                                       BigDecimal baseCalculoUfDestino, BigDecimal baseCalculoFcpUfDestino,
                                       BigDecimal percentualIcmsUfDestino, BigDecimal percentualFcpUfDestino,
                                       BigDecimal percentualPartilhaDestino, BigDecimal valorIcmsUfDestino,
                                       BigDecimal valorFcpUfDestino, BigDecimal valorIcmsUfRemetente) {
        private static BigDecimal ou0(BigDecimal v) {
            return v != null ? v : BigDecimal.ZERO;
        }

        public BigDecimal valorIbs() {
            return ou0(valorIbs);
        }

        public BigDecimal valorCbs() {
            return ou0(valorCbs);
        }

        public BigDecimal valorIs() {
            return ou0(valorIs);
        }

        public BigDecimal valorIss() {
            return ou0(valorIss);
        }

        public BigDecimal valorRetencoes() {
            return ou0(valorIssRetido).add(ou0(valorIrrf)).add(ou0(valorCsrf)).add(ou0(valorInss));
        }

        static ResultadoFiscalItem from(OperacaoFiscalResultado r) {
            if (r == null) {
                return ResultadoFiscalItem.builder().build();
            }
            return ResultadoFiscalItem.builder()
                    .baseCalculo(r.baseCalculo()).valorIs(r.valorIs()).valorIbsEstadual(r.valorIbsEstadual())
                    .valorIbsMunicipal(r.valorIbsMunicipal()).valorIbs(r.valorIbs()).valorCbs(r.valorCbs())
                    .valorSplitIbs(r.valorSplitIbs()).valorSplitCbs(r.valorSplitCbs()).valorIcms(r.valorIcms())
                    .valorIss(r.valorIss()).valorIssRetido(r.valorIssRetido()).valorIrrf(r.valorIrrf())
                    .valorCsrf(r.valorCsrf()).valorInss(r.valorInss()).valorCreditoIbs(r.valorCreditoIbs())
                    .valorCreditoCbs(r.valorCreditoCbs()).regimeAplicado(r.regimeAplicado())
                    .cClassTrib(r.cClassTrib()).percentualIbsUf(r.percentualIbsUf())
                    .percentualIbsMunicipal(r.percentualIbsMunicipal()).percentualCbs(r.percentualCbs())
                    .percentualReducaoAplicado(r.percentualReducaoAplicado()).cst(r.cst()).cstIcms(r.cstIcms())
                    .csosn(r.csosn()).percentualIcmsNominal(r.percentualIcmsNominal())
                    .percentualReducaoBaseIcms(r.percentualReducaoBaseIcms())
                    .modalidadeBaseCalculoIcms(r.modalidadeBaseCalculoIcms()).percentualFcp(r.percentualFcp())
                    .valorFcp(r.valorFcp()).percentualIcmsInterestadual(r.percentualIcmsInterestadual())
                    .baseCalculoUfDestino(r.baseCalculoUfDestino()).baseCalculoFcpUfDestino(r.baseCalculoFcpUfDestino())
                    .percentualIcmsUfDestino(r.percentualIcmsUfDestino()).percentualFcpUfDestino(r.percentualFcpUfDestino())
                    .percentualPartilhaDestino(r.percentualPartilhaDestino()).valorIcmsUfDestino(r.valorIcmsUfDestino())
                    .valorFcpUfDestino(r.valorFcpUfDestino()).valorIcmsUfRemetente(r.valorIcmsUfRemetente())
                    .build();
        }
    }
}
