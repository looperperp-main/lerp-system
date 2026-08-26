package com.l.erp.fiscalservice.services.fiscal;

import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercita o SQL de {@link TabelaFiscalJdbc} em H2 (modo PostgreSQL) — o risco desta fatia está
 * nas consultas, não na aritmética (essa é do {@code MotorFiscalServiceTest}).
 *
 * <p>ponytail: o DDL abaixo é um recorte dos changelogs fiscais (002 + 007 + 008) com as colunas que as
 * consultas tocam, não o schema inteiro; drift de tipo/constraint só aparece rodando o Liquibase
 * de verdade (teste de integração com Postgres, fatia futura). O que ESTE teste protege é o
 * casamento por prefixo mais longo e os filtros de vigência/alíquota nula.
 */
class TabelaFiscalJdbcTest {

    private static TabelaFiscalJdbc tabela;

    /** UUID fixo só para os testes de override de tenant da matriz ICMS. */
    private static final String TENANT = "11111111-1111-1111-1111-111111111111";

    private static final String[] SCHEMA = {
            "CREATE SCHEMA IF NOT EXISTS fiscal",
            """
            CREATE TABLE fiscal.cfop (
                codigo varchar(4) PRIMARY KEY,
                tipo_operacao varchar(10) NOT NULL,
                gera_credito_ibs boolean NOT NULL,
                gera_credito_cbs boolean NOT NULL,
                primeira_etapa_cadeia boolean NOT NULL)
            """,
            """
            CREATE TABLE fiscal.regime_dif_ncm (
                ncm varchar(10) NOT NULL,
                regime varchar(20) NOT NULL,
                percentual_reducao numeric(5,2) NOT NULL,
                vigente_de date NOT NULL,
                vigente_ate date)
            """,
            """
            CREATE TABLE fiscal.regime_cclasstrib (
                cclasstrib varchar(10) PRIMARY KEY,
                regime varchar(30) NOT NULL,
                percentual_reducao numeric(5,2) NOT NULL,
                vigente_de date NOT NULL,
                vigente_ate date)
            """,
            """
            CREATE TABLE fiscal.servico_cclasstrib (
                item_lc116 varchar(10) NOT NULL,
                cclasstrib varchar(10) NOT NULL)
            """,
            """
            CREATE TABLE fiscal.aliq_ibs_municipio (
                ibge_municipio varchar(7) NOT NULL,
                ano_vigencia int NOT NULL,
                aliquota_estadual numeric(6,4) NOT NULL,
                aliquota_municipal numeric(6,4) NOT NULL)
            """,
            """
            CREATE TABLE fiscal.aliq_cbs_regime (
                regime varchar(20) NOT NULL,
                ano_vigencia int NOT NULL,
                aliquota_pct numeric(6,4) NOT NULL)
            """,
            """
            CREATE TABLE fiscal.aliq_is_ncm (
                ncm varchar(10) NOT NULL,
                aliquota_pct numeric(5,2),
                vigente_de date NOT NULL,
                vigente_ate date)
            """,
            """
            CREATE TABLE fiscal.transicao_ano (
                ano int PRIMARY KEY,
                pct_remanescente numeric(5,2) NOT NULL,
                pis_cofins_vigente boolean NOT NULL)
            """,
            """
            CREATE TABLE fiscal.aliq_iss_municipio (
                ibge_municipio varchar(7) NOT NULL,
                item_lc116 varchar(5),
                aliquota_pct numeric(5,2) NOT NULL,
                vigente_de date NOT NULL,
                vigente_ate date)
            """,
            """
            CREATE TABLE fiscal.matriz_tributaria (
                tenant_id uuid,
                ncm_nbs varchar(9) NOT NULL,
                tipo_item varchar(1) NOT NULL,
                uf_origem char(2) NOT NULL,
                uf_destino char(2) NOT NULL,
                aliq_nominal numeric(5,2) NOT NULL,
                p_reducao_base numeric(5,2) NOT NULL,
                vigente_de date NOT NULL,
                vigente_ate date)
            """,
            """
            CREATE TABLE fiscal.retencao_config (
                tenant_id uuid,
                tributo varchar(10) NOT NULL,
                aliquota_pct numeric(5,2) NOT NULL,
                valor_minimo_base numeric(12,2) NOT NULL,
                ativo boolean NOT NULL)
            """
    };

