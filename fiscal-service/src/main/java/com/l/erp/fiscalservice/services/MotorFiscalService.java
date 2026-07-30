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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MotorFiscalService.class);

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
        // O regime IBS/CBS do serviço vem do cClassTrib DECLARADO, não do código LC 116 — o mesmo
        // serviço muda de classificação conforme o contexto (à administração pública vira 200043).
        // Sem ele o motor tributaria no escuro, então é 400, não fallback silencioso.
        if (servico) {
            if (!preenchido(req.getCClassTrib())) {
                throw new FiscalException(Constants.FISCAL_CCLASSTRIB_OBRIGATORIO);
            }
            // E não é campo livre: o Anexo VIII fixa quais cClassTrib valem para cada item LC 116.
            // Sem esta checagem um código inexistente cairia em PADRAO e tributaria cheio, calado.
            if (!tabela.cClassTribAdmitido(req.getCodigoServico(), req.getCClassTrib())) {
                throw new FiscalException(Constants.FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO);
            }
        }
        // tipoDocumento (opcional) tem que casar com o que veio classificado: NFS-e é documento de
        // serviço, NF-e/NFC-e de produto. Trocar isso muda o destino do IBS — local da prestação
        // (serviço) x município do destinatário (produto) —, então é 400. CT-e fica fora da regra:
        // o motor ainda não trata transporte, e reprovar aqui seria inventar regra.
        String tipoDoc = req.getTipoDocumento();
        boolean docDeServico = Constants.FISCAL_TIPO_DOC_NFSE.equals(tipoDoc);
        boolean docDeProduto = Constants.FISCAL_TIPO_DOC_NFE.equals(tipoDoc)
                || Constants.FISCAL_TIPO_DOC_NFCE.equals(tipoDoc);
        if ((docDeServico && produto) || (docDeProduto && servico)) {
            throw new FiscalException(Constants.FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL);
        }
        // Com o split ligado, splitPaymentAplicavel vem da condicao_pagamento e é obrigatório:
        // sem ele não dá pra distinguir "pagamento não splitável" de "o chamador esqueceu".
        if (splitLigado && req.getSplitPaymentAplicavel() == null) {
            throw new FiscalException(Constants.FISCAL_SPLIT_SEM_FORMA_PAGAMENTO);
        }

        // PASSO 0.5 — compor a base a partir dos componentes, em vez de confiar num número pronto:
        // frete, seguro e acessórias ENTRAM, desconto incondicional SAI (LC 214 art. 12, §2º).
        // Vem antes de MEI e alíquota zero porque esses caminhos também devolvem baseCalculo.
        BigDecimal valorTributavel = valorTributavel(req, memoria);

        // ZFM tem tratamento próprio na LC 214 que o motor NÃO implementa (fatia futura). O item é
        // tributado como nacional — pode dar imposto a mais —, então avisa antes de qualquer
        // caminho de retorno: vale também para MEI e alíquota zero, que retornam mais abaixo.
        if (Constants.FISCAL_ORIGEM_ZFM.equals(req.getOrigemProduto())) {
            log.warn("{} (tenant={}, cfop={}, ncm={})",
                    Constants.FISCAL_AVISO_ORIGEM_ZFM, tenantId, req.getCfop(), req.getNcm());
            memoria.add(Constants.FISCAL_AVISO_ORIGEM_ZFM);
        }

        // MEI não destaca IBS/CBS/IS (MF-02, §1.4.5)
        if (Constants.REGIME_MEI.equals(req.getRegimeEmpresa())) {
            memoria.add("Regime MEI: não destaca IBS/CBS/IS");
            return zerado(valorTributavel, RegimeDiferenciado.PADRAO, memoria, splitLigado);
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
                ? tabela.regimeCClassTrib(req.getCClassTrib())
                : tabela.regimeNcm(req.getNcm());

        // PADRAO aqui não é classificação declarada (isso é INTEGRAL): é ausência de linha em
        // regime_dif_ncm/regime_cclasstrib. O motor segue e tributa cheio — erro contra o
        // contribuinte — então o aviso vai pro log E pra memória de cálculo, nunca calado.
        if (Constants.REGIME_DIF_PADRAO.equals(regime.name())) {
            String aviso = Constants.FISCAL_AVISO_REGIME_PADRAO.formatted(
                    servico ? Constants.FISCAL_TIPO_CODIGO_CCLASSTRIB : Constants.FISCAL_TIPO_CODIGO_NCM,
                    servico ? req.getCClassTrib() : req.getNcm());
            log.warn("{} (tenant={}, cfop={})", aviso, tenantId, req.getCfop());
            memoria.add(aviso);
        }

        // PASSO 2 — alíquota zero (cesta básica, isento, imune) / monofásico
        if (regime.aliquotaZero()) {
            memoria.add("Regime " + regime.name() + ": alíquota zero, IBS/CBS/IS = 0 (§1.4.2 Passo 2)");
            return zerado(valorTributavel, regime, memoria, splitLigado);
        }
        if (regime.monofasico() && !cfop.primeiraEtapaCadeia()) {
            memoria.add("Monofásico fora da 1ª etapa: já recolhido na origem (§1.4.2 Passo 2)");
            return zerado(valorTributavel, regime, memoria, splitLigado);
        }

        // PASSO 3 — alíquotas vigentes pela data de competência
        int ano = req.getDataCompetencia().getYear();
        AliquotaIbs aliqIbs = tabela.aliquotaIbs(ibgeDestino, ano)
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_VIGENCIA_SEM_COBERTURA));

        // Alíquota de referência não é dado faltando — é a alíquota legal de quem não legislou a
        // própria. Mas se o ente legislou e a carga não tem, o imposto sai errado, então avisa.
        if (aliqIbs.referenciaNacional()) {
            String aviso = Constants.FISCAL_AVISO_ALIQUOTA_REFERENCIA.formatted(ibgeDestino);
            log.warn("{} (tenant={}, cfop={}, ano={})", aviso, tenantId, req.getCfop(), ano);
            memoria.add(aviso);
        }

        BigDecimal aliqCbs = tabela.aliquotaCbs(req.getRegimeEmpresa(), ano)
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_REGIME_SEM_ALIQUOTA_CBS));

        // PASSO 4 — IS antes do IBS/CBS (incide sobre o valor bruto, sem redução)
        BigDecimal aliqIs = servico ? BigDecimal.ZERO : tabela.aliquotaIs(req.getNcm()).orElse(BigDecimal.ZERO);
        BigDecimal valorIs = pct(valorTributavel, aliqIs);

        // PASSO 5 — base (o IS INTEGRA a base — LC 214/2025) + redução de ALÍQUOTA (não de base)
        BigDecimal base = valorTributavel.add(valorIs);
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

    private OperacaoFiscalDTO zerado(BigDecimal valorTributavel, RegimeDiferenciado regime,
                                     List<String> memoria, boolean splitLigado) {
        BigDecimal zero = BigDecimal.ZERO.setScale(ESCALA);
        return OperacaoFiscalDTO.builder()
                .baseCalculo(valorTributavel)
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

    /**
     * Base antes do IS: valor da operação + frete + seguro + outras despesas acessórias − desconto
     * incondicional (LC 214 art. 12, §2º). Componentes são opcionais; sem nenhum deles devolve o
     * próprio valor da operação e não polui a memória de cálculo. Desconto que zera ou inverte a
     * operação é erro de entrada (400), nunca base negativa tributada.
     */
    private BigDecimal valorTributavel(MotorFiscalRequest req, List<String> memoria) {
        BigDecimal frete = ouZero(req.getValorFrete());
        BigDecimal seguro = ouZero(req.getValorSeguro());
        BigDecimal outras = ouZero(req.getValorOutrasDespesas());
        BigDecimal desconto = ouZero(req.getValorDesconto());
        if (frete.signum() == 0 && seguro.signum() == 0 && outras.signum() == 0
                && desconto.signum() == 0) {
            return req.getValorOperacao();
        }
        BigDecimal tributavel = req.getValorOperacao()
                .add(frete).add(seguro).add(outras).subtract(desconto);
        if (tributavel.signum() <= 0) {
            throw new FiscalException(Constants.FISCAL_DESCONTO_MAIOR_QUE_OPERACAO);
        }
        memoria.add(Constants.FISCAL_MEMORIA_BASE_COMPOSTA.formatted(
                tributavel, req.getValorOperacao(), frete, seguro, outras, desconto));
        return tributavel;
    }

    private static BigDecimal ouZero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
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