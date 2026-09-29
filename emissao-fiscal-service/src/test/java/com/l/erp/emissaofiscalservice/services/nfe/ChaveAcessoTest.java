package com.l.erp.emissaofiscalservice.services.nfe;

import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaveAcessoTest {

    /** Exemplo oficial da NT Conjunta 2025.001 (p. 5-6): 12ABC34501DE → DV 3 e 5. */
    @Test
    void dvBateComOExemploOficialDoCnpjAlfanumerico() {
        assertEquals(3, ChaveAcesso.calcularDv("12ABC34501DE"));
        assertEquals(5, ChaveAcesso.calcularDv("12ABC34501DE3"));
    }

    @Test
    void chaveTem44PosicoesEDvConfereComORecalculo() {
        var r = ChaveAcesso.gerar("RJ", YearMonth.of(2026, 9), "11222333000181", "55", "1", 123L, "1", "45678901");

        assertEquals(44, r.chave().length());
        assertEquals("33", r.codigoUf());
        assertTrue(r.chave().startsWith("332609" + "11222333000181" + "55" + "001" + "000000123" + "1" + "45678901"));
        assertEquals(ChaveAcesso.calcularDv(r.chave().substring(0, 43)), Character.getNumericValue(r.chave().charAt(43)));
    }

    @Test
    void aceitaCnpjAlfanumericoNaChave() {
        var r = ChaveAcesso.gerar("SP", YearMonth.of(2026, 9), "12ABC34501DE35", "55", "1", 1L, "1", "12345678");

        assertEquals(44, r.chave().length());
        assertTrue(r.chave().contains("12ABC34501DE35"));
    }

    @Test
    void rejeitaUfInvalida() {
        assertThrows(BusinessException.class,
                () -> ChaveAcesso.gerar("XX", YearMonth.of(2026, 9), "11222333000181", "55", "1", 1L, "1", "12345678"));
    }

    @Test
    void codigoNumericoTem8DigitosENaoRepeteONumeroNemUmaSequenciaTrivial() {
        for (int i = 0; i < 200; i++) {
            String cNF = ChaveAcesso.gerarCodigoNumerico(7L);
            assertEquals(8, cNF.length());
            assertNotEquals("00000007", cNF);
            assertTrue(cNF.chars().distinct().count() > 1);
        }
    }
}