    /** Espelha o seed do changelog, mais as linhas que provam prefixo/vigência/alíquota nula. */
    private static final String[] SEED = {
            """
            INSERT INTO fiscal.cfop VALUES
                ('5101', 'SAIDA',   true, true, true),
                ('5102', 'SAIDA',   true, true, false),
                ('1101', 'ENTRADA', true, true, false)
            """,
            """
            INSERT INTO fiscal.regime_dif_ncm (ncm, regime, percentual_reducao, vigente_de, vigente_ate) VALUES
                ('1006.30', 'ANEXO_I_ZERO',  100, DATE '2027-01-01', NULL),
                ('1006',    'ANEXO_VII_60',   60, DATE '2027-01-01', NULL),
                ('2402.20', 'MONOFASICO',      0, DATE '2027-01-01', NULL),
                ('8471',    'ANEXO_I_ZERO',  100, DATE '2020-01-01', DATE '2026-12-31')
            """,
            """
            INSERT INTO fiscal.regime_cclasstrib (cclasstrib, regime, percentual_reducao, vigente_de, vigente_ate) VALUES
                ('200029', 'ANEXO_III_60', 60, DATE '2027-01-01', NULL),
                ('200028', 'ANEXO_II_60',  60, DATE '2020-01-01', DATE '2026-12-31')
            """,
            // Como no Anexo VIII: item COM zero à esquerda ('04.01'), ao contrário da LC 116.
            """
            INSERT INTO fiscal.servico_cclasstrib (item_lc116, cclasstrib) VALUES
                ('04.01', '200029'),
                ('04.22', '011002')
            """,
            // 2033 = regime permanente, alíquotas de referência reais do portal do piloto CBS.
            // '0000000' é a linha-base de referência (fiscal-023); '3552502' finge ter publicado
            // alíquota própria — valor inventado, só para provar que a própria vence a referência.
            "INSERT INTO fiscal.aliq_ibs_municipio VALUES ('0000000', 2033, 16.0000, 2.5000)",
            "INSERT INTO fiscal.aliq_ibs_municipio VALUES ('3550308', 2033, 16.0000, 2.5000)",
            "INSERT INTO fiscal.aliq_ibs_municipio VALUES ('3552502', 2033, 18.0000, 3.0000)",
            "INSERT INTO fiscal.aliq_cbs_regime VALUES ('LUCRO_REAL', 2033, 8.5000)",
            """
            INSERT INTO fiscal.aliq_is_ncm (ncm, aliquota_pct, vigente_de, vigente_ate) VALUES
                ('2402.20', 150.00, DATE '2027-01-01', NULL),
                ('8703',    NULL,   DATE '2027-01-01', NULL)
            """,
            // Curva completa do fiscal-025 — a tabela toda cabe no seed, então nada de recorte.
            """
            INSERT INTO fiscal.transicao_ano (ano, pct_remanescente, pis_cofins_vigente) VALUES
                (2026, 100.00, true),
                (2027, 100.00, false),
                (2028, 100.00, false),
                (2029,  90.00, false),
                (2030,  80.00, false),
                (2031,  70.00, false),
                (2032,  60.00, false),
                (2033,   0.00, false)
            """,
            // Referência = teto de 5% (fiscal-029); '3550308' tem item próprio (saúde) e genérico
            // (qualquer outro item); a linha de '10%' está VENCIDA e não pode vencer a de 3%.
            """
            INSERT INTO fiscal.aliq_iss_municipio (ibge_municipio, item_lc116, aliquota_pct, vigente_de, vigente_ate) VALUES
                ('0000000', NULL,     5.00, DATE '2026-01-01', NULL),
                ('3550308', '04.01',  3.00, DATE '2026-01-01', NULL),
                ('3550308', NULL,     4.00, DATE '2026-01-01', NULL),
                ('3550308', '04.01', 10.00, DATE '2020-01-01', DATE '2025-12-31')
            """,
            // 4 níveis: nacional-fallback (SP), nacional-específico ('10063021' em SP), tenant-fallback
            // e tenant-específico ('20099999' em SP) — mesmo TENANT do campo estático da classe. RJ
            // prova vigência: a linha de 99% está VENCIDA e não pode ganhar da vigente de 22%.
            """
            INSERT INTO fiscal.matriz_tributaria
                (tenant_id, ncm_nbs, tipo_item, uf_origem, uf_destino, aliq_nominal, p_reducao_base, vigente_de, vigente_ate) VALUES
                (NULL, '00000000', 'P', 'SP', 'SP', 18.00, 0,  DATE '2026-01-01', NULL),
                (NULL, '10063021', 'P', 'SP', 'SP', 12.00, 0,  DATE '2026-01-01', NULL),
                ('11111111-1111-1111-1111-111111111111', '00000000', 'P', 'SP', 'SP', 25.00, 10.00, DATE '2026-01-01', NULL),
                ('11111111-1111-1111-1111-111111111111', '20099999', 'P', 'SP', 'SP', 30.00, 5.00,  DATE '2026-01-01', NULL),
                (NULL, '00000000', 'P', 'RJ', 'RJ', 99.00, 0,  DATE '2020-01-01', DATE '2025-12-31'),
                (NULL, '00000000', 'P', 'RJ', 'RJ', 22.00, 0,  DATE '2026-01-01', NULL)
            """,
            // Nacional só tem IRRF e CSRF (INSS fica de propósito sem cobertura, prova o 400).
            // Tenant tem override só de IRRF, com alíquota e piso diferentes da linha nacional.
            """
            INSERT INTO fiscal.retencao_config (tenant_id, tributo, aliquota_pct, valor_minimo_base, ativo) VALUES
                (NULL, 'IRRF', 1.50, 10.00, true),
                (NULL, 'CSRF', 4.65, 5000.00, true),
                ('11111111-1111-1111-1111-111111111111', 'IRRF', 2.00, 0.00, true)
            """
    };

