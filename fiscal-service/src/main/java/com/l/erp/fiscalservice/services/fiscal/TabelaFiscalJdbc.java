package com.l.erp.fiscalservice.services.fiscal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Conteúdo fiscal lido das tabelas de referência {@code fiscal.*} (Fatia A1),
 * no lugar do seed em memória da Fatia 1.
 *
 * <p>São dados NACIONAIS: sem tenant e sem escrita — schema e conteúdo pertencem ao
 * {@code liquibase-service}. Consultas read-only, por isso JdbcClient e não JPA.
 *
 * <p><b>Casamento por prefixo mais longo</b> (§1.8-A): a lei publica o código na granularidade
 * dela ({@code '1006.30'}, posição), a NF-e traz o NCM completo de 8 dígitos
 * ({@code '10063021'}). Os dois lados são normalizados a dígitos e a linha mais específica
 * que prefixa o código da nota vence — assim uma regra de subposição sobrepõe a da posição.
 */
@Component
public class TabelaFiscalJdbc implements TabelaFiscal {

    private static final String SQL_CFOP = """
            SELECT codigo, tipo_operacao, gera_credito_ibs, gera_credito_cbs, primeira_etapa_cadeia
              FROM fiscal.cfop
             WHERE codigo = :codigo
            """;

    // ponytail: vigência corrente = vigente_ate IS NULL. A interface não recebe data aqui;
    // quando existirem duas versões da mesma regra, passar a dataCompetencia e filtrar
    // por vigente_de/vigente_ate.
    private static final String SQL_REGIME_NCM = """
            SELECT regime, percentual_reducao
              FROM fiscal.regime_dif_ncm
             WHERE ncm IS NOT NULL
               AND vigente_ate IS NULL
               AND :ncm LIKE replace(ncm, '.', '') || '%'
             ORDER BY length(replace(ncm, '.', '')) DESC
             LIMIT 1
            """;

    // Match EXATO, ao contrário do NCM: cClassTrib é código fechado do Anexo VIII ('200029'), não
    // hierarquia — '200029' não é filho de '2000'. Prefixo aqui só produziria falso positivo.
    private static final String SQL_REGIME_CCLASSTRIB = """
            SELECT regime, percentual_reducao
              FROM fiscal.regime_cclasstrib
             WHERE cclasstrib = :cclasstrib
               AND vigente_ate IS NULL
            """;

    // lpad: a LC 116 escreve o item sem zero à esquerda ('4.01') e o Anexo VIII com ele ('04.01').
    // Sem normalizar, um serviço de saúde legítimo seria rejeitado por diferença de formatação.
    private static final String SQL_CCLASSTRIB_ADMITIDO = """
            SELECT 1
              FROM fiscal.servico_cclasstrib
             WHERE item_lc116 = lpad(:item, 5, '0')
               AND cclasstrib = :cclasstrib
            """;

    private static final String SQL_ALIQ_IBS = """
            SELECT aliquota_estadual, aliquota_municipal
              FROM fiscal.aliq_ibs_municipio
             WHERE ibge_municipio = :ibge
               AND ano_vigencia = :ano
            """;

    private static final String SQL_ALIQ_CBS = """
            SELECT aliquota_pct
              FROM fiscal.aliq_cbs_regime
             WHERE regime = :regime
               AND ano_vigencia = :ano
            """;

    // aliquota_pct IS NOT NULL: o Anexo XVII lista NCM sujeitos ao IS cuja alíquota ainda não foi
    // regulamentada. Essas linhas existem como catálogo e o motor as ignora — melhor não destacar
    // IS do que destacar um valor inventado.
    private static final String SQL_ALIQ_IS = """
            SELECT aliquota_pct
              FROM fiscal.aliq_is_ncm
             WHERE aliquota_pct IS NOT NULL
               AND vigente_ate IS NULL
               AND :ncm LIKE replace(ncm, '.', '') || '%'
             ORDER BY length(replace(ncm, '.', '')) DESC
             LIMIT 1
            """;

    private final JdbcClient jdbc;

    public TabelaFiscalJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CfopInfo> cfop(String cfop) {
        return jdbc.sql(SQL_CFOP)
                .param("codigo", cfop)
                .query((rs, n) -> new CfopInfo(
                        rs.getString("codigo"),
                        TipoOperacaoFiscal.valueOf(rs.getString("tipo_operacao")),
                        rs.getBoolean("gera_credito_ibs"),
                        rs.getBoolean("gera_credito_cbs"),
                        rs.getBoolean("primeira_etapa_cadeia")))
                .optional();
    }

    @Override
    public RegimeDiferenciado regimeNcm(String ncm) {
        return jdbc.sql(SQL_REGIME_NCM)
                .param("ncm", somenteDigitos(ncm))
                .query(TabelaFiscalJdbc::regimeDaLinha)
                .optional()
                .orElse(RegimeDiferenciado.PADRAO);
    }

    @Override
    public RegimeDiferenciado regimeCClassTrib(String cClassTrib) {
        return jdbc.sql(SQL_REGIME_CCLASSTRIB)
                .param("cclasstrib", cClassTrib)
                .query(TabelaFiscalJdbc::regimeDaLinha)
                .optional()
                .orElse(RegimeDiferenciado.PADRAO);
    }

    @Override
    public boolean cClassTribAdmitido(String itemLc116, String cClassTrib) {
        return jdbc.sql(SQL_CCLASSTRIB_ADMITIDO)
                .param("item", itemLc116)
                .param("cclasstrib", cClassTrib)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public Optional<AliquotaIbs> aliquotaIbs(String ibgeMunicipio, int ano) {
        return jdbc.sql(SQL_ALIQ_IBS)
                .param("ibge", ibgeMunicipio)
                .param("ano", ano)
                .query((rs, n) -> new AliquotaIbs(
                        rs.getBigDecimal("aliquota_estadual"),
                        rs.getBigDecimal("aliquota_municipal")))
                .optional();
    }

    @Override
    public Optional<BigDecimal> aliquotaCbs(String regimeEmpresa, int ano) {
        return jdbc.sql(SQL_ALIQ_CBS)
                .param("regime", regimeEmpresa)
                .param("ano", ano)
                .query((rs, n) -> rs.getBigDecimal("aliquota_pct"))
                .optional();
    }

    @Override
    public Optional<BigDecimal> aliquotaIs(String ncm) {
        return jdbc.sql(SQL_ALIQ_IS)
                .param("ncm", somenteDigitos(ncm))
                .query((rs, n) -> rs.getBigDecimal("aliquota_pct"))
                .optional();
    }

    private static RegimeDiferenciado regimeDaLinha(ResultSet rs, int rowNum) throws SQLException {
        return RegimeDiferenciado.de(rs.getString("regime"), rs.getBigDecimal("percentual_reducao"));
    }

    /** NCM chega com ou sem pontuação ('1006.30' / '10063021'); comparar só os dígitos. */
    private static String somenteDigitos(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("\\D", "");
    }
}
