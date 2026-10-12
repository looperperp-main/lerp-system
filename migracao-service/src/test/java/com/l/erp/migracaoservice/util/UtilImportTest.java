package com.l.erp.migracaoservice.util;

import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilImportTest {

    @Test
    void cpfComDigitoCertoPassaEErradoOuRepetidoNao() {
        assertThat(DocumentoValidator.cpfValido("52998224725")).isTrue();
        assertThat(DocumentoValidator.cpfValido("11144477735")).isTrue();
        assertThat(DocumentoValidator.cpfValido("52998224724")).isFalse();
        assertThat(DocumentoValidator.cpfValido("11111111111")).isFalse();
        assertThat(DocumentoValidator.cpfValido("123")).isFalse();
        assertThat(DocumentoValidator.cpfValido(null)).isFalse();
    }

    @Test
    void cnpjNumericoEAlfanumericoComDvCerto() {
        assertThat(DocumentoValidator.cnpjValido("11222333000181")).isTrue();
        assertThat(DocumentoValidator.cnpjValido("12ABC34501DE35")).isTrue();   // exemplo da NT 2026.004
        assertThat(DocumentoValidator.cnpjValido("12ABC34501DE36")).isFalse();
        assertThat(DocumentoValidator.cnpjValido("00000000000000")).isFalse();
        assertThat(DocumentoValidator.cnpjValido("1122233300018")).isFalse();
        assertThat(DocumentoValidator.cnpjValido(null)).isFalse();
    }

    @Test
    void normalizarTiraMascaraEPoeEmMaiusculas() {
        assertThat(DocumentoValidator.normalizar("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(DocumentoValidator.normalizar(" 12.abc.345/01de-35 ")).isEqualTo("12ABC34501DE35");
        assertThat(DocumentoValidator.normalizar(null)).isEmpty();
    }

    @Test
    void formatarAplicaAMascaraDoCadastroEDeixaOutrosTamanhosComoVieram() {
        assertThat(DocumentoValidator.formatar("52998224725")).isEqualTo("529.982.247-25");
        assertThat(DocumentoValidator.formatar("11222333000181")).isEqualTo("11.222.333/0001-81");
        assertThat(DocumentoValidator.formatar("12ABC34501DE35")).isEqualTo("12.ABC.345/01DE-35");   // alfanumérico
        assertThat(DocumentoValidator.formatar("123")).isEqualTo("123");
    }

    @Test
    void textoEmBrancoViraNuloEOMaisVemSemEspacos() {
        assertThat(ValorImport.texto(null)).isNull();
        assertThat(ValorImport.texto("   ")).isNull();
        assertThat(ValorImport.texto("  UN ")).isEqualTo("UN");
    }

    @Test
    void dataAceitaIsoEBrasileiroERecusaInexistente() {
        assertThat(ValorImport.data("2026-10-12")).contains(LocalDate.of(2026, 10, 12));
        assertThat(ValorImport.data("12/10/2026")).contains(LocalDate.of(2026, 10, 12));
        assertThat(ValorImport.data("31/02/2026")).isEmpty();     // STRICT: não vira 28/02 em silêncio
        assertThat(ValorImport.data("2026-02-31")).isEmpty();
        assertThat(ValorImport.data("amanhã")).isEmpty();
    }

    @Test
    void booleanoAceitaSimNaoComEscritaVariada() {
        assertThat(ValorImport.booleano("SIM")).contains(true);
        assertThat(ValorImport.booleano(" s ")).contains(true);
        assertThat(ValorImport.booleano("true")).contains(true);
        assertThat(ValorImport.booleano("não")).contains(false);
        assertThat(ValorImport.booleano("0")).contains(false);
        assertThat(ValorImport.booleano("talvez")).isEmpty();
    }

    @Test
    void decimalAceitaVirgulaOuPonto() {
        assertThat(ValorImport.decimal("10,5")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("10.5"));
        assertThat(ValorImport.decimal("10.5")).isPresent();
        assertThat(ValorImport.decimal("abc")).isEmpty();
    }

    @Test
    void entidadeNormalizaEAceitaSoAsDoMvp() {
        assertThat(ImportEntidade.normalizar(" customer ")).isEqualTo("CUSTOMER");
        assertThat(ImportEntidade.normalizar("PRODUCT")).isEqualTo("PRODUCT");
        assertThatThrownBy(() -> ImportEntidade.normalizar("CHART_OF_ACCOUNTS"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> ImportEntidade.normalizar(null)).isInstanceOf(BusinessException.class);
    }
}
