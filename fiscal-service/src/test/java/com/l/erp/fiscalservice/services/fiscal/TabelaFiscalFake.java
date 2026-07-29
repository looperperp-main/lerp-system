package com.l.erp.fiscalservice.services.fiscal;

import com.l.erp.common.util.Constants;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
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

    public TabelaFiscalFake() {
        cfopMap.put("5101", new CfopInfo("5101", TipoOperacaoFiscal.SAIDA, true, true, true));
        cfopMap.put("5102", new CfopInfo("5102", TipoOperacaoFiscal.SAIDA, true, true, false));
        cfopMap.put("5405", new CfopInfo("5405", TipoOperacaoFiscal.SAIDA, true, true, false));
        cfopMap.put("5933", new CfopInfo("5933", TipoOperacaoFiscal.SAIDA, true, true, false));

        ncmMap.put("10063021", RegimeDiferenciado.de("ANEXO_I_ZERO", new BigDecimal("100"))); // arroz
        ncmMap.put("24022000", RegimeDiferenciado.de(Constants.REGIME_DIF_MONOFASICO, BigDecimal.ZERO));

        // 200029 = "Redução de 60% ... (Anexo III)" — serviços de saúde do item 4.01 da LC 116.
        servicoMap.put("200029", RegimeDiferenciado.de("ANEXO_III_60", new BigDecimal("60")));
        paresAdmitidos.add(par("4.01", "200029"));   // Anexo VIII: saúde humana

        ibsMap.put(chave("3550308", 2027), new AliquotaIbs(new BigDecimal("13.12"), new BigDecimal("4.50")));
        cbsMap.put(chave(Constants.REGIME_LUCRO_REAL, 2027), new BigDecimal("8.80"));
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
    public RegimeDiferenciado regimeCClassTrib(String cClassTrib) {
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
    public Optional<BigDecimal> aliquotaIs(String ncm) {
        return Optional.ofNullable(isMap.get(ncm));
    }

    private static String chave(String valor, int ano) {
        return valor + ":" + ano;
    }

    private static String par(String itemLc116, String cClassTrib) {
        return itemLc116 + "|" + cClassTrib;
    }
}
