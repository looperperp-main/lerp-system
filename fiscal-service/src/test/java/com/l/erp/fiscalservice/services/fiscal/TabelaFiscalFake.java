package com.l.erp.fiscalservice.services.fiscal;

import com.l.erp.common.util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Conteúdo fiscal fixo para o oráculo do motor (Fin.md §1.4.8).
 *
 * <p>Era a implementação de produção {@code TabelaFiscalInMemory} da Fatia 1; virou fixture de
 * teste quando o conteúdo real passou a vir de {@code fiscal.*} via {@link TabelaFiscalJdbc}.
 * Mantido para que {@code MotorFiscalServiceTest} continue testando ARITMÉTICA — sem banco e
 * sem contexto Spring. O acesso a dados tem teste próprio em {@code TabelaFiscalJdbcTest}.
 *
 * <p>Os valores espelham o seed de {@code fiscal-schema-002.yaml}; casamento exato aqui (prefixo
 * mais longo é problema do SQL, não deste fake).
 */
public class TabelaFiscalFake implements TabelaFiscal {

    private final Map<String, CfopInfo> cfopMap = new HashMap<>();
    private final Map<String, RegimeDiferenciado> ncmMap = new HashMap<>();
    private final Map<String, RegimeDiferenciado> servicoMap = new HashMap<>();
    private final Set<String> paresAdmitidos = new HashSet<>();
    private final Map<String, AliquotaIbs> ibsMap = new HashMap<>();
    private final Map<String, BigDecimal> cbsMap = new HashMap<>();
    private final Map<String, BigDecimal> isMap = new HashMap<>();
    private final Map<Integer, TransicaoAno> transicaoMap = new HashMap<>();
    private final Map<String, AliquotaIss> issMap = new HashMap<>();
    private final Map<String, RegimeIcms> matrizMap = new HashMap<>();
    private final Map<String, AliquotaRetencao> retencaoMap = new HashMap<>();
    private final Map<String, List<RegimeTributoOverride>> overridesMap = new HashMap<>();

