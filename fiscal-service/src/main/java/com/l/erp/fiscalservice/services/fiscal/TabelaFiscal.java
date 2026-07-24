package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Fonte de conteúdo fiscal (CFOP, regime por NCM/serviço, alíquotas IBS/CBS/IS).
 * Seam de extensão: a Fatia 1 usa {@link TabelaFiscalInMemory}; a fatia com DB
 * troca por uma implementação sobre {@code fiscal.aliq_*}/{@code fiscal.cfop}/{@code fiscal.ncm}.
 */
public interface TabelaFiscal {

    Optional<CfopInfo> cfop(String cfop);

    /** Regime do NCM; PADRAO quando não cadastrado (warning, não bloqueia — MF-10). */
    RegimeDiferenciado regimeNcm(String ncm);

    /** Regime do serviço (código LC 116); PADRAO quando não cadastrado. */
    RegimeDiferenciado regimeServico(String codigoServico);

    Optional<AliquotaIbs> aliquotaIbs(String ibgeMunicipio, int ano);

    Optional<BigDecimal> aliquotaCbs(String regimeEmpresa, int ano);

    Optional<BigDecimal> aliquotaIs(String ncm);
}