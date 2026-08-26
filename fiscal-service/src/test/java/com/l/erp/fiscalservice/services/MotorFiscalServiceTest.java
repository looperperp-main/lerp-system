package com.l.erp.fiscalservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.fiscalservice.api.dto.MotorFiscalRequest;
import com.l.erp.fiscalservice.api.dto.OperacaoFiscalDTO;
import com.l.erp.fiscalservice.exception.FiscalException;
import com.l.erp.fiscalservice.infra.config.SplitPaymentProperties;
import com.l.erp.fiscalservice.services.fiscal.TabelaFiscalFake;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oráculo: exemplos numéricos do Fin.md §1.4.8 (SP/município 3550308, 2033,
 * IBS estadual 16,00% + municipal 2,50%, CBS Lucro Real 8,50%).
 *
 * <p>O ano é 2033 (REGIME PERMANENTE), não 2027: a alíquota real de 2027 é 0,05% + 0,05% de IBS
 * e 8,4% de CBS (portal do piloto CBS), simbólica de propósito, e tributo de 0,10 não exercita
 * arredondamento nem redução. Os 16,00/2,50/8,50 são as alíquotas de referência REAIS de 2033.
 * Sem contexto Spring — cálculo puro e determinístico.
 */
class MotorFiscalServiceTest {

    private static final String SP = "3550308";
    /** Rio: no fake só existe pela linha de REFERÊNCIA — é o caso H3 (aviso sem mudar o número). */
    private static final String RJ = "3304557";
    private static final String TENANT_PILOTO = "tenant-piloto";
    private static final LocalDate COMP = LocalDate.of(2033, 3, 15);

    /** Default do produto: split desligado. */
    private final MotorFiscalService motor = motorCom(new SplitPaymentProperties(false, Set.of()));