    public TabelaFiscalFake() {
        cfopMap.put("5101", new CfopInfo("5101", TipoOperacaoFiscal.SAIDA, true, true, true));
        cfopMap.put("5102", new CfopInfo("5102", TipoOperacaoFiscal.SAIDA, true, true, false));
        cfopMap.put("5405", new CfopInfo("5405", TipoOperacaoFiscal.SAIDA, true, true, false));
        cfopMap.put("5933", new CfopInfo("5933", TipoOperacaoFiscal.SAIDA, true, true, false));
        // ENTRADA, com as mesmas flags do cfop.csv: compra para comercialização gera crédito de IBS
        // e de CBS e não é 1ª etapa da cadeia (item 4 — crédito de entrada).
        cfopMap.put("1102", new CfopInfo("1102", TipoOperacaoFiscal.ENTRADA, true, true, false));
        // ENTRADA sem direito a crédito pelo próprio CFOP (ex.: uso e consumo) — vedação distinta
        // de usoConsumoPessoal declarado no request: aqui é o CFOP em si que não credita.
        cfopMap.put("1556", new CfopInfo("1556", TipoOperacaoFiscal.ENTRADA, false, false, false));

        ncmMap.put("10063021", RegimeDiferenciado.de("ANEXO_I_ZERO", new BigDecimal("100"))); // arroz
        ncmMap.put("24022000", RegimeDiferenciado.de(Constants.REGIME_DIF_MONOFASICO, BigDecimal.ZERO));

        // 200029 = "Redução de 60% ... (Anexo III)" — serviços de saúde do item 4.01 da LC 116.
        servicoMap.put("200029", RegimeDiferenciado.de("ANEXO_III_60", new BigDecimal("60")));
        paresAdmitidos.add(par("4.01", "200029"));   // Anexo VIII: saúde humana
        // 000001 = tributação integral. Tem linha em regime_cclasstrib com redução 0 (fiscal-019),
        // logo o regime aplicado é INTEGRAL — não PADRAO, que é o fallback de cClassTrib SEM linha lá.
        servicoMap.put("000001", RegimeDiferenciado.de("INTEGRAL", BigDecimal.ZERO));
        paresAdmitidos.add(par("1.01", "000001"));   // Anexo VIII: análise/desenvolvimento de sistemas

        // 2033 = regime permanente, com as alíquotas REAIS do portal do piloto CBS (16,0 + 2,5 de
        // IBS e 8,5 de CBS; ver fiscal-022). Não é 2027 de propósito: na transição a referência é
        // simbólica (0,05% + 0,05% / 8,4%) e não exercita arredondamento nem redução.
        // referenciaNacional = false: aqui é linha própria do município. A precedência
        // própria x referência é do SQL, testada no TabelaFiscalJdbcTest.
        ibsMap.put(chave("3550308", 2033),
                new AliquotaIbs(new BigDecimal("16.00"), new BigDecimal("2.50"), false));
        // Rio: mesmos percentuais, mas vindos da linha de referência (fiscal-023). Serve ao caso H3
        // — o motor tem que avisar sem mudar o número.
        ibsMap.put(chave("3304557", 2033),
                new AliquotaIbs(new BigDecimal("16.00"), new BigDecimal("2.50"), true));
        cbsMap.put(chave(Constants.REGIME_LUCRO_REAL, 2033), new BigDecimal("8.50"));
        isMap.put("24022000", new BigDecimal("150"));

        // Curva da transição inteira (as 8 linhas do fiscal-025): aqui não vale escolher ano, é a
        // tabela toda — quem for exercitar 3c precisa do degrau de cada competência. Ano fora
        // disso (2025, 2035) fica sem linha de propósito: o motor não pode assumir 100% nem 0%.
        transicaoMap.put(2026, new TransicaoAno(new BigDecimal("100.00"), true));
        transicaoMap.put(2027, new TransicaoAno(new BigDecimal("100.00"), false));
        transicaoMap.put(2028, new TransicaoAno(new BigDecimal("100.00"), false));
        transicaoMap.put(2029, new TransicaoAno(new BigDecimal("90.00"), false));
        transicaoMap.put(2030, new TransicaoAno(new BigDecimal("80.00"), false));
        transicaoMap.put(2031, new TransicaoAno(new BigDecimal("70.00"), false));
        transicaoMap.put(2032, new TransicaoAno(new BigDecimal("60.00"), false));
        transicaoMap.put(2033, new TransicaoAno(new BigDecimal("0.00"), false));

        // ISS (fatia 3d): só a referência (teto de 5% da LC 116 art. 8-A, fiscal-029) — nenhum
        // município tem alíquota própria carregada ainda. Ainda não consumido pelo motor (3c).
        issMap.put(chaveIss(Constants.FISCAL_IBGE_REFERENCIA_NACIONAL, null),
                new AliquotaIss(new BigDecimal("5.00"), true));

        // Matriz ICMS (fatia 3b): só a linha-base nacional (fallback de NCM) de SP — mesmo
        // recorte da carga real (27 linhas, uma por UF, sem override de tenant ainda). Precedência
        // dos 4 níveis é testada no TabelaFiscalJdbcTest; aqui é só o suficiente pro oráculo.
        matrizMap.put(chaveMatriz(null, Constants.FISCAL_NCM_NBS_FALLBACK, "SP", "SP"),
                new RegimeIcms(new BigDecimal("18.00"), BigDecimal.ZERO, true));

        // 2029 = degrau intermediário da transição (pctRemanescente 90%, fiscal-025) — só pra
        // exercitar o legado (fatia 3c) fora do regime permanente de 2033. Mesmos valores de
        // 2033 por simplicidade (o fake não modela a alíquota IBS/CBS variando ano a ano).
        ibsMap.put(chave("3550308", 2029),
                new AliquotaIbs(new BigDecimal("16.00"), new BigDecimal("2.50"), false));
        cbsMap.put(chave(Constants.REGIME_LUCRO_REAL, 2029), new BigDecimal("8.50"));

        // Ano de teste 2026 (item 7.9): alíquota REAL da curva de transição (fiscal-schema-007,
        // fonte piloto CBS/RFB) — 0,10% estadual + 0,00% municipal de IBS, 0,90% de CBS, só
        // referência nacional (nenhum município legislou ainda).
        ibsMap.put(chave("3550308", 2026),
                new AliquotaIbs(new BigDecimal("0.10"), new BigDecimal("0.00"), true));
        cbsMap.put(chave(Constants.REGIME_LUCRO_REAL, 2026), new BigDecimal("0.90"));

        // Retenção (fatia 3e): só a linha-base nacional (tenant_id NULL) de cada tributo — igual
        // ao recorte do ISS/ICMS acima, sem override de tenant nesta carga.
        retencaoMap.put(chaveRetencao(null, Constants.TRIBUTO_IRRF),
                new AliquotaRetencao(new BigDecimal("1.50"), new BigDecimal("10.00")));
        retencaoMap.put(chaveRetencao(null, Constants.TRIBUTO_CSRF),
                new AliquotaRetencao(new BigDecimal("4.65"), new BigDecimal("5000.00")));
        retencaoMap.put(chaveRetencao(null, Constants.TRIBUTO_INSS),
                new AliquotaRetencao(new BigDecimal("11.00"), BigDecimal.ZERO));

        // Overrides de regime (item 7.7) — os 2 casos que reducaoPercentual sozinho não expressa.
        // PROUNI (art. 308): zera só a CBS; sem linha de IBS aqui = IBS segue na referência cheia.
        // Item "08.01" = par admitido real do Anexo VIII (servico-cclasstrib.csv linha 89).
        servicoMap.put("200025", RegimeDiferenciado.de("PROUNI", BigDecimal.ZERO));
        paresAdmitidos.add(par("08.01", "200025"));
        overridesMap.put("PROUNI", List.of(
                new RegimeTributoOverride(Constants.FISCAL_TRIBUTO_CBS,
                        Constants.FISCAL_TIPO_PERCENTUAL_REDUCAO, new BigDecimal("100"))));
        // Serviço financeiro (art. 233): soma IBS+CBS travada em valor absoluto, por ano. Item
        // "15.01" = par admitido real do Anexo VIII (servico-cclasstrib.csv linha 164). Valor do
        // teste é 13,50 (metade da referência 27,00 = 16,00+2,50+8,50 de 2033), não o 10,85% real
        // da curva do art. 233 — escolhido só pra dividir exato e testar a ARITMÉTICA do rateio
        // proporcional sem depender de arredondamento; o valor real vive no seed do Liquibase.
        servicoMap.put("010002", RegimeDiferenciado.de("SERVICO_FINANCEIRO", BigDecimal.ZERO));
        paresAdmitidos.add(par("15.01", "010002"));
        overridesMap.put("SERVICO_FINANCEIRO", List.of(
                new RegimeTributoOverride(Constants.FISCAL_TRIBUTO_TOTAL,
                        Constants.FISCAL_TIPO_ALIQUOTA_ABSOLUTA, new BigDecimal("13.50"))));
    }

