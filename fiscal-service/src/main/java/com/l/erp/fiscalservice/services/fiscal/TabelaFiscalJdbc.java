package com.l.erp.fiscalservice.services.fiscal;

import com.l.erp.common.util.Constants;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
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

    // item 7.8: vigência é POR DATA (:data = dataCompetencia da nota), não "a linha atual" — uma
    // nota reprocessada com competência antiga tem que enxergar a regra que valia na época dela,
    // mesmo que hoje já exista uma versão mais nova. vigente_de DESC desempata se a carga deixar
    // duas linhas vigentes na mesma janela (não deveria acontecer com dado correto, mas SELECT com
    // .optional() estoura exception se vier mais de uma linha — o LIMIT 1 já evita isso sozinho).
    private static final String SQL_REGIME_NCM = """
            SELECT regime, percentual_reducao
              FROM fiscal.regime_dif_ncm
             WHERE ncm IS NOT NULL
               AND vigente_de <= :data AND (vigente_ate IS NULL OR vigente_ate > :data)
               AND :ncm LIKE replace(ncm, '.', '') || '%'
             ORDER BY length(replace(ncm, '.', '')) DESC, vigente_de DESC
             LIMIT 1
            """;

    // Match EXATO, ao contrário do NCM: cClassTrib é código fechado do Anexo VIII ('200029'), não
    // hierarquia — '200029' não é filho de '2000'. Prefixo aqui só produziria falso positivo.
    private static final String SQL_REGIME_CCLASSTRIB = """
            SELECT regime, percentual_reducao
              FROM fiscal.regime_cclasstrib
             WHERE cclasstrib = :cclasstrib
               AND vigente_de <= :data AND (vigente_ate IS NULL OR vigente_ate > :data)
             ORDER BY vigente_de DESC
             LIMIT 1
            """;

    // lpad: a LC 116 escreve o item sem zero à esquerda ('4.01') e o Anexo VIII com ele ('04.01').
    // Sem normalizar, um serviço de saúde legítimo seria rejeitado por diferença de formatação.
    private static final String SQL_CCLASSTRIB_ADMITIDO = """
            SELECT 1
              FROM fiscal.servico_cclasstrib
             WHERE item_lc116 = lpad(:item, 5, '0')
               AND cclasstrib = :cclasstrib
            """;

    // Alíquota PRÓPRIA do município vence a de REFERÊNCIA (linha sentinela '0000000'): a referência
    // do Senado é uniforme por tipo de ente, então replicá-la nos 5.570 municípios seria guardar 8
    // valores distintos em 44.560 linhas. O ORDER BY faz a precedência; sem linha nenhuma para o ano
    // o Optional volta vazio e o motor devolve 400 — dado faltando nunca vira alíquota zero.
    private static final String SQL_ALIQ_IBS = """
            SELECT aliquota_estadual, aliquota_municipal, ibge_municipio
              FROM fiscal.aliq_ibs_municipio
             WHERE ibge_municipio IN (:ibge, :referencia)
               AND ano_vigencia = :ano
             ORDER BY CASE WHEN ibge_municipio = :referencia THEN 1 ELSE 0 END
             LIMIT 1
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
               AND vigente_de <= :data AND (vigente_ate IS NULL OR vigente_ate > :data)
               AND :ncm LIKE replace(ncm, '.', '') || '%'
             ORDER BY length(replace(ncm, '.', '')) DESC, vigente_de DESC
             LIMIT 1
            """;

    // Sem default: ano fora da curva publicada não vira 100% nem 0% de ICMS/ISS — volta vazio e o
    // motor decide (mesmo princípio de SQL_ALIQ_IBS).
    private static final String SQL_TRANSICAO = """
            SELECT pct_remanescente, pis_cofins_vigente
              FROM fiscal.transicao_ano
             WHERE ano = :ano
            """;

    // Mesma precedência de SQL_ALIQ_IBS: item PRÓPRIO do município vence o item GENÉRICO
    // (item_lc116 IS NULL) do próprio município, que vence a REFERÊNCIA (teto de 5%). lpad como em
    // SQL_CCLASSTRIB_ADMITIDO — item chega sem zero à esquerda ('4.01'), tabela grava com ('04.01').
    private static final String SQL_ALIQ_ISS = """
            SELECT aliquota_pct, ibge_municipio
              FROM fiscal.aliq_iss_municipio
             WHERE ibge_municipio IN (:ibge, :referencia)
               AND (item_lc116 = lpad(:item, 5, '0') OR item_lc116 IS NULL)
               AND vigente_de <= :data AND (vigente_ate IS NULL OR vigente_ate > :data)
             ORDER BY CASE WHEN ibge_municipio = :referencia THEN 1 ELSE 0 END,
                      CASE WHEN item_lc116 IS NULL THEN 1 ELSE 0 END,
                      vigente_de DESC
             LIMIT 1
            """;

    // 4 níveis via ORDER BY, mesma técnica de SQL_ALIQ_IBS/SQL_ALIQ_ISS: tenant_id = :tenant vence
    // NULL (comparação com NULL na tabela nunca é true, cai no ELSE sozinha), ncm_nbs específico
    // vence o fallback. tipo_item fixo em 'P' porque ICMS é só sobre mercadoria — 'S' (NBS) fica
    // pra quando este método servir outro imposto além de ICMS.
    private static final String SQL_MATRIZ_ICMS = """
            SELECT aliq_nominal, p_reducao_base, ncm_nbs
              FROM fiscal.matriz_tributaria
             WHERE tipo_item = 'P'
               AND uf_origem = :ufOrigem
               AND uf_destino = :ufDestino
               AND ncm_nbs IN (:ncmNbs, :fallback)
               AND (tenant_id = :tenantId::bigint OR tenant_id IS NULL)
               AND vigente_de <= :data AND (vigente_ate IS NULL OR vigente_ate > :data)
             ORDER BY CASE WHEN tenant_id = :tenantId::bigint THEN 0 ELSE 1 END,
                      CASE WHEN ncm_nbs = :ncmNbs THEN 0 ELSE 1 END,
                      vigente_de DESC
             LIMIT 1
            """;

    // Mesma técnica de 2 níveis de SQL_ALIQ_ISS (sem o degrau de item genérico: retenção não tem
    // essa granularidade): override do tenant vence a linha nacional (tenant_id IS NULL).
    private static final String SQL_RETENCAO = """
            SELECT aliquota_pct, valor_minimo_base
              FROM fiscal.retencao_config
             WHERE tributo = :tributo
               AND (tenant_id = :tenantId::bigint OR tenant_id IS NULL)
               AND ativo = true
             ORDER BY CASE WHEN tenant_id = :tenantId::bigint THEN 0 ELSE 1 END
             LIMIT 1
            """;

    // ano_vigencia IS NULL: override vale pra todos os anos (ex.: Prouni, redução fixa). Quando
    // presente (curva do serviço financeiro), casa só o ano exato — sem fallback pra ano vizinho,
    // mesmo princípio de SQL_ALIQ_IBS/SQL_TRANSICAO: ano sem linha publicada não é erro do motor.
    private static final String SQL_ALIQUOTA_REGIME_TRIBUTO = """
            SELECT tributo, tipo, valor
              FROM fiscal.aliquota_regime_tributo
             WHERE regime = :regime
               AND (ano_vigencia IS NULL OR ano_vigencia = :ano)
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
    public RegimeDiferenciado regimeNcm(String ncm, LocalDate competencia) {
        return jdbc.sql(SQL_REGIME_NCM)
                .param("ncm", somenteDigitos(ncm))
                .param("data", competencia)
                .query(TabelaFiscalJdbc::regimeDaLinha)
                .optional()
                .orElse(RegimeDiferenciado.PADRAO);
    }

    @Override
    public RegimeDiferenciado regimeCClassTrib(String cClassTrib, LocalDate competencia) {
        return jdbc.sql(SQL_REGIME_CCLASSTRIB)
                .param("cclasstrib", cClassTrib)
                .param("data", competencia)
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
                .param("referencia", Constants.FISCAL_IBGE_REFERENCIA_NACIONAL)
                .param("ano", ano)
                .query((rs, n) -> new AliquotaIbs(
                        rs.getBigDecimal("aliquota_estadual"),
                        rs.getBigDecimal("aliquota_municipal"),
                        Constants.FISCAL_IBGE_REFERENCIA_NACIONAL.equals(rs.getString("ibge_municipio"))))
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
    public Optional<BigDecimal> aliquotaIs(String ncm, LocalDate competencia) {
        return jdbc.sql(SQL_ALIQ_IS)
                .param("ncm", somenteDigitos(ncm))
                .param("data", competencia)
                .query((rs, n) -> rs.getBigDecimal("aliquota_pct"))
                .optional();
    }

    @Override
    public Optional<TransicaoAno> transicao(int ano) {
        return jdbc.sql(SQL_TRANSICAO)
                .param("ano", ano)
                .query((rs, n) -> new TransicaoAno(
                        rs.getBigDecimal("pct_remanescente"),
                        rs.getBoolean("pis_cofins_vigente")))
                .optional();
    }

    @Override
    public Optional<AliquotaIss> aliquotaIss(String ibgeMunicipio, String itemLc116, LocalDate competencia) {
        return jdbc.sql(SQL_ALIQ_ISS)
                .param("ibge", ibgeMunicipio)
                .param("referencia", Constants.FISCAL_IBGE_REFERENCIA_NACIONAL)
                .param("item", itemLc116)
                .param("data", competencia)
                .query((rs, n) -> new AliquotaIss(
                        rs.getBigDecimal("aliquota_pct"),
                        Constants.FISCAL_IBGE_REFERENCIA_NACIONAL.equals(rs.getString("ibge_municipio"))))
                .optional();
    }

    @Override
    public Optional<RegimeIcms> aliquotaIcms(String tenantId, String ncmNbs, String ufOrigem, String ufDestino,
                                              LocalDate competencia) {
        return jdbc.sql(SQL_MATRIZ_ICMS)
                .param("tenantId", tenantId)
                .param("ncmNbs", ncmNbs)
                .param("fallback", Constants.FISCAL_NCM_NBS_FALLBACK)
                .param("ufOrigem", ufOrigem)
                .param("ufDestino", ufDestino)
                .param("data", competencia)
                .query((rs, n) -> new RegimeIcms(
                        rs.getBigDecimal("aliq_nominal"),
                        rs.getBigDecimal("p_reducao_base"),
                        Constants.FISCAL_NCM_NBS_FALLBACK.equals(rs.getString("ncm_nbs"))))
                .optional();
    }

    @Override
    public Optional<AliquotaRetencao> retencao(String tenantId, String tributo) {
        return jdbc.sql(SQL_RETENCAO)
                .param("tenantId", tenantId)
                .param("tributo", tributo)
                .query((rs, n) -> new AliquotaRetencao(
                        rs.getBigDecimal("aliquota_pct"),
                        rs.getBigDecimal("valor_minimo_base")))
                .optional();
    }

    @Override
    public List<RegimeTributoOverride> overridesRegime(String regime, int ano) {
        return jdbc.sql(SQL_ALIQUOTA_REGIME_TRIBUTO)
                .param("regime", regime)
                .param("ano", ano)
                .query((rs, n) -> new RegimeTributoOverride(
                        rs.getString("tributo"),
                        rs.getString("tipo"),
                        rs.getBigDecimal("valor")))
                .list();
    }

    private static RegimeDiferenciado regimeDaLinha(ResultSet rs, int rowNum) throws SQLException {
        return RegimeDiferenciado.de(rs.getString("regime"), rs.getBigDecimal("percentual_reducao"));
    }

    /** NCM chega com ou sem pontuação ('1006.30' / '10063021'); comparar só os dígitos. */
    private static String somenteDigitos(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("\\D", "");
    }
}
