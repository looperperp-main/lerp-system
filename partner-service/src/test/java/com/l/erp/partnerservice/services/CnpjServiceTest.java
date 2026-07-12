package com.l.erp.partnerservice.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CnpjService monta seu próprio RestClient internamente (campo final, não injetado via
 * construtor) e consulta a API pública OpenCNPJ via rede real. Sem refatorar o serviço para
 * injetar o RestClient, não é possível mockar a chamada HTTP em um teste unitário puro — por
 * isso aqui cobrimos apenas a validação de formato do CNPJ (normalização + checagem de 14
 * caracteres), que é a única lógica de negócio executada antes da chamada de rede. O caminho
 * feliz (CNPJ válido → resposta da OpenCNPJ) fica descoberto; testá-lo exigiria extrair o
 * RestClient para o construtor (fora do escopo desta tarefa).
 */
class CnpjServiceTest {

    private final CnpjService cnpjService = new CnpjService();

    @ParameterizedTest
    @ValueSource(strings = {
            "123",                    // muito curto
            "1234567890123456",       // muito longo
            "12.345.678/0001",        // faltam dígitos após remover separadores
            ""                        // vazio
    })
    void shouldRejectCnpjWithInvalidLength(String cnpjInvalido) {
        assertThatThrownBy(() -> cnpjService.consultar(cnpjInvalido))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("14 caracteres");
    }

    @Test
    void shouldRejectFormattedCnpjWithWrongDigitCount() {
        // 13 dígitos numéricos formatados como CNPJ: após remover separadores sobra 13, não 14
        assertThatThrownBy(() -> cnpjService.consultar("12.345.678/001-90"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CNPJ deve conter 14 caracteres");
    }
}
