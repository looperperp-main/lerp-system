package com.l.erp.fiscalservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.fiscalservice.api.dto.MotorFiscalRequest;
import com.l.erp.fiscalservice.api.dto.OperacaoFiscalDTO;
import com.l.erp.fiscalservice.exception.FiscalException;
import com.l.erp.fiscalservice.services.fiscal.TabelaFiscalInMemory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oráculo: exemplos numéricos do Fin.md §1.4.8 (SP/município 3550308, 2027,
 * IBS estadual 13,12% + municipal 4,50%, CBS Lucro Real 8,80%).
 * Sem contexto Spring — cálculo puro e determinístico.
 */
class MotorFiscalServiceTest {

    private final MotorFiscalService motor = new MotorFiscalService(new TabelaFiscalInMemory());

    private static final String SP = "3550308";
    private static final LocalDate COMP = LocalDate.of(2027, 3, 15);

    /** BigDecimal por valor numérico (ignora escala): 21.14 == 21.140. */
    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual),
                () -> "esperado " + esperado + " mas foi " + atual);
    }

    @Test
    void ex1_produtoPadrao_notebook() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build());

        assertValor("0", r.getValorIs());
        assertValor("1312.00", r.getValorIbsEstadual());
        assertValor("450.00", r.getValorIbsMunicipal());
        assertValor("1762.00", r.getValorIbs());
        assertValor("880.00", r.getValorCbs());
    }

    @Test
    void ex2_cestaBasica_arroz_zeraTudo() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5102").ncm("10063021").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("500")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build());

        assertValor("0", r.getValorIbs());
        assertValor("0", r.getValorCbs());
        assertValor("0", r.getValorIs());
        assertValor("500", r.getBaseCalculo());
    }

    @Test
    void ex3_cigarro_fabricante_1aEtapa_comIS() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("24022000").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build());

        assertValor("150.00", r.getValorIs());
        assertValor("250", r.getBaseCalculo());        // 100 + IS integra a base
        assertValor("44.05", r.getValorIbs());         // 32.80 + 11.25
        assertValor("22.00", r.getValorCbs());
    }

    @Test
    void ex3b_cigarro_distribuidor_foraDa1aEtapa_zera() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5102").ncm("24022000").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build());

        assertValor("0", r.getValorIbs());
        assertValor("0", r.getValorCbs());
        assertValor("0", r.getValorIs());
    }

    @Test
    void ex4_servico_saude_reducao60() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("4.01").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("300")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build());

        assertValor("300", r.getBaseCalculo());
        assertValor("15.74", r.getValorIbsEstadual());  // 300 * (13.12% * 0.4)
        assertValor("5.40", r.getValorIbsMunicipal());   // 300 * (4.50% * 0.4)
        assertValor("21.14", r.getValorIbs());
        assertValor("10.56", r.getValorCbs());           // 300 * (8.80% * 0.4)
    }

    @Test
    void mei_naoDestaca_zeraTudo() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_MEI).build());

        assertValor("0", r.getValorIbs());
        assertValor("0", r.getValorCbs());
        assertValor("10000", r.getBaseCalculo());
    }

    @Test
    void splitPayment_espelhaTributos() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .splitPaymentAplicavel(true).build());

        assertValor("1762.00", r.getValorSplitIbs());
        assertValor("880.00", r.getValorSplitCbs());
    }

    @Test
    void cfopDesconhecido_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("9999").ncm("84713012").ibgeDestino(SP)
                        .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build()));
        assertEquals(Constants.FISCAL_CFOP_NAO_ENCONTRADO, ex.getCodigo());
    }

    @Test
    void memoriaCalculo_naoVazia_paraOperacaoTributada() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build());
        assertTrue(r.getMemoriaCalculo().stream().anyMatch(l -> l.contains("CBS")));
    }
}