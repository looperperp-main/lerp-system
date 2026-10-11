package com.l.erp.migracaoservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.CatalogTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo de tabelas exportáveis DESCOBERTO do information_schema a cada chamada: tabela nova com
 * tenant_id nos schemas de {@link Constants#EXPORT_SCHEMAS} entra sozinha, e tabela que não existe
 * naquele banco (ex.: migration de outra branch ainda não aplicada) simplesmente não aparece — o
 * export nunca quebra por catálogo desatualizado.
 *
 * Isolamento por tenant é inegociável: tabela sem tenant_id só entra se tiver FK de coluna única
 * para uma tabela do escopo que tenha; senão é pulada (ponytail: um nível de FK, suficiente hoje).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportCatalogService {

    private static final String SQL_COLUNAS = """
            SELECT c.table_schema, c.table_name, c.column_name
              FROM information_schema.columns c
              JOIN information_schema.tables t
                ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
             WHERE c.table_schema IN (:schemas)
             ORDER BY c.table_schema, c.table_name, c.ordinal_position""";

    // Só FKs de uma coluna: com FK composta o pareamento coluna a coluna do information_schema não é confiável.
    private static final String SQL_FKS = """
            SELECT kcu.table_schema, kcu.table_name, kcu.column_name,
                   ccu.table_schema AS ref_schema, ccu.table_name AS ref_table, ccu.column_name AS ref_column
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu
                ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
              JOIN information_schema.constraint_column_usage ccu
                ON ccu.constraint_name = tc.constraint_name AND ccu.constraint_schema = tc.table_schema
             WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema IN (:schemas)
               AND (SELECT count(*) FROM information_schema.key_column_usage k2
                     WHERE k2.constraint_name = tc.constraint_name AND k2.table_schema = tc.table_schema) = 1""";

    private final JdbcClient jdbc;

    public List<CatalogTable> descobrir() {
        Map<String, Set<String>> colunas = lerColunas();
        Map<String, String[]> fks = lerFks();

        List<CatalogTable> catalogo = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : colunas.entrySet()) {
            String chave = e.getKey();
            if (Constants.EXPORT_TABELAS_EXCLUIDAS.contains(chave)) continue;
            String[] partes = chave.split("\\.", 2);
            String filtro = montarFiltroTenant(partes[0], partes[1], e.getValue(), colunas, fks);
            if (filtro == null) {
                log.debug("Tabela {} fora do catálogo de export: sem tenant_id e sem FK resolvível", chave);
                continue;
            }
            String dateColumn = Constants.EXPORT_COLUNAS_DATA.stream().filter(e.getValue()::contains).findFirst().orElse(null);
            catalogo.add(new CatalogTable(partes[0], partes[1], rotuloGrupo(partes[0]), humanizar(partes[1]), filtro, dateColumn));
        }
        catalogo.sort(Comparator.comparing(CatalogTable::schema).thenComparing(CatalogTable::table));
        return catalogo;
    }

    /** Fragmento SQL com um {@code ?} (o tenant), ou null se não dá para isolar por tenant. */
    private String montarFiltroTenant(String schema, String tabela, Set<String> cols,
                                      Map<String, Set<String>> todas, Map<String, String[]> fks) {
        if (cols.contains(Constants.EXPORT_COLUNA_TENANT)) {
            return aspas(Constants.EXPORT_COLUNA_TENANT) + " = ?";
        }
        String prefixo = schema + "." + tabela + ".";
        for (Map.Entry<String, String[]> fk : fks.entrySet()) {
            // chave = "schema.tabela.coluna"; valor = {refSchema, refTabela, refColuna}
            if (!fk.getKey().startsWith(prefixo)) continue;
            String coluna = fk.getKey().substring(prefixo.length());
            String[] ref = fk.getValue();
            Set<String> refCols = todas.get(ref[0] + "." + ref[1]);
            boolean paiIsolavel = refCols != null && refCols.contains(Constants.EXPORT_COLUNA_TENANT)
                    && !Constants.EXPORT_TABELAS_EXCLUIDAS.contains(ref[0] + "." + ref[1]);
            if (paiIsolavel) {
                return aspas(coluna) + " IN (SELECT " + aspas(ref[2]) + " FROM " + aspas(ref[0]) + "." + aspas(ref[1])
                        + " WHERE " + aspas(Constants.EXPORT_COLUNA_TENANT) + " = ?)";
            }
        }
        return null;
    }

    private Map<String, Set<String>> lerColunas() {
        Map<String, Set<String>> mapa = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.sql(SQL_COLUNAS).param("schemas", Constants.EXPORT_SCHEMAS).query().listOfRows()) {
            mapa.computeIfAbsent(r.get("table_schema") + "." + r.get("table_name"), k -> new LinkedHashSet<>())
                    .add((String) r.get("column_name"));
        }
        return mapa;
    }

    private Map<String, String[]> lerFks() {
        Map<String, String[]> mapa = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.sql(SQL_FKS).param("schemas", Constants.EXPORT_SCHEMAS).query().listOfRows()) {
            mapa.putIfAbsent(r.get("table_schema") + "." + r.get("table_name") + "." + r.get("column_name"),
                    new String[]{(String) r.get("ref_schema"), (String) r.get("ref_table"), (String) r.get("ref_column")});
        }
        return mapa;
    }

    /** Identificador SQL entre aspas; os nomes vêm do catálogo do banco, nunca do usuário. */
    public static String aspas(String identificador) {
        return "\"" + identificador.replace("\"", "\"\"") + "\"";
    }

    /** "pedido_compra_item" → "Pedido compra item" (rótulo de fallback; o front pode refinar). */
    public static String humanizar(String tabela) {
        String s = tabela.replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    static String rotuloGrupo(String schema) {
        return "emissao".equals(schema) ? Constants.EXPORT_GRUPO_EMISSAO_ROTULO : humanizar(schema);
    }
}
