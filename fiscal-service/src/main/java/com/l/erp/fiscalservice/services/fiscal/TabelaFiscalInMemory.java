package com.l.erp.fiscalservice.services.fiscal;

import com.l.erp.common.util.Constants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Conteúdo fiscal seedado em memória.
 *
 * <p>ponytail: valores ESTIMADOS do Fin.md §1.4.8/§1.8 (SP 2027, cigarro, cesta básica,
 * redução 60%). Só valem de verdade após publicação do CGIBS. Trocar por lookup nas
 * tabelas Liquibase {@code fiscal.cfop}/{@code fiscal.ncm}/{@code fiscal.aliq_*} na fatia com DB.
 */
@Component
public class TabelaFiscalInMemory implements TabelaFiscal {

    private final Map<String, CfopInfo> cfopMap = new HashMap<>();
    private final Map<String, RegimeDiferenciado> ncmMap = new HashMap<>();
    private final Map<String, RegimeDiferenciado> servicoMap = new HashMap<>();
    private final Map<String, AliquotaIbs> ibsMap = new HashMap<>();
    private final Map<String, BigDecimal> cbsMap = new HashMap<>();
    private final Map<String, BigDecimal> isMap = new HashMap<>();

    public TabelaFiscalInMemory() {
        // CFOPs de saída (primeiraEtapaCadeia: produção própria/importação = 1ª etapa)
        cfopMap.put("5101", new CfopInfo("5101", TipoOperacaoFiscal.SAIDA, true, true, true));  // venda produção própria
        cfopMap.put("5102", new CfopInfo("5102", TipoOperacaoFiscal.SAIDA, true, true, false)); // revenda
        cfopMap.put("5405", new CfopInfo("5405", TipoOperacaoFiscal.SAIDA, true, true, false)); // revenda monofásico/ST
        cfopMap.put("5933", new CfopInfo("5933", TipoOperacaoFiscal.SAIDA, true, true, false)); // prestação de serviço

        // Regime diferenciado por NCM (exato nesta fatia; match por prefixo mais longo — §1.8-A — na fatia com DB)
        ncmMap.put("10063021", RegimeDiferenciado.CESTA_BASICA); // arroz
        ncmMap.put("24022000", RegimeDiferenciado.MONOFASICO);   // cigarro

        // Regime por serviço (código LC 116)
        servicoMap.put("4.01", RegimeDiferenciado.REDUCAO_60);   // saúde

        // Alíquota IBS por município + ano (São Paulo, 2027 — estimado)
        ibsMap.put(chaveIbs("3550308", 2027), new AliquotaIbs(new BigDecimal("13.12"), new BigDecimal("4.50")));

        // Alíquota CBS por regime + ano (estimado)
        cbsMap.put(chaveCbs(Constants.REGIME_LUCRO_REAL, 2027), new BigDecimal("8.80"));

        // Alíquota IS por NCM (cigarro — estimado)
        isMap.put("24022000", new BigDecimal("150"));
    }

    @Override
    public Optional<CfopInfo> cfop(String cfop) {
        return Optional.ofNullable(cfopMap.get(cfop));
    }

    @Override
    public RegimeDiferenciado regimeNcm(String ncm) {
        return ncmMap.getOrDefault(ncm, RegimeDiferenciado.PADRAO);
    }

    @Override
    public RegimeDiferenciado regimeServico(String codigoServico) {
        return servicoMap.getOrDefault(codigoServico, RegimeDiferenciado.PADRAO);
    }

    @Override
    public Optional<AliquotaIbs> aliquotaIbs(String ibgeMunicipio, int ano) {
        return Optional.ofNullable(ibsMap.get(chaveIbs(ibgeMunicipio, ano)));
    }

    @Override
    public Optional<BigDecimal> aliquotaCbs(String regimeEmpresa, int ano) {
        return Optional.ofNullable(cbsMap.get(chaveCbs(regimeEmpresa, ano)));
    }

    @Override
    public Optional<BigDecimal> aliquotaIs(String ncm) {
        return Optional.ofNullable(isMap.get(ncm));
    }

    private static String chaveIbs(String ibgeMunicipio, int ano) {
        return ibgeMunicipio + ":" + ano;
    }

    private static String chaveCbs(String regimeEmpresa, int ano) {
        return regimeEmpresa + ":" + ano;
    }
}