    private static MotorFiscalService motorCom(SplitPaymentProperties split) {
        return new MotorFiscalService(new TabelaFiscalFake(), split);
    }

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
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("0", r.getValorIs());
        assertValor("1600.00", r.getValorIbsEstadual());
        assertValor("250.00", r.getValorIbsMunicipal());
        assertValor("1850.00", r.getValorIbs());
        assertValor("850.00", r.getValorCbs());
    }

    @Test
    void ex2_cestaBasica_arroz_zeraTudo() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5102").ncm("10063021").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("500")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

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
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("150.00", r.getValorIs());
        assertValor("250", r.getBaseCalculo());        // 100 + IS integra a base
        assertValor("46.25", r.getValorIbs());         // 40.00 + 6.25
        assertValor("21.25", r.getValorCbs());
    }

    @Test
    void ex3b_cigarro_distribuidor_foraDa1aEtapa_zera() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5102").ncm("24022000").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("0", r.getValorIbs());
        assertValor("0", r.getValorCbs());
        assertValor("0", r.getValorIs());
    }

    @Test
    void ex4_servico_saude_reducao60() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("4.01").cClassTrib("200029").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("300")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("300", r.getBaseCalculo());
        assertValor("19.20", r.getValorIbsEstadual());  // 300 * (16.00% * 0.4)
        assertValor("3.00", r.getValorIbsMunicipal());   // 300 * (2.50% * 0.4)
        assertValor("22.20", r.getValorIbs());
        assertValor("10.20", r.getValorCbs());           // 300 * (8.50% * 0.4)
        assertEquals("ANEXO_III_60", r.getRegimeAplicado());
    }

    /**
     * D3 do spec/casos-teste-motor-fiscal.md — cClassTrib 000001 (tributação integral): par admitido
     * pelo Anexo VIII e COM linha em regime_cclasstrib (redução 0), então sai INTEGRAL com alíquota
     * cheia. Guarda a diferença entre "integral por classificação" e PADRAO (cClassTrib sem linha).
     */
    @Test
    void ex5_servico_cclassTribIntegral_semReducao() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("500")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("0", r.getValorIs());                // serviço não tem IS
        assertValor("500", r.getBaseCalculo());
        assertValor("80.00", r.getValorIbsEstadual());   // 500 * 16.00%
        assertValor("12.50", r.getValorIbsMunicipal());  // 500 * 2.50%
        assertValor("92.50", r.getValorIbs());
        assertValor("42.50", r.getValorCbs());           // 500 * 8.50%
        assertEquals("INTEGRAL", r.getRegimeAplicado());
    }

    @Test
    void mei_naoDestaca_zeraTudo() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_MEI).build(), null);

        assertValor("0", r.getValorIbs());
        assertValor("0", r.getValorCbs());
        assertValor("10000", r.getBaseCalculo());
    }

    @Test
    void splitPayment_flagLigada_espelhaTributos() {
        MotorFiscalService comSplit = motorCom(new SplitPaymentProperties(true, Set.of()));

        OperacaoFiscalDTO r = comSplit.calcular(notebookSplit(true), null);
        assertValor("1850.00", r.getValorSplitIbs());
        assertValor("850.00", r.getValorSplitCbs());

        // Flag ligada mas forma de pagamento não splitável: campo presente, valor zero.
        OperacaoFiscalDTO semSplitNoPagamento = comSplit.calcular(notebookSplit(false), null);
        assertValor("0", semSplitNoPagamento.getValorSplitIbs());
        assertValor("0", semSplitNoPagamento.getValorSplitCbs());
    }

    @Test
    void splitPayment_flagDesligada_naoEmiteCampos() {
        OperacaoFiscalDTO r = motor.calcular(notebookSplit(true), TENANT_PILOTO);

        assertNull(r.getValorSplitIbs());
        assertNull(r.getValorSplitCbs());
        assertValor("1850.00", r.getValorIbs());   // flag não altera o cálculo do tributo
    }

    @Test
    void splitPayment_ligadoApenasParaTenantDaAllowlist() {
        MotorFiscalService porTenant = motorCom(new SplitPaymentProperties(false, Set.of(TENANT_PILOTO)));

        OperacaoFiscalDTO ligado = porTenant.calcular(notebookSplit(true), TENANT_PILOTO);
        assertValor("1850.00", ligado.getValorSplitIbs());

        OperacaoFiscalDTO outroTenant = porTenant.calcular(notebookSplit(true), "tenant-fora-da-lista");
        assertNull(outroTenant.getValorSplitIbs());
    }

    @Test
    void splitLigado_semFormaPagamento_lancaFiscalException() {
        MotorFiscalService comSplit = motorCom(new SplitPaymentProperties(true, Set.of()));

        FiscalException ex = assertThrows(FiscalException.class,
                () -> comSplit.calcular(notebookSplit(null), null));
        assertEquals(Constants.FISCAL_SPLIT_SEM_FORMA_PAGAMENTO, ex.getCodigo());

        // com o split desligado o campo continua opcional — não quebra quem não usa split
        assertNull(motor.calcular(notebookSplit(null), null).getValorSplitIbs());
    }

    @Test
    void ncmEServico_juntos_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5933").ncm("84713012").codigoServico("4.01")
                        .ibgeDestino(SP).ibgeLocalPrestacao(SP)
                        .valorOperacao(new BigDecimal("300")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_NCM_E_SERVICO_CONFLITANTES, ex.getCodigo());
    }

    @Test
    void semNcmNemServico_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5101").ibgeDestino(SP)
                        .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_NCM_OU_SERVICO_OBRIGATORIO, ex.getCodigo());
    }

    @Test
    void servicoSemCClassTrib_lancaFiscalException() {
        // Sem cClassTrib não dá pra saber se o serviço tem redução: cair em PADRAO tributaria
        // cheio um serviço possivelmente desonerado — erro CONTRA o contribuinte, e calado.
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5933").codigoServico("4.01").ibgeLocalPrestacao(SP)
                        .valorOperacao(new BigDecimal("300")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_CCLASSTRIB_OBRIGATORIO, ex.getCodigo());
    }

    @Test
    void servicoComCClassTribNaoAdmitido_lancaFiscalException() {
        // 200048 é hotelaria: existe no Anexo VIII, mas não vale para o item 4.01 (saúde).
        // Sem a checagem cairia em PADRAO e tributaria cheio sem avisar ninguém.
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5933").codigoServico("4.01").cClassTrib("200048").ibgeLocalPrestacao(SP)
                        .valorOperacao(new BigDecimal("300")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO, ex.getCodigo());
    }

    @Test
    void tipoDocumentoTrocado_lancaFiscalException() {
        // NFS-e é documento de serviço e NF-e/NFC-e de produto: trocar isso muda o destino do IBS
        // (local da prestação x município do destinatário), então é 400 e não fallback.
        FiscalException comNcm = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5101").ncm("84713012").ibgeDestino(SP)
                        .tipoDocumento(Constants.FISCAL_TIPO_DOC_NFSE)
                        .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL, comNcm.getCodigo());

        FiscalException comServico = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5933").codigoServico("4.01").cClassTrib("200029").ibgeLocalPrestacao(SP)
                        .tipoDocumento(Constants.FISCAL_TIPO_DOC_NFE)
                        .valorOperacao(new BigDecimal("300")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL, comServico.getCodigo());
    }

    /** CT-e fica FORA da regra (transporte ainda não é tratado) e o campo segue opcional. */
    @Test
    void tipoDocumentoCoerenteOuCte_calculaNormalmente() {
        assertValor("1850.00", motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .tipoDocumento(Constants.FISCAL_TIPO_DOC_NFCE)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null).getValorIbs());

        assertValor("1850.00", motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .tipoDocumento("CTe")
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null).getValorIbs());
    }

    /**
     * ZFM não tem tratamento implementado: o item é tributado como nacional (imposto possivelmente
     * a mais), então o aviso tem que sair na memória de cálculo — nunca calado.
     */
    @Test
    void origemZfm_avisaNaMemoriaCalculo_semAlterarOCalculo() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP).origemProduto(Constants.FISCAL_ORIGEM_ZFM)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertTrue(r.getMemoriaCalculo().contains(Constants.FISCAL_AVISO_ORIGEM_ZFM));
        assertValor("1850.00", r.getValorIbs());   // aviso não altera o tributo

        OperacaoFiscalDTO nacional = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP).origemProduto("NACIONAL")
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertFalse(nacional.getMemoriaCalculo().contains(Constants.FISCAL_AVISO_ORIGEM_ZFM));
    }

    /**
     * H3: município sem alíquota própria cai na de referência (fiscal-023) — mesmo número de A1,
     * mas com aviso. O silêncio é que seria perigoso: se o ente publicou alíquota própria e a carga
     * não tem, o imposto sai errado sem ninguém notar.
     */
    @Test
    void municipioSemAliquotaPropria_avisaSemAlterarOCalculo() {
        String avisoRio = Constants.FISCAL_AVISO_ALIQUOTA_REFERENCIA.formatted(RJ);

        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(RJ)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertTrue(r.getMemoriaCalculo().contains(avisoRio));
        assertValor("1850.00", r.getValorIbs());   // referência não muda o número, só avisa
        assertValor("850.00", r.getValorCbs());

        // São Paulo tem linha própria no fake: mesmo valor, sem aviso.
        OperacaoFiscalDTO comLinhaPropria = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertFalse(comLinhaPropria.getMemoriaCalculo().stream()
                .anyMatch(l -> l.startsWith("AVISO: município")));
    }

    /**
     * 7.14: frete, seguro e acessórias ENTRAM na base; desconto incondicional SAI.
     * 10000 + 500 + 100 + 50 − 200 = 10450 → IBS 16,00% + 2,50% e CBS 8,50% sobre 10450.
     */
    @Test
    void baseComposta_acrescimosEntram_descontoSai() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000"))
                .valorFrete(new BigDecimal("500")).valorSeguro(new BigDecimal("100"))
                .valorOutrasDespesas(new BigDecimal("50")).valorDesconto(new BigDecimal("200"))
                .dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("10450", r.getBaseCalculo());
        assertValor("1672.00", r.getValorIbsEstadual());
        assertValor("261.25", r.getValorIbsMunicipal());
        assertValor("1933.25", r.getValorIbs());
        assertValor("888.25", r.getValorCbs());
        assertTrue(r.getMemoriaCalculo().stream().anyMatch(l -> l.startsWith("Valor tributável:")));

        // Sem componentes a linha da composição não sai — o valor da operação já É a base.
        OperacaoFiscalDTO semComponentes = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertFalse(semComponentes.getMemoriaCalculo().stream()
                .anyMatch(l -> l.startsWith("Valor tributável:")));
    }

    /** O IS incide sobre a base JÁ composta: (100 + 20) × 150% = 180, e integra a base → 300. */
    @Test
    void baseComposta_isIncideSobreOTotal() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("24022000").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("100")).valorFrete(new BigDecimal("20"))
                .dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("180.00", r.getValorIs());
        assertValor("300", r.getBaseCalculo());
        assertValor("48.00", r.getValorIbsEstadual());
        assertValor("7.50", r.getValorIbsMunicipal());
        assertValor("25.50", r.getValorCbs());
    }

    /** Caminho de retorno antecipado (alíquota zero) também devolve a base COMPOSTA: 500 + 20. */
    @Test
    void baseComposta_valeParaCaminhoDeAliquotaZero() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5102").ncm("10063021").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("500")).valorFrete(new BigDecimal("20"))
                .dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("520", r.getBaseCalculo());
        assertValor("0", r.getValorIbs());
    }

    /** Desconto que zera ou inverte a operação é erro de entrada, não base negativa tributada. */
    @Test
    void descontoMaiorOuIgualAoValor_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5101").ncm("84713012").ibgeDestino(SP)
                        .valorOperacao(new BigDecimal("100")).valorDesconto(new BigDecimal("100"))
                        .dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_DESCONTO_MAIOR_QUE_OPERACAO, ex.getCodigo());
    }

    /** G5: CFOP de entrada no motor de saída é 400 — crédito de entrada é fatia futura (item 4). */
    @Test
    void cfopDeEntrada_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("1102").ncm("84713012").ibgeDestino(SP)
                        .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_CFOP_INVALIDO_SAIDA, ex.getCodigo());
    }

    @Test
    void cfopDesconhecido_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("9999").ncm("84713012").ibgeDestino(SP)
                        .valorOperacao(new BigDecimal("100")).dataCompetencia(COMP)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_CFOP_NAO_ENCONTRADO, ex.getCodigo());
    }

    @Test
    void memoriaCalculo_naoVazia_paraOperacaoTributada() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertTrue(r.getMemoriaCalculo().stream().anyMatch(l -> l.contains("CBS")));
    }

    /**
     * PADRAO = sem linha de regime, tributando cheio: tem que aparecer na memória de cálculo,
     * nunca calado. INTEGRAL (classificação declarada, redução 0) também tributa cheio, mas
     * NÃO é aviso — se avisasse nos dois o aviso perderia o sentido.
     */
    @Test
    void regimePadrao_avisaNaMemoriaCalculo_integralNao() {
        OperacaoFiscalDTO semRegime = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)   // NCM sem linha no fake
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertEquals(Constants.REGIME_DIF_PADRAO, semRegime.getRegimeAplicado());
        assertTrue(semRegime.getMemoriaCalculo().stream()
                .anyMatch(l -> l.contains("sem regime cadastrado") && l.contains("84713012")));

        OperacaoFiscalDTO integral = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertFalse(integral.getMemoriaCalculo().stream()
                .anyMatch(l -> l.contains("sem regime cadastrado")));
    }

    // ── Fatia 3c — legado (ICMS/ISS) durante a transição 2026-2033 ──

    @Test
    void legado_icms_produto_transicao2029() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .ufOrigem("SP").ufDestino("SP")
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(LocalDate.of(2029, 3, 15))
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("1620.00", r.getValorIcms()); // 10000 * 18% (fallback SP) * 90% remanescente
        assertNull(r.getValorIss());
    }

    @Test
    void legado_iss_servico_transicao2029() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("4.01").cClassTrib("200029").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("300")).dataCompetencia(LocalDate.of(2029, 3, 15))
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);

        assertValor("13.50", r.getValorIss()); // 300 * 5% (referência) * 90% remanescente
        assertNull(r.getValorIcms());
    }

    @Test
    void legado_transicaoZero_naoCalculaIcmsNemIss() {
        // COMP = 2033, pctRemanescente 0: os 26 testes originais já provam isso implicitamente,
        // este só deixa explícito que os dois campos ficam null no regime permanente.
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertNull(r.getValorIcms());
        assertNull(r.getValorIss());
    }

    @Test
    void legado_produtoSemUf_transicaoAtiva_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5101").ncm("84713012").ibgeDestino(SP)
                        .valorOperacao(new BigDecimal("10000")).dataCompetencia(LocalDate.of(2029, 3, 15))
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_UF_OBRIGATORIA_TRANSICAO, ex.getCodigo());
    }

    @Test
    void legado_icmsSemCobertura_lancaFiscalException() {
        // RJ→RJ não está na matriz do fake (só SP→SP): 400 em vez de assumir ICMS zero.
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5101").ncm("84713012").ibgeDestino(SP)
                        .ufOrigem("RJ").ufDestino("RJ")
                        .valorOperacao(new BigDecimal("10000")).dataCompetencia(LocalDate.of(2029, 3, 15))
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_ICMS_SEM_COBERTURA, ex.getCodigo());
    }

    @Test
    void anoForaDaCurvaDeTransicao_lancaFiscalException() {
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5101").ncm("84713012").ibgeDestino(SP)
                        .ufOrigem("SP").ufDestino("SP")
                        .valorOperacao(new BigDecimal("10000")).dataCompetencia(LocalDate.of(2025, 3, 15))
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_VIGENCIA_SEM_COBERTURA, ex.getCodigo());
    }

    // ── Fatia 3e — retenção na fonte (ISS/IRRF/CSRF/INSS) ──

    @Test
    void retencao_naoDeclarada_todosOsCamposNulos() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null);
        assertNull(r.getValorIssRetido());
        assertNull(r.getValorIrrf());
        assertNull(r.getValorCsrf());
        assertNull(r.getValorInss());
    }

    @Test
    void retencaoEmProduto_lancaFiscalException() {
        // Retenção na fonte é conceito de serviço (tomador retém do prestador) — declarar a flag
        // numa operação de produto é erro do chamador, não silenciosamente ignorado.
        FiscalException ex = assertThrows(FiscalException.class, () -> motor.calcular(
                MotorFiscalRequest.builder()
                        .cfop("5101").ncm("84713012").ibgeDestino(SP)
                        .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                        .reterIrrf(true)
                        .regimeEmpresa(Constants.REGIME_LUCRO_REAL).build(), null));
        assertEquals(Constants.FISCAL_RETENCAO_APENAS_SERVICO, ex.getCodigo());
    }

    @Test
    void retencao_issRetidoNaFonte_igualAoIssLegado() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("4.01").cClassTrib("200029").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("300")).dataCompetencia(LocalDate.of(2029, 3, 15))
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .issRetidoNaFonte(true).build(), null);
        assertValor("13.50", r.getValorIss());
        assertValor("13.50", r.getValorIssRetido());
    }

    @Test
    void retencao_irrf_calculaSobreValorTributavel() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .reterIrrf(true).build(), null);
        assertValor("150.00", r.getValorIrrf()); // 10000 * 1,50%
        assertNull(r.getValorCsrf());
        assertNull(r.getValorInss());
    }

    @Test
    void retencao_irrf_pisoMensal_dispensaSemAcumuloERetemComAcumulo() {
        // 500 * 1,50% = 7,50 — sozinho não supera o piso de 10,00 (Lei 13.137/2015 art. 67).
        OperacaoFiscalDTO semAcumulo = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("500")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .reterIrrf(true).build(), null);
        assertNull(semAcumulo.getValorIrrf());

        // Com 5,00 já retido no mês, o acumulado (5,00 + 7,50 = 12,50) supera o piso — retém só
        // a parcela desta operação (7,50), não o acumulado inteiro.
        OperacaoFiscalDTO comAcumulo = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("500")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .reterIrrf(true).valorAcumuladoMesIrrf(new BigDecimal("5.00")).build(), null);
        assertValor("7.50", comAcumulo.getValorIrrf());
    }

    @Test
    void retencao_csrf_acimaDoPiso_retem() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("6000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .reterCsrf(true).build(), null);
        assertValor("279.00", r.getValorCsrf()); // 6000 * 4,65%
    }

    @Test
    void retencao_csrf_noPisoOuAbaixo_dispensada() {
        // Piso é <= (igual dispensa): valor bruto igual a 5000,00 não supera o piso de 5000,00.
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("5000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .reterCsrf(true).build(), null);
        assertNull(r.getValorCsrf());
    }

    @Test
    void retencao_inss_semPiso_sempreRetem() {
        OperacaoFiscalDTO r = motor.calcular(MotorFiscalRequest.builder()
                .cfop("5933").codigoServico("1.01").cClassTrib("000001").ibgeLocalPrestacao(SP)
                .valorOperacao(new BigDecimal("1000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .reterInss(true).build(), null);
        assertValor("110.00", r.getValorInss()); // 1000 * 11,00%, piso 0 no fake
    }

    /** Notebook do ex1 (IBS 1850,00 / CBS 850,00), variando só a aplicabilidade do split. */
    private static MotorFiscalRequest notebookSplit(Boolean splitAplicavel) {
        return MotorFiscalRequest.builder()
                .cfop("5101").ncm("84713012").ibgeDestino(SP)
                .valorOperacao(new BigDecimal("10000")).dataCompetencia(COMP)
                .regimeEmpresa(Constants.REGIME_LUCRO_REAL)
                .splitPaymentAplicavel(splitAplicavel).build();
    }
}
