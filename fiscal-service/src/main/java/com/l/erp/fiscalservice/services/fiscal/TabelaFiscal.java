package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;
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
}