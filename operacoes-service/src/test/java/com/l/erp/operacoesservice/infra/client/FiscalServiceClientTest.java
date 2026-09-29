package com.l.erp.operacoesservice.infra.client;

import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Regressão do gap fechado em spec/modulos/emissao-fiscal/emissao-fiscal.md §3 item 11: o record
 * local {@code OperacaoFiscalResultado} descartava silenciosamente ICMS/DIFAL/FCP porque só
 * declarava 8 campos de valor com {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
 */
class FiscalServiceClientTest {

    @Test
    void calcularItem_naoDescartaIcmsDifalFcpDaResposta() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FiscalServiceClient client = new FiscalServiceClient(builder, "http://fiscal-service");

        String json = """
                {
                  "valorIbs": 10.00,
                  "valorCbs": 5.00,
                  "valorIcms": 12.34,
                  "regimeAplicado": "PADRAO",
                  "cfop": "6102",
                  "cstIcms": "00",
                  "percentualFcp": 2.0000,
                  "valorFcp": 3.45,
                  "percentualIcmsInterestadual": 12.0000,
                  "valorIcmsUfDestino": 6.78,
                  "valorFcpUfDestino": 1.23,
                  "valorIcmsUfRemetente": 9.87,
                  "memoriaCalculo": ["passo 1", "passo 2"]
                }
                """;
        server.expect(requestTo("http://fiscal-service/fiscal/calcular"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        PedidoItem item = PedidoItem.builder().produtoId(UUID.randomUUID())
                .tipoItem(TipoItemPedido.MERCADORIA).valorTotal(new BigDecimal("100.00")).build();
        CadastroServiceClient.ProdutoRef produto =
                new CadastroServiceClient.ProdutoRef("MERCADORIA", null, true, "12345678", null, "Produto");
        CadastroServiceClient.EnderecoFiscalRef endereco = new CadastroServiceClient.EnderecoFiscalRef("SP", "3550308");

        FiscalServiceClient.ResultadoFiscalItem resultado =
                client.calcularItem(item, produto, LocalDate.now(), 1L, endereco, "RJ");

        assertThat(resultado.valorIcms()).isEqualByComparingTo("12.34");
        assertThat(resultado.regimeAplicado()).isEqualTo("PADRAO");
        assertThat(resultado.cfop()).isEqualTo("6102");
        assertThat(resultado.cstIcms()).isEqualTo("00");
        assertThat(resultado.percentualFcp()).isEqualByComparingTo("2.0000");
        assertThat(resultado.valorFcp()).isEqualByComparingTo("3.45");
        assertThat(resultado.percentualIcmsInterestadual()).isEqualByComparingTo("12.0000");
        assertThat(resultado.valorIcmsUfDestino()).isEqualByComparingTo("6.78");
        assertThat(resultado.valorFcpUfDestino()).isEqualByComparingTo("1.23");
        assertThat(resultado.valorIcmsUfRemetente()).isEqualByComparingTo("9.87");
        server.verify();
    }
}