    @Override
    public Optional<CfopInfo> cfop(String cfop) {
        return Optional.ofNullable(cfopMap.get(cfop));
    }

    /** Sem versão por data no fake (item 7.8) — só a linha corrente; real é o JdbcTest. */
    @Override
    public RegimeDiferenciado regimeNcm(String ncm, LocalDate competencia) {
        return ncmMap.getOrDefault(ncm, RegimeDiferenciado.PADRAO);
    }

    /** Sem versão por data no fake (item 7.8) — só a linha corrente; real é o JdbcTest. */
    @Override
    public RegimeDiferenciado regimeCClassTrib(String cClassTrib, LocalDate competencia) {
        return servicoMap.getOrDefault(cClassTrib, RegimeDiferenciado.PADRAO);
    }

    /** Match literal: a normalização do item ('4.01' x '04.01') é do SQL, testada no JdbcTest. */
    @Override
    public boolean cClassTribAdmitido(String itemLc116, String cClassTrib) {
        return paresAdmitidos.contains(par(itemLc116, cClassTrib));
    }

    @Override
    public Optional<AliquotaIbs> aliquotaIbs(String ibgeMunicipio, int ano) {
        return Optional.ofNullable(ibsMap.get(chave(ibgeMunicipio, ano)));
    }

    @Override
    public Optional<BigDecimal> aliquotaCbs(String regimeEmpresa, int ano) {
        return Optional.ofNullable(cbsMap.get(chave(regimeEmpresa, ano)));
    }

