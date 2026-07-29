package com.l.erp.fiscalservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.fiscalservice.api.dto.MotorFiscalRequest;
import com.l.erp.fiscalservice.api.dto.OperacaoFiscalDTO;
import com.l.erp.fiscalservice.exception.FiscalException;
import com.l.erp.fiscalservice.infra.config.SplitPaymentProperties;
import com.l.erp.fiscalservice.services.fiscal.AliquotaIbs;
import com.l.erp.fiscalservice.services.fiscal.CfopInfo;
import com.l.erp.fiscalservice.services.fiscal.RegimeDiferenciado;
import com.l.erp.fiscalservice.services.fiscal.TabelaFiscal;
import com.l.erp.fiscalservice.services.fiscal.TipoOperacaoFiscal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor Fiscal — núcleo de cálculo IBS/CBS/IS (Fin.md Módulo I, §1.4).
 *
 * <p>Determinístico e sem side effects: mesmos inputs → mesmo output; não persiste nada (MF-06).
 * Fatia 1: apenas SAÍDA (NF-e/NFC-e/NFS-e). Entrada/crédito (§1.4.3), persistência
 * ({@code calcularEPersistir}), recálculo de período e apuração mensal ficam para fatias seguintes.
 */
@Service
public class MotorFiscalService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA = 2;

    private final TabelaFiscal tabela;
    private final SplitPaymentProperties splitProps;

    public MotorFiscalService(TabelaFiscal tabela, SplitPaymentProperties splitProps) {
        this.tabela = tabela;
        this.splitProps = splitProps;
    }

    /**
     * @param tenantId tenant da requisição (header {@code X-Tenant-Id}); usado apenas para decidir
     *                 o split payment por tenant — não influencia o cálculo dos tributos.
     */
    public OperacaoFiscalDTO calcular(MotorFiscalRequest req, String tenantId) {
        List<String> memoria = new ArrayList<>();
        boolean splitLigado = splitProps.habilitadoPara(tenantId);

        // PASSO 0 — entrada inconsistente é 400, nunca tributo calculado no escuro.
        boolean produto = preenchido(req.getNcm());
        boolean servico = preenchido(req.getCodigoServico());
        if (produto == servico) {
            throw new FiscalException(produto
                    ? Constants.FISCAL_NCM_E_SERVICO_CONFLITANTES     // os dois: produto ou serviço?
                    : Constants.FISCAL_NCM_OU_SERVICO_OBRIGATORIO);   // nenhum: nada a classificar
        }
        // Com o split ligado, splitPaymentAplicavel vem da condicao_pagamento e é obrigatório:
        // sem ele não dá pra distinguir "pagamento não splitável" de "o chamador esqueceu".
        if (splitLigado && req.getSplitPaymentAplicavel() == null) {
            throw new FiscalException(Constants.FISCAL_SPLIT_SEM_FORMA_PAGAMENTO);
        }

        // MEI não destaca IBS/CBS/IS (MF-02, §1.4.5)
        if (Constants.REGIME_MEI.equals(req.getRegimeEmpresa())) {
            memoria.add("Regime MEI: não destaca IBS/CBS/IS");
            return zerado(req, RegimeDiferenciado.PADRAO, memoria, splitLigado);
        }

        // PASSO 1 — validar CFOP
        CfopInfo cfop = tabela.cfop(req.getCfop())
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_CFOP_NAO_ENCONTRADO));
        if (cfop.tipoOperacao() != TipoOperacaoFiscal.SAIDA) {
            throw new FiscalException(Constants.FISCAL_CFOP_INVALIDO_SAIDA);
        }

        // Serviço (NFS-e): IBS é pelo LOCAL DA PRESTAÇÃO, não pelo tomador (§1.4.5)
        String ibgeDestino = servico ? req.getIbgeLocalPrestacao() : req.getIbgeDestino();
        RegimeDiferenciado regime = servico
                ? tabela.regimeServico(req.getCodigoServico())
                : tabela.regimeNcm(req.getNcm());

        // PASSO 2 — cesta básica / monofásico
        if (regime.isCestaBasica()) {
            memoria.add("NCM cesta básica: IBS/CBS/IS = 0 (§1.4.2 Passo 2)");
            return zerado(req, regime, memoria, splitLigado);
        }
        if (regime.isMonofasico() && !cfop.primeiraEtapaCadeia()) {
            memoria.add("Monofásico fora da 1ª etapa: já recolhido na origem (§1.4.2 Passo 2)");
            return zerado(req, regime, memoria, splitLigado);
        }

        // PASSO 3 — alíquotas vigentes pela data de competência
        int ano = req.getDataCompetencia().getYear();
        AliquotaIbs aliqIbs = tabela.aliquotaIbs(ibgeDestino, ano)
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_MUNICIPIO_SEM_ALIQUOTA_IBS));
        BigDecimal aliqCbs = tabela.aliquotaCbs(req.getRegimeEmpresa(), ano)
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_REGIME_SEM_ALIQUOTA_CBS));

        // PASSO 4 — IS antes do IBS/CBS (incide sobre o valor bruto, sem redução)
        BigDecimal aliqIs = servico ? BigDecimal.ZERO : tabela.aliquotaIs(req.getNcm()).orElse(BigDecimal.ZERO);
        BigDecimal valorIs = pct(req.getValorOperacao(), aliqIs);

        // PASSO 5 — base (o IS INTEGRA a base — LC 214/2025) + redução de ALÍQUOTA (não de base)
        BigDecimal base = req.getValorOperacao().add(valorIs);
        BigDecimal fator = fatorReducao(regime.reducaoPercentual());
        BigDecimal aliqIbsEstEfetiva = aliqIbs.estadual().multiply(fator);
        BigDecimal aliqIbsMunEfetiva = aliqIbs.municipal().multiply(fator);
        BigDecimal aliqCbsEfetiva = aliqCbs.multiply(fator);

        // PASSO 6 — IBS estadual + municipal
        BigDecimal valorIbsEstadual = pct(base, aliqIbsEstEfetiva);
        BigDecimal valorIbsMunicipal = pct(base, aliqIbsMunEfetiva);
        BigDecimal valorIbs = valorIbsEstadual.add(valorIbsMunicipal);

        // PASSO 7 — CBS
        BigDecimal valorCbs = pct(base, aliqCbsEfetiva);

        // PASSO 8 — split payment (teto = valor do tributo; liquidação real vem em fatia futura).
        // Flag desligada ⇒ campos AUSENTES (null) no contrato de saída, não zerados: o documento
        // fiscal não carrega split e nada é informado à Plataforma Pública.
        boolean aplicavel = splitLigado && Boolean.TRUE.equals(req.getSplitPaymentAplicavel());
        BigDecimal valorSplitIbs = split(splitLigado, aplicavel ? valorIbs : null);
        BigDecimal valorSplitCbs = split(splitLigado, aplicavel ? valorCbs : null);

        memoria.add("Regime: " + regime.name() + " (redução de alíquota " + regime.reducaoPercentual() + "%)");
        memoria.add("IS: " + valorIs + " (alíquota " + aliqIs + "%)");
        memoria.add("Base IBS/CBS (valor + IS): " + base);
        memoria.add("IBS estadual: " + valorIbsEstadual + " | municipal: " + valorIbsMunicipal);
        memoria.add("CBS: " + valorCbs);

        return OperacaoFiscalDTO.builder()
                .baseCalculo(base)
                .valorIs(valorIs)
                .valorIbsEstadual(valorIbsEstadual)
                .valorIbsMunicipal(valorIbsMunicipal)
                .valorIbs(valorIbs)
                .valorCbs(valorCbs)
                .valorSplitIbs(valorSplitIbs)
                .valorSplitCbs(valorSplitCbs)
                .regimeAplicado(regime.name())
                .memoriaCalculo(memoria)
                .build();
    }

    private OperacaoFiscalDTO zerado(MotorFiscalRequest req, RegimeDiferenciado regime,
                                     List<String> memoria, boolean splitLigado) {
        BigDecimal zero = BigDecimal.ZERO.setScale(ESCALA);
        return OperacaoFiscalDTO.builder()
                .baseCalculo(req.getValorOperacao())
                .valorIs(zero)
                .valorIbsEstadual(zero)
                .valorIbsMunicipal(zero)
                .valorIbs(zero)
                .valorCbs(zero)
                .valorSplitIbs(split(splitLigado, null))
                .valorSplitCbs(split(splitLigado, null))
                .regimeAplicado(regime.name())
                .memoriaCalculo(memoria)
                .build();
    }

    /**
     * Valor de split a publicar: {@code null} (campo ausente) com a flag desligada; com a flag
     * ligada, o tributo a segregar — ou 0,00 quando a forma de pagamento não é splitável.
     */
    private BigDecimal split(boolean ligado, BigDecimal tributo) {
        if (!ligado) {
            return null;
        }
        return tributo != null ? tributo : BigDecimal.ZERO.setScale(ESCALA);
    }

    /** valor × alíquota% ÷ 100, arredondado a 2 casas (HALF_UP). */
    private BigDecimal pct(BigDecimal valor, BigDecimal aliquotaPercentual) {
        return valor.multiply(aliquotaPercentual).divide(CEM, ESCALA, RoundingMode.HALF_UP);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Fator multiplicador da redução de alíquota: (1 − redução/100). */
    private BigDecimal fatorReducao(BigDecimal reducaoPercentual) {
        return BigDecimal.ONE.subtract(reducaoPercentual.divide(CEM));
    }
}