package com.l.erp.fiscalservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.fiscalservice.api.dto.MotorFiscalRequest;
import com.l.erp.fiscalservice.api.dto.OperacaoFiscalDTO;
import com.l.erp.fiscalservice.exception.FiscalException;
import com.l.erp.fiscalservice.infra.config.SplitPaymentProperties;
import com.l.erp.fiscalservice.services.fiscal.AliquotaIbs;
import com.l.erp.fiscalservice.services.fiscal.AliquotaInterestadual;
import com.l.erp.fiscalservice.services.fiscal.AliquotaIss;
import com.l.erp.fiscalservice.services.fiscal.AliquotaRetencao;
import com.l.erp.fiscalservice.services.fiscal.CfopInfo;
import com.l.erp.fiscalservice.services.fiscal.RegimeDiferenciado;
import com.l.erp.fiscalservice.services.fiscal.RegimeIcms;
import com.l.erp.fiscalservice.services.fiscal.RegimeTributoOverride;
import com.l.erp.fiscalservice.services.fiscal.TabelaFiscal;
import com.l.erp.fiscalservice.services.fiscal.TipoOperacaoFiscal;
import com.l.erp.fiscalservice.services.fiscal.TransicaoAno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Motor Fiscal — núcleo de cálculo IBS/CBS/IS (Fin.md Módulo I, §1.4).
 *
 * <p>Determinístico e sem side effects: mesmos inputs → mesmo output; não persiste nada (MF-06).
 * Cobre SAÍDA (NF-e/NFC-e/NFS-e) e o crédito de ENTRADA (§1.4.3, item 4 — quem persiste saldo e
 * aproveitamento é o {@code operacoes-service}/AP). Persistência ({@code calcularEPersistir}),
 * recálculo de período e apuração mensal ficam para fatias seguintes.
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

        // PASSO 0 — CFOP determina o tipo de operação (SAÍDA/ENTRADA); vem primeiro porque o
        // split (só existe do lado da saída) e o resto da validação dependem de já saber qual é.
        //
        // Sem cfop pronto: resolve por naturezaOperacao + UF (Etapa 0, fiscal.cfop_regra) — só
        // cobre SAÍDA (venda); crédito de ENTRADA continua exigindo cfop explícito do chamador.
        // Muta req.setCfop() para que toda referência a req.getCfop() mais abaixo (memória/log)
        // já veja o código resolvido, sem duplicar variável.
        if (!preenchido(req.getCfop())) {
            if (!preenchido(req.getNaturezaOperacao())) {
                throw new FiscalException(Constants.FISCAL_NATUREZA_OPERACAO_OBRIGATORIA);
            }
            if (!preenchido(req.getUfOrigem()) || !preenchido(req.getUfDestino())) {
                throw new FiscalException(Constants.FISCAL_UF_OBRIGATORIA_RESOLUCAO_CFOP);
            }
            String cfopResolvido = tabela.resolverCfop(req.getNaturezaOperacao(), req.getUfOrigem(), req.getUfDestino())
                    .orElseThrow(() -> new FiscalException(Constants.FISCAL_CFOP_REGRA_NAO_ENCONTRADA));
            req.setCfop(cfopResolvido);
            memoria.add("CFOP resolvido: " + cfopResolvido + " (natureza=" + req.getNaturezaOperacao() + ")");
        }
        CfopInfo cfop = tabela.cfop(req.getCfop())
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_CFOP_NAO_ENCONTRADO));
        boolean entrada = cfop.tipoOperacao() == TipoOperacaoFiscal.ENTRADA;

        // PASSO 0.1 — entrada inconsistente é 400, nunca tributo calculado no escuro.
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
        // Split só existe do lado da saída (segregação no recebimento) — entrada não participa.
        if (!entrada && splitLigado && req.getSplitPaymentAplicavel() == null) {
            throw new FiscalException(Constants.FISCAL_SPLIT_SEM_FORMA_PAGAMENTO);
        }
        // Retenção (fatia 3e) só existe em serviço — ISS/IRRF/CSRF/INSS aqui são sobre pagamento
        // de serviço a PJ. Declarar retenção numa nota de produto é erro de entrada, não zero calado.
        boolean pedeRetencao = Boolean.TRUE.equals(req.getIssRetidoNaFonte())
                || Boolean.TRUE.equals(req.getReterIrrf())
                || Boolean.TRUE.equals(req.getReterCsrf())
                || Boolean.TRUE.equals(req.getReterInss());
        if (produto && pedeRetencao) {
            throw new FiscalException(Constants.FISCAL_RETENCAO_APENAS_SERVICO);
        }
        // Vedação de crédito (art. 57 §7º) só existe do lado da SAÍDA — a vedação em si já foi
        // decidida na entrada (item 4, usoConsumoPessoal). Declarar numa entrada é erro de entrada.
        if (entrada && Boolean.TRUE.equals(req.getBemSemCreditoNaEntrada())) {
            throw new FiscalException(Constants.FISCAL_VEDACAO_57_APENAS_SAIDA);
        }

        // PASSO 0.5 — compor a base a partir dos componentes, em vez de confiar num número pronto:
        // frete, seguro e acessórias ENTRAM, desconto incondicional SAI (LC 214 art. 12, §2º).
        // Vem antes de MEI e alíquota zero porque esses caminhos também devolvem baseCalculo.
        BigDecimal valorTributavel = valorTributavel(req, memoria);

        // PASSO 0.6 — art. 57 §7º da LC 214/2025 (incluído pela LC 227/2026): revenda de um bem
        // que não gerou crédito na entrada pode excluir da base o valor de aquisição, até o limite
        // do valor da venda — evita tributar em cascata algo que já "pagou" imposto sem nunca ter
        // tido crédito a abater. Declarado pelo chamador (AR/O2C, que sabe se aquele bem específico
        // gerou crédito no item 4) — o motor não deduz isso sozinho.
        if (Boolean.TRUE.equals(req.getBemSemCreditoNaEntrada())) {
            BigDecimal valorAquisicao = req.getValorAquisicaoSemCredito();
            if (valorAquisicao == null) {
                throw new FiscalException(Constants.FISCAL_VEDACAO_57_SEM_VALOR_AQUISICAO);
            }
            BigDecimal exclusao = valorAquisicao.min(valorTributavel);
            valorTributavel = valorTributavel.subtract(exclusao);
            memoria.add(Constants.FISCAL_MEMORIA_VEDACAO_57.formatted(exclusao, valorAquisicao));
        }

        // ZFM tem tratamento próprio na LC 214 que o motor NÃO implementa (fatia futura). O item é
        // tributado como nacional — pode dar imposto a mais —, então avisa antes de qualquer
        // caminho de retorno: vale também para MEI e alíquota zero, que retornam mais abaixo.
        if (Constants.FISCAL_ORIGEM_ZFM.equals(req.getOrigemProduto())) {
            log.warn("{} (tenant={}, cfop={}, ncm={})",
                    Constants.FISCAL_AVISO_ORIGEM_ZFM, tenantId, req.getCfop(), req.getNcm());
            memoria.add(Constants.FISCAL_AVISO_ORIGEM_ZFM);
        }

        // MEI não destaca IBS/CBS/IS (MF-02, §1.4.5) — nem CST/CSOSN: regimeEmpresa "MEI" não é
        // Constants.REGIME_SIMPLES_NACIONAL, então cairia no balde NORMAL por engano; mais seguro
        // deixar de fora (mesmo escopo já estabelecido para os campos da Etapa 0 neste caminho).
        if (Constants.REGIME_MEI.equals(req.getRegimeEmpresa())) {
            memoria.add("Regime MEI: não destaca IBS/CBS/IS");
            return zerado(valorTributavel, RegimeDiferenciado.PADRAO, memoria, splitLigado, null, null);
        }

        // Entrada (item 4, §1.4.3) — crédito, não tributo devido. Segue por cálculo próprio;
        // quem persiste saldo e aproveitamento é o operacoes-service (AP).
        if (entrada) {
            return calcularCredito(req, cfop, servico, valorTributavel, memoria);
        }

        // Serviço (NFS-e): IBS é pelo LOCAL DA PRESTAÇÃO, não pelo tomador (§1.4.5)
        String ibgeDestino = servico ? req.getIbgeLocalPrestacao() : req.getIbgeDestino();
        RegimeDiferenciado regime = regimeDoItem(req, servico, memoria);

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

        // Etapa 0 (§11) — CST-ICMS/CSOSN calculado aqui, ANTES dos retornos antecipados de
        // alíquota-zero/monofásico: ISENTA (CST 40) é exatamente o caso mais comum que passa por
        // zerado() logo abaixo, então a resolução não pode ficar depois desses returns. Só PRODUTO
        // (ICMS é imposto de mercadoria; NFS-e não tem esse campo).
        String cstIcms = null;
        String csosn = null;
        if (!servico) {
            Optional<String> resolvido = tabela.resolverCstIcms(req.getRegimeEmpresa(), regime);
            if (resolvido.isPresent()) {
                if (Constants.REGIME_SIMPLES_NACIONAL.equals(req.getRegimeEmpresa())) {
                    csosn = resolvido.get();
                } else {
                    cstIcms = resolvido.get();
                }
                memoria.add("CST/CSOSN resolvido: " + resolvido.get());
            }
        }

        // PASSO 2 — alíquota zero (cesta básica, isento, imune) / monofásico
        if (regime.aliquotaZero()) {
            memoria.add("Regime " + regime.name() + ": alíquota zero, IBS/CBS/IS = 0 (§1.4.2 Passo 2)");
            return zerado(valorTributavel, regime, memoria, splitLigado, cstIcms, csosn);
        }
        if (regime.monofasico() && !cfop.primeiraEtapaCadeia()) {
            memoria.add("Monofásico fora da 1ª etapa: já recolhido na origem (§1.4.2 Passo 2)");
            return zerado(valorTributavel, regime, memoria, splitLigado, cstIcms, csosn);
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

        // PASSO 3.5 — legado da transição (fatia 3c): ICMS (produto) ou ISS (serviço), na mesma
        // competência do IBS/CBS. Mesmo princípio de vigência sem cobertura de FISCAL_VIGENCIA_SEM_COBERTURA.
        TransicaoAno transicao = tabela.transicao(ano)
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_VIGENCIA_SEM_COBERTURA));
        Legado legado = calcularLegado(req, servico, valorTributavel, transicao, tenantId, memoria);

        // PASSO 4 — IS antes do IBS/CBS (incide sobre o valor bruto, sem redução)
        BigDecimal aliqIs = servico ? BigDecimal.ZERO
                : tabela.aliquotaIs(req.getNcm(), req.getDataCompetencia()).orElse(BigDecimal.ZERO);
        BigDecimal valorIs = pct(valorTributavel, aliqIs);

        // PASSO 5 — base (o IS INTEGRA a base — LC 214/2025) + redução de ALÍQUOTA (não de base)
        BigDecimal base = valorTributavel.add(valorIs);
        FatoresRegime fatores = fatoresEfetivos(regime, aliqIbs.estadual(), aliqIbs.municipal(), aliqCbs, ano, memoria);
        BigDecimal aliqIbsEstEfetiva = aliqIbs.estadual().multiply(fatores.fatorIbs());
        BigDecimal aliqIbsMunEfetiva = aliqIbs.municipal().multiply(fatores.fatorIbs());
        BigDecimal aliqCbsEfetiva = aliqCbs.multiply(fatores.fatorCbs());

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

        // PASSO 9 — retenção na fonte (fatia 3e): valores retidos, dentro do próprio motor
        // (decisão de 30/07/2026, spec/fiscal/motor-fiscal-proximos-passos.md §3) — persistir título e
        // gerar guia é responsabilidade do futuro AR/contas-a-receber, não deste serviço.
        Retencao retencao = calcularRetencao(req, valorTributavel, legado.iss(), tenantId, memoria);

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
                .valorIcms(legado.icms())
                .valorIss(legado.iss())
                .valorIssRetido(retencao.issRetido())
                .valorIrrf(retencao.irrf())
                .valorCsrf(retencao.csrf())
                .valorInss(retencao.inss())
                .regimeAplicado(regime.name())
                .memoriaCalculo(memoria)
                .cClassTrib(req.getCClassTrib())
                .percentualIbsUf(aliqIbsEstEfetiva)
                .percentualIbsMunicipal(aliqIbsMunEfetiva)
                .percentualCbs(aliqCbsEfetiva)
                .percentualReducaoAplicado(regime.reducaoPercentual())
                .percentualIcmsNominal(legado.percentualIcmsNominal())
                .percentualReducaoBaseIcms(legado.percentualReducaoBaseIcms())
                .modalidadeBaseCalculoIcms(legado.modalidadeBaseCalculoIcms())
                .cstIcms(cstIcms)
                .csosn(csosn)
                .percentualFcp(legado.percentualFcp())
                .valorFcp(legado.valorFcp())
                .percentualIcmsInterestadual(legado.percentualIcmsInterestadual())
                .baseCalculoUfDestino(legado.baseCalculoUfDestino())
                .baseCalculoFcpUfDestino(legado.baseCalculoFcpUfDestino())
                .percentualIcmsUfDestino(legado.percentualIcmsUfDestino())
                .percentualFcpUfDestino(legado.percentualFcpUfDestino())
                .percentualPartilhaDestino(legado.percentualPartilhaDestino())
                .valorIcmsUfDestino(legado.valorIcmsUfDestino())
                .valorFcpUfDestino(legado.valorFcpUfDestino())
                .valorIcmsUfRemetente(legado.valorIcmsUfRemetente())
                .build();
    }

    // ponytail: MEI/alíquota-zero/monofásico não calculam legado nem retenção nesta fatia —
    // campos saem null (mesmo comportamento de antes de 3c/3e). Escopo real desses casos fica
    // pra quando um caso de teste real exigir (ex.: serviço isento com ISS retido na fonte).
    // Mesmo raciocínio cobre cClassTrib/percentuais: esses caminhos retornam ANTES de buscar
    // AliquotaIbs/AliquotaCbs (Passo 3), então não há valor a propagar. cstIcms/csosn são a
    // EXCEÇÃO (parâmetros aqui, não sempre null): resolvidos antes do early-return porque ISENTA
    // é exatamente o caso mais comum que passa por aqui — ver PASSO 2 em calcular().
    private OperacaoFiscalDTO zerado(BigDecimal valorTributavel, RegimeDiferenciado regime,
                                     List<String> memoria, boolean splitLigado,
                                     String cstIcms, String csosn) {
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
                .cstIcms(cstIcms)
                .csosn(csosn)
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

    /** Mesmo que {@link #pct}, com escala configurável — usado na base dupla do DIFAL (issue #103,
     * Conv. ICMS 236/2021), onde a divisão intermediária precisa de escala ≥ 10 e o arredondamento
     * final só entra depois (mesmo cuidado de {@code fatoresEfetivos}). */
    private BigDecimal pctEscala(BigDecimal valor, BigDecimal aliquotaPercentual, int escala) {
        return valor.multiply(aliquotaPercentual).divide(CEM, escala, RoundingMode.HALF_UP);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    /**
     * Regime diferenciado do item, resolvendo o conflito de um mesmo NCM aparecer em dois anexos
     * da LC 214 com reduções diferentes (ex.: {@code 21069090} em Anexo I 100% e Anexo VI 60%).
     *
     * <p>Princípio da especialidade: vence o anexo de maior benefício <b>desde que</b> o produto
     * atenda estritamente à descrição textual dele; o anexo de menor benefício é a regra geral de
     * retaguarda para todos os demais produtos do mesmo NCM. Só o contribuinte sabe qual dos dois
     * é o caso, e ele afirma isso declarando o {@code cClassTrib} no documento — exatamente como
     * na NF-e. Por isso, em produto, o {@code cClassTrib} declarado prevalece sobre o NCM; sem
     * declaração (ou declarando código sem linha na tabela) vale o NCM, que carrega a redução de
     * retaguarda. Em serviço não há NCM: o {@code cClassTrib} é a única chave.
     *
     * <p>Em produto o código declarado não passa por checagem de admissibilidade — a do Anexo VIII
     * é por item da LC 116, só existe para serviço. Declarar código indevido é responsabilidade do
     * emitente, como na NF-e; por isso a linha na memória de cálculo, que deixa a escolha visível.
     */
    private RegimeDiferenciado regimeDoItem(MotorFiscalRequest req, boolean servico,
                                            List<String> memoria) {
        if (servico) {
            return tabela.regimeCClassTrib(req.getCClassTrib(), req.getDataCompetencia());
        }
        if (preenchido(req.getCClassTrib())) {
            RegimeDiferenciado declarado =
                    tabela.regimeCClassTrib(req.getCClassTrib(), req.getDataCompetencia());
            if (!Constants.REGIME_DIF_PADRAO.equals(declarado.name())) {
                memoria.add(Constants.FISCAL_MEMORIA_CCLASSTRIB_VENCE_NCM
                        .formatted(declarado.name(), req.getCClassTrib(), req.getNcm()));
                return declarado;
            }
        }
        return tabela.regimeNcm(req.getNcm(), req.getDataCompetencia());
    }

    /** Fator multiplicador da redução de alíquota: (1 − redução/100). */
    private BigDecimal fatorReducao(BigDecimal reducaoPercentual) {
        return BigDecimal.ONE.subtract(reducaoPercentual.divide(CEM));
    }

    /**
     * Fator de IBS e de CBS a aplicar sobre a alíquota de referência — item 7.7. Parte do fator
     * único de {@code regime.reducaoPercentual()} (default hoje, vale pros ~267 regimes que não
     * precisam de mais nada) e aplica por cima os overrides de {@code fiscal.aliquota_regime_tributo},
     * se houver, para os 2 casos que um percentual só não expressa: redução isolada por tributo
     * (Prouni, art. 308 — zera só CBS) e alíquota somada em valor ABSOLUTO (serviço financeiro,
     * art. 233 — soma IBS+CBS fixa por ano). Overrides de tenant/backend futuro entram aqui, sem
     * mexer no resto do motor.
     */
    private FatoresRegime fatoresEfetivos(RegimeDiferenciado regime, BigDecimal aliqIbsEst,
                                           BigDecimal aliqIbsMun, BigDecimal aliqCbs, int ano,
                                           List<String> memoria) {
        BigDecimal fatorPadrao = fatorReducao(regime.reducaoPercentual());
        BigDecimal fatorIbs = fatorPadrao;
        BigDecimal fatorCbs = fatorPadrao;

        for (RegimeTributoOverride override : tabela.overridesRegime(regime.name(), ano)) {
            if (Constants.FISCAL_TIPO_ALIQUOTA_ABSOLUTA.equals(override.tipo())) {
                // ponytail: a lei fixa a SOMA (art. 233), não o split IBS/CBS interno — escalamos
                // as 3 componentes de referência proporcionalmente pelo mesmo fator, preservando
                // o peso relativo entre elas. Upgrade: coluna própria se algum regime futuro
                // exigir alíquota absoluta por tributo em vez de só a soma.
                BigDecimal referenciaTotal = aliqIbsEst.add(aliqIbsMun).add(aliqCbs);
                BigDecimal fatorAbsoluto = referenciaTotal.signum() == 0
                        ? BigDecimal.ZERO
                        : override.valor().divide(referenciaTotal, 10, RoundingMode.HALF_UP);
                fatorIbs = fatorAbsoluto;
                fatorCbs = fatorAbsoluto;
                memoria.add("Override de regime (" + regime.name() + "): alíquota total travada em "
                        + override.valor() + "% (referência seria " + referenciaTotal + "%)");
            } else if (Constants.FISCAL_TRIBUTO_IBS.equals(override.tributo())) {
                fatorIbs = fatorReducao(override.valor());
                memoria.add("Override de regime (" + regime.name() + "): IBS com redução própria de "
                        + override.valor() + "%");
            } else if (Constants.FISCAL_TRIBUTO_CBS.equals(override.tributo())) {
                fatorCbs = fatorReducao(override.valor());
                memoria.add("Override de regime (" + regime.name() + "): CBS com redução própria de "
                        + override.valor() + "%");
            }
        }
        return new FatoresRegime(fatorIbs, fatorCbs);
    }

    private record FatoresRegime(BigDecimal fatorIbs, BigDecimal fatorCbs) {
    }

    /**
     * ICMS (produto) ou ISS (serviço) proporcional ao remanescente da transição (fatia 3c) — o
     * motor MULTIPLICA o tributo cheio pelo {@code pctRemanescente} da competência; nunca os
     * dois juntos, já que produto x serviço são mutuamente exclusivos desde o PASSO 0.
     * PIS/COFINS (vigente só em 2026) fica de fora por decisão de escopo (item 7.9): o art. 348 da
     * LC 214/2025 dispensa o recolhimento de IBS/CBS no ano de teste para quem cumpre as obrigações
     * acessórias, e exige PIS/COFINS integral do mesmo jeito — não há compensação para calcular numa
     * nota isolada, só na apuração multi-competência de quem descumprir, que fica fora do fiscal-service.
     */
    private Legado calcularLegado(MotorFiscalRequest req, boolean servico, BigDecimal valorTributavel,
                                   TransicaoAno transicao, String tenantId, List<String> memoria) {
        if (transicao.pctRemanescente().signum() == 0) {
            return Legado.NENHUM;
        }
        if (transicao.pisCofinsVigente()) {
            log.warn("{} (tenant={}, cfop={})", Constants.FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA, tenantId, req.getCfop());
            memoria.add(Constants.FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA);
        }
        BigDecimal fatorLegado = transicao.pctRemanescente().divide(CEM);

        if (servico) {
            AliquotaIss aliqIss = tabela
                    .aliquotaIss(req.getIbgeLocalPrestacao(), req.getCodigoServico(), req.getDataCompetencia())
                    .orElseThrow(() -> new FiscalException(Constants.FISCAL_ISS_SEM_COBERTURA));
            if (aliqIss.referenciaNacional()) {
                String aviso = Constants.FISCAL_AVISO_ALIQUOTA_REFERENCIA.formatted(req.getIbgeLocalPrestacao());
                log.warn("{} (tenant={}, cfop={})", aviso, tenantId, req.getCfop());
                memoria.add(aviso);
            }
            BigDecimal valorIss = pct(valorTributavel, aliqIss.aliquotaPct())
                    .multiply(fatorLegado).setScale(ESCALA, RoundingMode.HALF_UP);
            memoria.add("ISS legado (" + transicao.pctRemanescente() + "% remanescente): " + valorIss);
            return Legado.deIss(valorIss);
        }

        if (!preenchido(req.getUfOrigem()) || !preenchido(req.getUfDestino())) {
            throw new FiscalException(Constants.FISCAL_UF_OBRIGATORIA_TRANSICAO);
        }
        boolean interestadual = !req.getUfOrigem().equals(req.getUfDestino());

        // Issue #103, achado 2.2: sem os dois indicadores não dá pra saber se cabe DIFAL — 400 em
        // vez de assumir "sem DIFAL" calado. Só exigido em produto interestadual (mesmo recorte
        // do EC 87/2015 — operação interna e serviço não têm DIFAL de mercadoria).
        if (interestadual && (!preenchido(req.getIndFinal()) || !preenchido(req.getIndIEDest()))) {
            throw new FiscalException(Constants.FISCAL_DESTINATARIO_INDICADORES_OBRIGATORIOS);
        }

        RegimeIcms regimeIcms = interestadual
                ? new RegimeIcms(AliquotaInterestadual.de(req.getUfOrigem(), req.getUfDestino(), req.getOrigemProduto()),
                        BigDecimal.ZERO, false, BigDecimal.ZERO)
                : tabela.aliquotaIcms(tenantId, req.getNcm(), req.getUfOrigem(), req.getUfDestino(), req.getDataCompetencia())
                        .orElseThrow(() -> new FiscalException(Constants.FISCAL_ICMS_SEM_COBERTURA));
        BigDecimal aliqIcmsEfetiva = regimeIcms.aliqNominal().multiply(fatorReducao(regimeIcms.pReducaoBase()));
        BigDecimal icmsNominal = pct(valorTributavel, aliqIcmsEfetiva);
        BigDecimal valorIcms = icmsNominal.multiply(fatorLegado).setScale(ESCALA, RoundingMode.HALF_UP);
        memoria.add("ICMS legado (" + transicao.pctRemanescente() + "% remanescente): " + valorIcms);

        if (!interestadual) {
            // FCP da operação interna (achado 2.3, grupo ICMS00): pFcp é campo próprio da linha,
            // nunca junto do p_reducao_base — nas UFs que desmembram a alíquota cheia (RJ, SE) sai
            // à parte, senão o desmembramento faria o ICMS interno cair em silêncio.
            BigDecimal valorFcp = pct(valorTributavel, regimeIcms.pFcp())
                    .multiply(fatorLegado).setScale(ESCALA, RoundingMode.HALF_UP);
            if (regimeIcms.pFcp().signum() > 0) {
                memoria.add("FCP: " + valorFcp + " (alíquota " + regimeIcms.pFcp() + "%)");
            }
            return Legado.deIcmsInterno(valorIcms, regimeIcms.aliqNominal(), regimeIcms.pReducaoBase(),
                    regimeIcms.pFcp(), valorFcp);
        }

        // pICMSInter sai em TODA saída interestadual de produto (grupo sempre presente na NF-e);
        // DIFAL só quando o destinatário é consumidor final NÃO contribuinte (EC 87/2015, §5.1).
        BigDecimal pInter = regimeIcms.aliqNominal();
        boolean aplicaDifal = Constants.FISCAL_IND_FINAL_CONSUMIDOR_FINAL.equals(req.getIndFinal())
                && Constants.FISCAL_IND_IE_DEST_NAO_CONTRIBUINTE.equals(req.getIndIEDest());
        if (!aplicaDifal) {
            memoria.add("Sem DIFAL (indFinal=" + req.getIndFinal() + ", indIEDest=" + req.getIndIEDest()
                    + "): destinatário contribuinte ou não consumidor final");
            return Legado.deIcmsInterestadual(valorIcms, regimeIcms.aliqNominal(), pInter);
        }

        RegimeIcms internoDestino = tabela
                .aliquotaIcms(tenantId, req.getNcm(), req.getUfDestino(), req.getUfDestino(), req.getDataCompetencia())
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_ICMS_SEM_COBERTURA));
        String metodoBase = tabela.metodoBaseDifal(req.getUfDestino(), req.getDataCompetencia())
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_DIFAL_SEM_COBERTURA));
        BigDecimal pInternaDest = internoDestino.aliqNominal();
        BigDecimal pFcpDest = internoDestino.pFcp();

        BigDecimal vBcUfDest;
        BigDecimal vIcmsUfDestNominal;
        if (Constants.FISCAL_DIFAL_METODO_BASE_DUPLA.equals(metodoBase)) {
            // Conv. ICMS 236/2021: escala interna ≥ 10, arredondamento só no valor final.
            BigDecimal divisor = BigDecimal.ONE.subtract(pInternaDest.divide(CEM, 10, RoundingMode.HALF_UP));
            vBcUfDest = valorTributavel.subtract(icmsNominal).divide(divisor, 10, RoundingMode.HALF_UP);
            vIcmsUfDestNominal = pctEscala(vBcUfDest, pInternaDest, 10).subtract(icmsNominal);
        } else {
            vBcUfDest = valorTributavel;
            vIcmsUfDestNominal = pct(vBcUfDest, pInternaDest.subtract(pInter));
        }
        BigDecimal vFcpUfDestNominal = pctEscala(vBcUfDest, pFcpDest, 10);

        BigDecimal baseUfDestino = vBcUfDest.setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal valorIcmsUfDestino = vIcmsUfDestNominal.multiply(fatorLegado).setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal valorFcpUfDestino = vFcpUfDestNominal.multiply(fatorLegado).setScale(ESCALA, RoundingMode.HALF_UP);
        memoria.add("DIFAL (" + metodoBase + "): base destino " + baseUfDestino + ", ICMS destino "
                + valorIcmsUfDestino + ", FCP destino " + valorFcpUfDestino);

        return Legado.deDifal(valorIcms, regimeIcms.aliqNominal(), pInter, baseUfDestino, pInternaDest,
                pFcpDest, valorIcmsUfDestino, valorFcpUfDestino);
    }

    /**
     * Retenção na fonte (fatia 3e) — ISS/IRRF/CSRF/INSS, cada um só quando declarado no request
     * (padrão "declarado, não deduzido", igual ao cClassTrib). Piso de dispensa usa {@code <=}
     * uniformemente (CSRF já é "igual ou inferior" na IN 1234/2012; IRRF e INSS seguem o mesmo
     * corte por simplicidade). Campo fica {@code null} tanto quando não declarado quanto quando
     * dispensado pelo piso — o chamador não precisa distinguir os dois: em nenhum dos casos há
     * valor a reter.
     */
    private Retencao calcularRetencao(MotorFiscalRequest req, BigDecimal valorTributavel,
                                       BigDecimal valorIssLegado, String tenantId, List<String> memoria) {
        boolean pedeAlgo = Boolean.TRUE.equals(req.getIssRetidoNaFonte())
                || Boolean.TRUE.equals(req.getReterIrrf())
                || Boolean.TRUE.equals(req.getReterCsrf())
                || Boolean.TRUE.equals(req.getReterInss());
        if (!pedeAlgo) {
            return Retencao.NENHUMA;
        }

        BigDecimal issRetido = null;
        if (Boolean.TRUE.equals(req.getIssRetidoNaFonte())) {
            issRetido = valorIssLegado; // mesmo valor do ISS calculado no PASSO 3.5 — só muda quem paga
            memoria.add(issRetido != null
                    ? "ISS retido na fonte pelo tomador: " + issRetido
                    : "ISS retido na fonte: sem ISS legado nesta competência (nada a reter)");
        }

        BigDecimal irrf = null;
        if (Boolean.TRUE.equals(req.getReterIrrf())) {
            AliquotaRetencao aliq = tabela.retencao(tenantId, Constants.TRIBUTO_IRRF)
                    .orElseThrow(() -> new FiscalException(Constants.FISCAL_TRIBUTO_SEM_ALIQUOTA_RETENCAO));
            BigDecimal calculado = pct(valorTributavel, aliq.aliquotaPct());
            // Lei 13.137/2015 art. 67: piso é sobre o RETIDO acumulado no mês pro mesmo
            // prestador, não sobre a operação isolada — só o IRRF acumula nesta fatia.
            BigDecimal totalMes = ouZero(req.getValorAcumuladoMesIrrf()).add(calculado);
            if (totalMes.compareTo(aliq.valorMinimoBase()) <= 0) {
                memoria.add("IRRF dispensado: retido acumulado no mês (" + totalMes
                        + ") não supera o piso de " + aliq.valorMinimoBase());
            } else {
                irrf = calculado;
                memoria.add("IRRF retido: " + irrf + " (alíquota " + aliq.aliquotaPct() + "%)");
            }
        }

        BigDecimal csrf = null;
        if (Boolean.TRUE.equals(req.getReterCsrf())) {
            AliquotaRetencao aliq = tabela.retencao(tenantId, Constants.TRIBUTO_CSRF)
                    .orElseThrow(() -> new FiscalException(Constants.FISCAL_TRIBUTO_SEM_ALIQUOTA_RETENCAO));
            if (valorTributavel.compareTo(aliq.valorMinimoBase()) <= 0) {
                memoria.add("CSRF dispensado: valor bruto (" + valorTributavel
                        + ") não supera o piso de " + aliq.valorMinimoBase());
            } else {
                csrf = pct(valorTributavel, aliq.aliquotaPct());
                memoria.add("CSRF retido: " + csrf + " (alíquota " + aliq.aliquotaPct() + "%)");
            }
        }

        BigDecimal inss = null;
        if (Boolean.TRUE.equals(req.getReterInss())) {
            AliquotaRetencao aliq = tabela.retencao(tenantId, Constants.TRIBUTO_INSS)
                    .orElseThrow(() -> new FiscalException(Constants.FISCAL_TRIBUTO_SEM_ALIQUOTA_RETENCAO));
            if (valorTributavel.compareTo(aliq.valorMinimoBase()) <= 0) {
                memoria.add("INSS dispensado: valor bruto (" + valorTributavel
                        + ") não supera o piso de " + aliq.valorMinimoBase());
            } else {
                inss = pct(valorTributavel, aliq.aliquotaPct());
                memoria.add("INSS retido: " + inss + " (alíquota " + aliq.aliquotaPct() + "%)");
            }
        }

        return new Retencao(issRetido, irrf, csrf, inss);
    }

    /**
     * Crédito de entrada (item 4, §1.4.3) — quanto da entrada é creditável de IBS/CBS. IS nunca
     * credita (monofásico/cumulativo por desenho, fora daqui). Vedações: uso e consumo pessoal
     * zera o crédito inteiro; entrada cujo destino é saída desonerada credita só o complemento do
     * {@code percentualSaidaDesonerada}. Quem persiste saldo e aproveitamento é o
     * operacoes-service (AP) — aqui só o valor calculado, com a mesma memória de cálculo da saída.
     */
    private OperacaoFiscalDTO calcularCredito(MotorFiscalRequest req, CfopInfo cfop, boolean servico,
                                               BigDecimal valorTributavel, List<String> memoria) {
        if (Boolean.TRUE.equals(req.getUsoConsumoPessoal())) {
            memoria.add("Uso e consumo pessoal: entrada não gera crédito (§1.4.3)");
            return semCredito(valorTributavel, memoria);
        }

        String ibgeDestino = servico ? req.getIbgeLocalPrestacao() : req.getIbgeDestino();
        RegimeDiferenciado regime = regimeDoItem(req, servico, memoria);

        int ano = req.getDataCompetencia().getYear();
        AliquotaIbs aliqIbs = tabela.aliquotaIbs(ibgeDestino, ano)
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_VIGENCIA_SEM_COBERTURA));
        BigDecimal aliqCbs = tabela.aliquotaCbs(req.getRegimeEmpresa(), ano)
                .orElseThrow(() -> new FiscalException(Constants.FISCAL_REGIME_SEM_ALIQUOTA_CBS));

        FatoresRegime fatores = fatoresEfetivos(regime, aliqIbs.estadual(), aliqIbs.municipal(), aliqCbs, ano, memoria);
        BigDecimal aliqIbsEfetiva = aliqIbs.estadual().add(aliqIbs.municipal()).multiply(fatores.fatorIbs());
        BigDecimal aliqCbsEfetiva = aliqCbs.multiply(fatores.fatorCbs());

        BigDecimal fatorProporcional = BigDecimal.ONE
                .subtract(ouZero(req.getPercentualSaidaDesonerada()).divide(CEM));

        BigDecimal creditoIbs = cfop.geraCreditoIbs()
                ? pct(valorTributavel, aliqIbsEfetiva).multiply(fatorProporcional).setScale(ESCALA, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(ESCALA);
        BigDecimal creditoCbs = cfop.geraCreditoCbs()
                ? pct(valorTributavel, aliqCbsEfetiva).multiply(fatorProporcional).setScale(ESCALA, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(ESCALA);

        memoria.add("Regime: " + regime.name() + " (redução de alíquota " + regime.reducaoPercentual() + "%)");
        if (req.getPercentualSaidaDesonerada() != null) {
            memoria.add("Crédito proporcional: " + req.getPercentualSaidaDesonerada()
                    + "% da entrada destinada a saída desonerada");
        }
        memoria.add("Crédito IBS: " + creditoIbs + " | CBS: " + creditoCbs);

        return OperacaoFiscalDTO.builder()
                .baseCalculo(valorTributavel)
                .valorCreditoIbs(creditoIbs)
                .valorCreditoCbs(creditoCbs)
                .regimeAplicado(regime.name())
                .memoriaCalculo(memoria)
                .cClassTrib(req.getCClassTrib())
                .percentualIbsUf(aliqIbs.estadual().multiply(fatores.fatorIbs()))
                .percentualIbsMunicipal(aliqIbs.municipal().multiply(fatores.fatorIbs()))
                .percentualCbs(aliqCbs.multiply(fatores.fatorCbs()))
                .percentualReducaoAplicado(regime.reducaoPercentual())
                .build();
    }

    private OperacaoFiscalDTO semCredito(BigDecimal valorTributavel, List<String> memoria) {
        BigDecimal zero = BigDecimal.ZERO.setScale(ESCALA);
        return OperacaoFiscalDTO.builder()
                .baseCalculo(valorTributavel)
                .valorCreditoIbs(zero)
                .valorCreditoCbs(zero)
                .memoriaCalculo(memoria)
                .build();
    }

    /**
     * ICMS (produto) xor ISS (serviço) da transição — os dois {@code null} quando pctRemanescente = 0.
     * {@code percentualIcmsNominal}/{@code percentualReducaoBaseIcms}/{@code modalidadeBaseCalculoIcms}
     * só saem preenchidos no ramo ICMS (produto) — mesmo padrão de {@code icms}.
     *
     * <p>Campos de DIFAL/FCP (issue #103): {@code percentualFcp}/{@code valorFcp} são o FCP da
     * OPERAÇÃO INTERNA (grupo ICMS00), só no ramo sem interestadualidade. {@code percentualIcmsInterestadual}
     * sai em toda saída interestadual de produto; os demais (base/ICMS/FCP de destino, partilha,
     * ICMS do remetente) só quando o DIFAL se aplica (consumidor final não contribuinte).
     */
    private record Legado(BigDecimal icms, BigDecimal iss, BigDecimal percentualIcmsNominal,
                           BigDecimal percentualReducaoBaseIcms, String modalidadeBaseCalculoIcms,
                           BigDecimal percentualFcp, BigDecimal valorFcp,
                           BigDecimal percentualIcmsInterestadual,
                           BigDecimal baseCalculoUfDestino, BigDecimal baseCalculoFcpUfDestino,
                           BigDecimal percentualIcmsUfDestino, BigDecimal percentualFcpUfDestino,
                           BigDecimal percentualPartilhaDestino, BigDecimal valorIcmsUfDestino,
                           BigDecimal valorFcpUfDestino, BigDecimal valorIcmsUfRemetente) {

        private static final Legado NENHUM = new Legado(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        static Legado deIss(BigDecimal valorIss) {
            return new Legado(null, valorIss, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        static Legado deIcmsInterno(BigDecimal icms, BigDecimal aliqNominal, BigDecimal pReducaoBase,
                                     BigDecimal percentualFcp, BigDecimal valorFcp) {
            return new Legado(icms, null, aliqNominal, pReducaoBase, Constants.FISCAL_ICMS_MODBC_VALOR_OPERACAO,
                    percentualFcp, valorFcp, null, null, null, null, null, null, null, null, null);
        }

        static Legado deIcmsInterestadual(BigDecimal icms, BigDecimal aliqNominal, BigDecimal pInter) {
            return new Legado(icms, null, aliqNominal, BigDecimal.ZERO, Constants.FISCAL_ICMS_MODBC_VALOR_OPERACAO,
                    null, null, pInter, null, null, null, null, null, null, null, null);
        }

        static Legado deDifal(BigDecimal icms, BigDecimal aliqNominal, BigDecimal pInter, BigDecimal baseUfDestino,
                               BigDecimal pInternaDest, BigDecimal pFcpDest, BigDecimal valorIcmsUfDestino,
                               BigDecimal valorFcpUfDestino) {
            return new Legado(icms, null, aliqNominal, BigDecimal.ZERO, Constants.FISCAL_ICMS_MODBC_VALOR_OPERACAO,
                    null, null, pInter, baseUfDestino, baseUfDestino, pInternaDest, pFcpDest,
                    Constants.FISCAL_DIFAL_PARTILHA_DESTINO_INTEGRAL, valorIcmsUfDestino, valorFcpUfDestino,
                    BigDecimal.ZERO.setScale(ESCALA));
        }
    }

    /** Valores retidos na fonte — cada campo {@code null} quando não declarado ou dispensado pelo piso. */
    private record Retencao(BigDecimal issRetido, BigDecimal irrf, BigDecimal csrf, BigDecimal inss) {
        private static final Retencao NENHUMA = new Retencao(null, null, null, null);
    }
}