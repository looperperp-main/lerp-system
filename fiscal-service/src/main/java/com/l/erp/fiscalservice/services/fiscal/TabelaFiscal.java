package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Fonte de conteúdo fiscal (CFOP, regime por NCM/serviço, alíquotas IBS/CBS/IS).
 * Implementação de produção: {@link TabelaFiscalJdbc}, sobre as tabelas de referência
 * {@code fiscal.cfop}/{@code fiscal.regime_dif_ncm}/{@code fiscal.aliq_*} (Fatia A1).
 */
public interface TabelaFiscal {

    Optional<CfopInfo> cfop(String cfop);

    /** Regime do NCM; PADRAO quando não cadastrado (warning, não bloqueia — MF-10). */
    RegimeDiferenciado regimeNcm(String ncm);

    /**
     * Regime do serviço pelo cClassTrib DECLARADO na nota; PADRAO quando não cadastrado.
     *
     * <p>Não recebe o código LC 116: o mesmo serviço tem cClassTrib diferente conforme o contexto
     * da operação (prestado à administração pública vira 200043), então a classificação é declarada
     * no documento, não deduzida do cadastro — igual à NF-e. Os cClassTrib admitidos por item
     * LC 116 estão em {@code fiscal.servico_cclasstrib} (Anexo VIII), para o cadastro escolher.
     */
    RegimeDiferenciado regimeCClassTrib(String cClassTrib);

    /**
     * O Anexo VIII admite este cClassTrib para este item LC 116?
     *
     * <p>Separado de {@link #regimeCClassTrib} de propósito: "código não admitido" é erro do
     * emitente (400) e "admitido mas sem percentual cadastrado" é PADRAO. Misturar os dois num
     * retorno só transformaria hotelaria (200048, legítimo e ainda sem percentual) em rejeição.
     */
    boolean cClassTribAdmitido(String itemLc116, String cClassTrib);

    Optional<AliquotaIbs> aliquotaIbs(String ibgeMunicipio, int ano);

    Optional<BigDecimal> aliquotaCbs(String regimeEmpresa, int ano);

    Optional<BigDecimal> aliquotaIs(String ncm);

    /**
     * Curva da transição no ano: quanto de ICMS/ISS ainda é devido e se PIS/COFINS ainda incidem.
     *
     * <p>Vazio para ano fora de 2026-2033 — o motor devolve 400 em vez de assumir 100% ou 0%,
     * mesmo princípio de {@link #aliquotaIbs}. Consumida pelo motor na fatia 3c.
     */
    Optional<TransicaoAno> transicao(int ano);

    /**
     * Alíquota de ISS do município do local da prestação, pelo item LC 116. Vazio quando nem o
     * município nem a referência têm linha para o item — hoje só a referência (teto de 5% da
     * LC 116 art. 8-A) está carregada. Consumida pelo motor na fatia 3c.
     */
    Optional<AliquotaIss> aliquotaIss(String ibgeMunicipio, String itemLc116);

    /**
     * Alíquota interna de ICMS pela matriz {@code fiscal.matriz_tributaria} (fatia 3b), busca em
     * 4 níveis — override do tenant vence base nacional, NCM/NBS específico vence o fallback
     * {@code Constants.FISCAL_NCM_NBS_FALLBACK}: (tenant, ncm) > (tenant, fallback) >
     * (nacional, ncm) > (nacional, fallback). Vazio quando nenhum dos quatro casa — o motor
     * devolve 400, nunca assume alíquota zero. Consumida pelo motor na fatia 3c.
     *
     * <p>{@code tenantId} pode ser {@code null} (sem override, só a base nacional é elegível).
     * Hoje só a base nacional (27 linhas, uma por UF, sem override de NCM) está carregada.
     */
    Optional<RegimeIcms> aliquotaIcms(String tenantId, String ncmNbs, String ufOrigem, String ufDestino);

    /**
     * Alíquota e piso de dispensa de um tributo retido na fonte (fatia 3e — IRRF, CSRF, INSS;
     * {@code Constants.TRIBUTO_*}). Busca em 2 níveis, igual ao ISS: override do tenant vence a
     * linha nacional ({@code tenant_id IS NULL}). Vazio quando nem o tenant nem a base nacional
     * têm linha — o motor devolve 400 em vez de deixar de reter calado.
     *
     * <p>{@code tenantId} pode ser {@code null} (sem override, só a base nacional é elegível).
     */
    Optional<AliquotaRetencao> retencao(String tenantId, String tributo);

    /**
     * Overrides de {@code fiscal.aliquota_regime_tributo} (item 7.7) para o regime e ano — vazio
     * quando o regime não tem override (a imensa maioria: continuam só com o {@code fator} único
     * de {@link RegimeDiferenciado#reducaoPercentual()}). Cobre os 2 casos que um percentual só
     * não expressa: redução isolada por tributo (Prouni, art. 308) e alíquota somada em valor
     * absoluto (serviço financeiro, art. 233, curva por ano). Consumida pelo motor na fatia 3d.
     */
    List<RegimeTributoOverride> overridesRegime(String regime, int ano);
}