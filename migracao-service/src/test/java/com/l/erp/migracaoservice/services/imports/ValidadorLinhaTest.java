package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.domain.ErroCampo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorLinhaTest {

    private final ValidadorLinha validador = new ValidadorLinha();

    private Map<String, String> cliente(String tipo, String nome, String documento) {
        Map<String, String> linha = new HashMap<>();
        linha.put("tipo", tipo);
        linha.put("nome_razao", nome);
        linha.put("documento", documento);
        return linha;
    }

    private List<ErroCampo> validarCliente(Map<String, String> linha) {
        return validador.validar(CamposFixture.cliente(), linha);
    }

    private Map<String, String> produto(String sku, String nome, String tipo, String unidade) {
        Map<String, String> linha = new HashMap<>();
        linha.put("sku", sku);
        linha.put("nome", nome);
        linha.put("tipo", tipo);
        linha.put("unidade", unidade);
        return linha;
    }

    @Test
    void clientePessoaFisicaECnpjValidosNaoTemErro() {
        assertThat(validarCliente(cliente("PF", "Maria", "529.982.247-25"))).isEmpty();
        assertThat(validarCliente(cliente("pj", "Acme", "11.222.333/0001-81"))).isEmpty();
        assertThat(validarCliente(cliente("PJ", "Acme", "12.ABC.345/01DE-35"))).isEmpty();   // CNPJ alfanumérico
    }

    @Test
    void camposObrigatoriosVaziosGeramUmErroCadaUm() {
        var erros = validarCliente(new HashMap<>());

        assertThat(erros).extracting(ErroCampo::field).containsExactly("tipo", "nome_razao", "documento");
        assertThat(erros).extracting(ErroCampo::message).containsOnly(Constants.IMPORT_CAMPO_OBRIGATORIO);
    }

    @Test
    void documentoComDigitoErradoOuTamanhoErrado() {
        assertThat(validarCliente(cliente("PF", "Maria", "529.982.247-24")))
                .containsExactly(new ErroCampo("documento", Constants.IMPORT_DOCUMENTO_INVALIDO));
        assertThat(validarCliente(cliente("PF", "Maria", "123")))
                .containsExactly(new ErroCampo("documento", Constants.IMPORT_DOCUMENTO_INVALIDO));
    }

    @Test
    void documentoNaoCondizComOTipoDaLinha() {
        assertThat(validarCliente(cliente("PJ", "Acme", "529.982.247-25")))
                .containsExactly(new ErroCampo("documento", Constants.IMPORT_DOCUMENTO_TIPO));
        assertThat(validarCliente(cliente("PF", "Maria", "11.222.333/0001-81")))
                .containsExactly(new ErroCampo("documento", Constants.IMPORT_DOCUMENTO_TIPO));
    }

    @Test
    void tipoDePessoaForaDePfPj() {
        assertThat(validarCliente(cliente("XX", "Maria", "52998224725")))
                .contains(new ErroCampo("tipo", Constants.IMPORT_PF_PJ_INVALIDO));
    }

    @Test
    void tamanhoMaximoVemDoSchema() {
        var erros = validarCliente(cliente("PF", "x".repeat(201), "52998224725"));

        assertThat(erros).containsExactly(new ErroCampo("nome_razao", String.format(Constants.IMPORT_CAMPO_TAMANHO, 200)));
    }

    @Test
    void dataBooleanoEIndicadorDeIe() {
        Map<String, String> linha = cliente("PF", "Maria", "52998224725");
        linha.put("data_nascimento", "31/02/1990");
        linha.put("ativo", "talvez");
        linha.put("ind_ie_dest", "QUALQUER");

        assertThat(validarCliente(linha)).containsExactlyInAnyOrder(
                new ErroCampo("data_nascimento", Constants.IMPORT_DATA_INVALIDA),
                new ErroCampo("ativo", Constants.IMPORT_BOOLEAN_INVALIDO),
                new ErroCampo("ind_ie_dest", Constants.IMPORT_IND_IE_INVALIDO));

        linha.put("data_nascimento", "15/01/1990");
        linha.put("ativo", "não");
        linha.put("ind_ie_dest", "isento");
        assertThat(validarCliente(linha)).isEmpty();
    }

    @Test
    void produtoValidoAceitaNcmComPontoEServicoComCedilha() {
        Map<String, String> linha = produto("SKU-1", "Cabo", "SERVIÇO", "UN");
        linha.put("ncm", "8544.42.00");
        linha.put("ean", "7891000100103");
        linha.put("fator", "1,5");

        assertThat(validador.validar(CamposFixture.produto(), linha)).isEmpty();
    }

    @Test
    void produtoComNcmEanTipoEFatorInvalidos() {
        Map<String, String> linha = produto("SKU-1", "Cabo", "OUTRO", "UN");
        linha.put("ncm", "123");
        linha.put("ean", "12345");
        linha.put("fator", "abc");

        assertThat(validador.validar(CamposFixture.produto(), linha)).containsExactlyInAnyOrder(
                new ErroCampo("tipo", Constants.IMPORT_TIPO_PRODUTO_INVALIDO),
                new ErroCampo("ncm", Constants.IMPORT_NCM_INVALIDO),
                new ErroCampo("ean", Constants.IMPORT_EAN_INVALIDO),
                new ErroCampo("fator", Constants.IMPORT_DECIMAL_INVALIDO));
    }

    @Test
    void regraDesconhecidaNoSchemaNaoQuebraNemReprova() {
        var campo = new CampoCanonico("x", 1, "STRING", false, "REGRA_FUTURA", null, null);

        assertThat(validador.validar(List.of(campo), Map.of("x", "qualquer"))).isEmpty();
    }
}