    @Override
    public Optional<BigDecimal> aliquotaIs(String ncm, LocalDate competencia) {
        return Optional.ofNullable(isMap.get(ncm));
    }

    @Override
    public Optional<TransicaoAno> transicao(int ano) {
        return Optional.ofNullable(transicaoMap.get(ano));
    }

    /** Item próprio > item genérico do município > referência — precedência real é do SQL/JdbcTest. */
    @Override
    public Optional<AliquotaIss> aliquotaIss(String ibgeMunicipio, String itemLc116, LocalDate competencia) {
        AliquotaIss porItem = issMap.get(chaveIss(ibgeMunicipio, itemLc116));
        if (porItem != null) {
            return Optional.of(porItem);
        }
        return Optional.ofNullable(issMap.get(chaveIss(Constants.FISCAL_IBGE_REFERENCIA_NACIONAL, null)));
    }

    /** 4 níveis (tenant+ncm > tenant+fallback > nacional+ncm > nacional+fallback); real é o SQL/JdbcTest. */
    @Override
    public Optional<RegimeIcms> aliquotaIcms(String tenantId, String ncmNbs, String ufOrigem, String ufDestino,
                                              LocalDate competencia) {
        if (tenantId != null) {
            RegimeIcms doTenant = matrizMap.get(chaveMatriz(tenantId, ncmNbs, ufOrigem, ufDestino));
            if (doTenant != null) {
                return Optional.of(doTenant);
            }
            RegimeIcms doTenantFallback =
                    matrizMap.get(chaveMatriz(tenantId, Constants.FISCAL_NCM_NBS_FALLBACK, ufOrigem, ufDestino));
            if (doTenantFallback != null) {
                return Optional.of(doTenantFallback);
            }
        }
        RegimeIcms nacional = matrizMap.get(chaveMatriz(null, ncmNbs, ufOrigem, ufDestino));
        if (nacional != null) {
            return Optional.of(nacional);
        }
        return Optional.ofNullable(
                matrizMap.get(chaveMatriz(null, Constants.FISCAL_NCM_NBS_FALLBACK, ufOrigem, ufDestino)));
    }

    /** Tenant > nacional; real é o SQL/JdbcTest. */
    @Override
    public Optional<AliquotaRetencao> retencao(String tenantId, String tributo) {
        if (tenantId != null) {
            AliquotaRetencao doTenant = retencaoMap.get(chaveRetencao(tenantId, tributo));
            if (doTenant != null) {
                return Optional.of(doTenant);
            }
        }
        return Optional.ofNullable(retencaoMap.get(chaveRetencao(null, tributo)));
    }

    /** Sem versão por ano no fake — os 2 cenários de teste valem pra qualquer ano (real é o JdbcTest). */
    @Override
    public List<RegimeTributoOverride> overridesRegime(String regime, int ano) {
        return overridesMap.getOrDefault(regime, List.of());
    }

    private static String chave(String valor, int ano) {
        return valor + ":" + ano;
    }

    private static String par(String itemLc116, String cClassTrib) {
        return itemLc116 + "|" + cClassTrib;
    }

    private static String chaveIss(String ibgeMunicipio, String itemLc116) {
        return ibgeMunicipio + "|" + itemLc116;
    }

    private static String chaveMatriz(String tenantId, String ncmNbs, String ufOrigem, String ufDestino) {
        return tenantId + "|" + ncmNbs + "|" + ufOrigem + "|" + ufDestino;
    }

    private static String chaveRetencao(String tenantId, String tributo) {
        return tenantId + "|" + tributo;
    }
}