    @BeforeAll
    static void criarBanco() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:fiscal-a1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jt = new JdbcTemplate(ds);
        for (String ddl : SCHEMA) {
            jt.execute(ddl);
        }
        for (String insert : SEED) {
            jt.execute(insert);
        }
        tabela = new TabelaFiscalJdbc(JdbcClient.create(ds));
    }

    @Test
    void cfop_existente_trazTipoEFlagDePrimeiraEtapa() {
        CfopInfo saida = tabela.cfop("5101").orElseThrow();
        assertEquals(TipoOperacaoFiscal.SAIDA, saida.tipoOperacao());
        assertTrue(saida.primeiraEtapaCadeia());

        assertFalse(tabela.cfop("5102").orElseThrow().primeiraEtapaCadeia());
        assertEquals(TipoOperacaoFiscal.ENTRADA, tabela.cfop("1101").orElseThrow().tipoOperacao());
    }

    @Test
    void cfop_inexistente_vazio() {
        assertTrue(tabela.cfop("9999").isEmpty());
    }

    @Test
    void regimeNcm_ncmDaNota_casaComOPrefixoMaisLongo() {
        // NCM completo da NF-e ('10063021') contra a posição publicada ('1006.30'):
        // '1006' também prefixa, mas perde por ser menos específico.
        RegimeDiferenciado regime = tabela.regimeNcm("10063021");
        assertEquals("ANEXO_I_ZERO", regime.name());
        assertTrue(regime.aliquotaZero());
        assertEquals(0, new BigDecimal("100").compareTo(regime.reducaoPercentual()));
    }

    @Test
    void regimeNcm_prefixoCurtoAindaVale_quandoNaoHaMaisEspecifico() {
        RegimeDiferenciado regime = tabela.regimeNcm("10061010");
        assertEquals("ANEXO_VII_60", regime.name());
        assertFalse(regime.aliquotaZero());
    }

    @Test
    void regimeNcm_monofasico_reconhecidoPeloNome() {
        RegimeDiferenciado regime = tabela.regimeNcm("24022000");
        assertEquals(Constants.REGIME_DIF_MONOFASICO, regime.name());
        assertTrue(regime.monofasico());
        assertFalse(regime.aliquotaZero());
    }

    @Test
    void regimeNcm_semCadastro_caiEmPadrao() {
        assertSame(RegimeDiferenciado.PADRAO, tabela.regimeNcm("39269090"));
    }

    @Test
    void regimeNcm_regraVencida_ignorada() {
        // '8471' está na tabela mas com vigente_ate no passado: não pode zerar um notebook.
        assertSame(RegimeDiferenciado.PADRAO, tabela.regimeNcm("84713012"));
    }

    @Test
    void regimeCClassTrib_casaExato() {
        RegimeDiferenciado regime = tabela.regimeCClassTrib("200029");
        assertEquals("ANEXO_III_60", regime.name());
        assertEquals(0, new BigDecimal("60").compareTo(regime.reducaoPercentual()));
    }

    @Test
    void regimeCClassTrib_naoCasaPorPrefixo() {
        // Ao contrário do NCM: cClassTrib é código fechado. '2000' não pode herdar de '200029'
        // nem '20002999' cair nele — prefixo aqui daria redução a quem não tem direito.
        assertSame(RegimeDiferenciado.PADRAO, tabela.regimeCClassTrib("2000"));
        assertSame(RegimeDiferenciado.PADRAO, tabela.regimeCClassTrib("2000299"));
    }

    @Test
    void regimeCClassTrib_semCadastroOuVencido_caiEmPadrao() {
        assertSame(RegimeDiferenciado.PADRAO, tabela.regimeCClassTrib("200048"));  // sem cadastro
        assertSame(RegimeDiferenciado.PADRAO, tabela.regimeCClassTrib("200028"));  // vigência vencida
    }

    @Test
    void cClassTribAdmitido_aceitaItemComOuSemZeroAEsquerda() {
        // A nota traz o item como a LC 116 publica ('4.01'); o Anexo VIII gravou '04.01'.
        // Sem o lpad, um serviço de saúde legítimo levaria 400 por formatação.
        assertTrue(tabela.cClassTribAdmitido("4.01", "200029"));
        assertTrue(tabela.cClassTribAdmitido("04.01", "200029"));
    }

    @Test
    void cClassTribAdmitido_recusaParNaoListado() {
        // 200048 (hotelaria) existe no Anexo VIII, mas não para o item 4.01.
        assertFalse(tabela.cClassTribAdmitido("4.01", "200048"));
        assertFalse(tabela.cClassTribAdmitido("4.01", "999999"));
        assertFalse(tabela.cClassTribAdmitido("9.99", "200029"));
    }

    @Test
    void aliquotaIbs_porMunicipioEAno() {
        AliquotaIbs aliq = tabela.aliquotaIbs("3550308", 2033).orElseThrow();
        assertEquals(0, new BigDecimal("16.00").compareTo(aliq.estadual()));
        assertEquals(0, new BigDecimal("2.50").compareTo(aliq.municipal()));
        assertFalse(aliq.referenciaNacional());

        // Ano sem linha NENHUMA (nem própria, nem de referência) segue vazio: o motor devolve 400
        // FISCAL_VIGENCIA_SEM_COBERTURA em vez de inventar alíquota.
        assertTrue(tabela.aliquotaIbs("3550308", 2026).isEmpty());
    }

    @Test
    void aliquotaIbs_municipioSemLinhaPropria_caiNaReferenciaNacional() {
        // Rio de Janeiro não está no seed: antes do fiscal-023 isso era 400, agora usa a referência
        // do Senado — que é uniforme por tipo de ente — e marca a origem para o motor avisar.
        AliquotaIbs aliq = tabela.aliquotaIbs("3304557", 2033).orElseThrow();
        assertEquals(0, new BigDecimal("16.00").compareTo(aliq.estadual()));
        assertEquals(0, new BigDecimal("2.50").compareTo(aliq.municipal()));
        assertTrue(aliq.referenciaNacional());
    }

    @Test
    void aliquotaIbs_aliquotaPropriaVenceAReferencia() {
        // Se o ente legislou a própria, ela manda — a referência é só o default de quem não legislou.
        AliquotaIbs aliq = tabela.aliquotaIbs("3552502", 2033).orElseThrow();
        assertEquals(0, new BigDecimal("18.00").compareTo(aliq.estadual()));
        assertEquals(0, new BigDecimal("3.00").compareTo(aliq.municipal()));
        assertFalse(aliq.referenciaNacional());
    }

    @Test
    void aliquotaCbs_porRegimeEAno() {
        assertEquals(0, new BigDecimal("8.50")
                .compareTo(tabela.aliquotaCbs(Constants.REGIME_LUCRO_REAL, 2033).orElseThrow()));
        assertTrue(tabela.aliquotaCbs(Constants.REGIME_LUCRO_REAL, 2026).isEmpty());
    }

    @Test
    void aliquotaIs_casaPorPrefixo_eIgnoraLinhaSemAliquotaRegulamentada() {
        assertEquals(0, new BigDecimal("150").compareTo(tabela.aliquotaIs("24022000").orElseThrow()));

        // '8703' consta do Anexo XVII mas com alíquota ainda não regulamentada (NULL):
        // o motor não pode destacar IS a partir dela.
        Optional<BigDecimal> semRegulamentacao = tabela.aliquotaIs("87032100");
        assertTrue(semRegulamentacao.isEmpty());

        assertTrue(tabela.aliquotaIs("84713012").isEmpty());
    }

    @Test
    void transicao_antesDe2029_icmsIssIntegral_ePisCofinsSoEm2026() {
        TransicaoAno t2026 = tabela.transicao(2026).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(t2026.pctRemanescente()));
        assertTrue(t2026.pisCofinsVigente());

        // 2027 extingue PIS/COFINS mas NÃO começa a reduzir ICMS/ISS — os dois eventos são
        // independentes, e é justamente o par que um percentual único não conseguiria expressar.
        TransicaoAno t2027 = tabela.transicao(2027).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(t2027.pctRemanescente()));
        assertFalse(t2027.pisCofinsVigente());
        assertFalse(tabela.transicao(2028).orElseThrow().pisCofinsVigente());
    }

    @Test
    void transicao_degrausDe2029A2033() {
        // As reduções de 10/20/30/40% da LC 214 lidas como REMANESCENTE, e 2033 com ICMS/ISS extintos.
        assertEquals(0, new BigDecimal("90").compareTo(tabela.transicao(2029).orElseThrow().pctRemanescente()));
        assertEquals(0, new BigDecimal("80").compareTo(tabela.transicao(2030).orElseThrow().pctRemanescente()));
        assertEquals(0, new BigDecimal("70").compareTo(tabela.transicao(2031).orElseThrow().pctRemanescente()));
        assertEquals(0, new BigDecimal("60").compareTo(tabela.transicao(2032).orElseThrow().pctRemanescente()));
        assertEquals(0, BigDecimal.ZERO.compareTo(tabela.transicao(2033).orElseThrow().pctRemanescente()));
    }

    @Test
    void transicao_anoForaDaCurva_vazio() {
        // Nem 100% (antes da reforma) nem 0% (depois): sem linha o motor devolve 400, igual à
        // alíquota IBS. Assumir qualquer um dos dois erraria o imposto em silêncio.
        assertTrue(tabela.transicao(2025).isEmpty());
        assertTrue(tabela.transicao(2035).isEmpty());
    }

    @Test
    void aliquotaIss_itemProprioDoMunicipio_venceOGenericoEAReferencia() {
        // A linha de 10% pro mesmo par está vencida (vigente_ate no passado) — não pode ganhar da
        // vigente de 3%, mesmo sendo "mais específica" por ordem de inserção.
        AliquotaIss aliq = tabela.aliquotaIss("3550308", "04.01").orElseThrow();
        assertEquals(0, new BigDecimal("3.00").compareTo(aliq.aliquotaPct()));
        assertFalse(aliq.referenciaNacional());
    }

    @Test
    void aliquotaIss_municipioSemItemEspecifico_caiNoGenericoDoMunicipio() {
        // '9.99' não tem linha própria em SP, mas o município legislou o genérico (item NULL) —
        // isso ainda vence a referência nacional.
        AliquotaIss aliq = tabela.aliquotaIss("3550308", "9.99").orElseThrow();
        assertEquals(0, new BigDecimal("4.00").compareTo(aliq.aliquotaPct()));
        assertFalse(aliq.referenciaNacional());
    }

    @Test
    void aliquotaIss_municipioSemCadastroNenhum_caiNaReferencia() {
        // Rio não está no seed: cai no teto de 5% da LC 116 art. 8-A, e o motor precisa saber que
        // veio da referência para avisar (mesmo princípio do aviso de IBS).
        AliquotaIss aliq = tabela.aliquotaIss("3304557", "04.01").orElseThrow();
        assertEquals(0, new BigDecimal("5.00").compareTo(aliq.aliquotaPct()));
        assertTrue(aliq.referenciaNacional());
    }

    @Test
    void aliquotaIss_aceitaItemComOuSemZeroAEsquerda() {
        // Mesma normalização de cClassTribAdmitido: a nota traz o item como a LC 116 publica.
        assertEquals(0, new BigDecimal("3.00")
                .compareTo(tabela.aliquotaIss("3550308", "4.01").orElseThrow().aliquotaPct()));
    }

    @Test
    void aliquotaIcms_tenantEspecifico_venceTudo() {
        // Nível 1 (tenant + ncm específico): mais específico que tudo, inclusive o próprio
        // fallback do mesmo tenant.
        RegimeIcms icms = tabela.aliquotaIcms(TENANT, "20099999", "SP", "SP").orElseThrow();
        assertEquals(0, new BigDecimal("30.00").compareTo(icms.aliqNominal()));
        assertEquals(0, new BigDecimal("5.00").compareTo(icms.pReducaoBase()));
        assertFalse(icms.ncmGenerico());
    }

    @Test
    void aliquotaIcms_tenantFallback_venceNacionalEspecifico() {
        // '10063021' tem linha nacional específica (12%), mas o tenant não tem override pra esse
        // NCM — cai no fallback DO TENANT (25%/10%), que vence a nacional específica: override de
        // tenant sempre manda sobre a base nacional, mesmo quando genérico contra específico.
        RegimeIcms icms = tabela.aliquotaIcms(TENANT, "10063021", "SP", "SP").orElseThrow();
        assertEquals(0, new BigDecimal("25.00").compareTo(icms.aliqNominal()));
        assertEquals(0, new BigDecimal("10.00").compareTo(icms.pReducaoBase()));
        assertTrue(icms.ncmGenerico());
    }

    @Test
    void aliquotaIcms_semTenant_nacionalEspecificoVenceFallback() {
        // Sem tenant (base pura): NCM específico da tabela nacional vence o fallback '00000000'.
        RegimeIcms icms = tabela.aliquotaIcms(null, "10063021", "SP", "SP").orElseThrow();
        assertEquals(0, new BigDecimal("12.00").compareTo(icms.aliqNominal()));
        assertFalse(icms.ncmGenerico());
    }

    @Test
    void aliquotaIcms_semTenantESemNcmEspecifico_caiNoFallbackNacional() {
        // NCM sem linha nenhuma (nacional ou tenant): cai no fallback geral da UF.
        RegimeIcms icms = tabela.aliquotaIcms(null, "99999999", "SP", "SP").orElseThrow();
        assertEquals(0, new BigDecimal("18.00").compareTo(icms.aliqNominal()));
        assertTrue(icms.ncmGenerico());
    }

    @Test
    void aliquotaIcms_vigenciaVencidaIgnorada() {
        // RJ tem uma linha de 99% VENCIDA e outra de 22% vigente — a vencida não pode ganhar.
        RegimeIcms icms = tabela.aliquotaIcms(null, Constants.FISCAL_NCM_NBS_FALLBACK, "RJ", "RJ").orElseThrow();
        assertEquals(0, new BigDecimal("22.00").compareTo(icms.aliqNominal()));
    }

    @Test
    void aliquotaIcms_semCobertura_vazio() {
        // Acre não está no seed deste teste: sem linha nenhuma, o motor devolve 400 em vez de
        // assumir alíquota zero — mesmo princípio de aliquotaIbs/transicao.
        assertTrue(tabela.aliquotaIcms(null, Constants.FISCAL_NCM_NBS_FALLBACK, "AC", "AC").isEmpty());
    }

    @Test
    void retencao_semTenant_trazAliquotaEPisoNacional() {
        AliquotaRetencao csrf = tabela.retencao(null, Constants.TRIBUTO_CSRF).orElseThrow();
        assertEquals(0, new BigDecimal("4.65").compareTo(csrf.aliquotaPct()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(csrf.valorMinimoBase()));
    }

    @Test
    void retencao_tenantComOverride_venceNacional() {
        // Nacional de IRRF é 1,50%/piso 10 — o override do tenant (2,00%/sem piso) precisa vencer.
        AliquotaRetencao irrf = tabela.retencao(TENANT, Constants.TRIBUTO_IRRF).orElseThrow();
        assertEquals(0, new BigDecimal("2.00").compareTo(irrf.aliquotaPct()));
        assertEquals(0, BigDecimal.ZERO.compareTo(irrf.valorMinimoBase()));
    }

    @Test
    void retencao_tenantSemOverride_caiNaNacional() {
        // Tenant não tem override de CSRF: usa a linha nacional normalmente.
        AliquotaRetencao csrf = tabela.retencao(TENANT, Constants.TRIBUTO_CSRF).orElseThrow();
        assertEquals(0, new BigDecimal("4.65").compareTo(csrf.aliquotaPct()));
    }

    @Test
    void retencao_semCobertura_vazio() {
        // INSS não tem linha nem nacional nem de tenant neste seed — 400, nunca zero.
        assertTrue(tabela.retencao(null, Constants.TRIBUTO_INSS).isEmpty());
        assertTrue(tabela.retencao(TENANT, Constants.TRIBUTO_INSS).isEmpty());
    }
}
