package com.l.erp.migracaoservice.services;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.CatalogTable;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.domain.ExportScope;
import com.l.erp.migracaoservice.infra.config.AsyncConfig;
import com.l.erp.migracaoservice.infra.config.ExportProperties;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import com.l.erp.migracaoservice.services.export.CsvRowSink;
import com.l.erp.migracaoservice.services.export.RowSink;
import com.l.erp.migracaoservice.services.export.XlsxRowSink;
import com.l.erp.migracaoservice.util.DownloadTokenGenerator;
import com.l.erp.migracaoservice.util.ValorExport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extração assíncrona do export: lê cada tabela do escopo em streaming (cursor, fetch 1000) numa
 * transação read-only e grava um arquivo por tabela em {baseDir}/{tenantId}/{batchId}/.
 * Retry automático, timeout de 30 min e e-mail de "pronto" são da fatia 3 (job agendado).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportExtractionService {

    private static final int TENTATIVAS_TOKEN = 3;
    private static final String RESULTADO_PRONTO = "pronto";
    private static final String RESULTADO_FALHA = "falha";
    private static final String RESULTADO_RETRY = "retry";

    private final ExportBatchRepository repo;
    private final ExportCatalogService catalogo;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager txManager;
    private final ExportProperties props;
    private final ObjectMapper mapper;
    private final MeterRegistry metricas;
    private final ExportNotificationService notificacao;

    @Async(AsyncConfig.EXPORT_EXECUTOR)
    public void executar(UUID batchId, Long tenantId) {
        if (!repo.marcarProcessando(batchId)) {
            log.warn("Exportação {} já foi assumida por outra execução; ignorando (tenant {})", batchId, tenantId);
            return;
        }
        Path dir = props.baseDir().resolve(String.valueOf(tenantId)).resolve(batchId.toString());
        Timer.Sample cronometro = Timer.start(metricas);
        String resultado = RESULTADO_PRONTO;
        try {
            ExportBatch batch = repo.buscar(batchId, tenantId).orElseThrow();
            ExportScope escopo = mapper.readValue(batch.scopeJson(), ExportScope.class);
            Map<String, CatalogTable> disponiveis = catalogo.descobrir().stream()
                    .collect(Collectors.toMap(CatalogTable::key, t -> t));

            Files.createDirectories(dir);
            Map<String, String> arquivos = new LinkedHashMap<>();
            Map<String, Long> linhas = new LinkedHashMap<>();
            for (String chave : escopo.tabelas()) {
                CatalogTable tabela = disponiveis.get(chave);
                if (tabela == null) {   // tabela saiu do catálogo entre o pedido e a extração
                    throw new BusinessException(String.format(Constants.EXPORT_TABELA_INDISPONIVEL, chave));
                }
                Path arquivo = dir.resolve(chave + extensao(batch.format()));
                linhas.put(chave, exportarTabela(tabela, tenantId, escopo, batch.format(), arquivo));
                // Caminho relativo ao base-dir: o mesmo registro vale local e no OCI, e o download valida o confinamento.
                arquivos.put(chave, props.baseDir().relativize(arquivo).toString().replace('\\', '/'));
            }
            finalizar(batchId, tenantId, arquivos, linhas);
        } catch (Exception e) {
            log.error("Falha ao gerar exportação {} do tenant {}", batchId, tenantId, e);
            apagarDiretorio(dir);
            if (e instanceof BusinessException) {
                // Erro de regra (ex.: tabela saiu do catálogo): repetir não adianta; a mensagem é segura p/ o usuário.
                resultado = RESULTADO_FALHA;
                repo.marcarFalhou(batchId, e.getMessage());
            } else {
                // Erro técnico: volta p/ a fila (a manutenção reexecuta após a espera) até esgotar as tentativas.
                resultado = repo.reenfileirarOuFalhar(batchId, Constants.EXPORT_MAX_TENTATIVAS, Constants.EXPORT_FALHA_GERACAO)
                        ? RESULTADO_RETRY : RESULTADO_FALHA;
            }
        } finally {
            cronometro.stop(Timer.builder("export_duracao_segundos").publishPercentiles(0.5, 0.95)
                    .tag("resultado", resultado).register(metricas));
            metricas.counter("export_execucoes_total", "resultado", resultado).increment();
        }
    }

    private void finalizar(UUID batchId, Long tenantId, Map<String, String> arquivos, Map<String, Long> linhas) {
        OffsetDateTime expira = OffsetDateTime.now().plusDays(props.retentionDays());
        String paths = mapper.writeValueAsString(arquivos);
        String counts = mapper.writeValueAsString(linhas);
        for (int i = 1; ; i++) {
            try {
                repo.marcarPronto(batchId, paths, counts, DownloadTokenGenerator.gerar(), expira);
                break;
            } catch (DuplicateKeyException colisao) {   // 2^-256: só existe pra não engolir o impossível
                if (i == TENTATIVAS_TOKEN) throw colisao;
            }
        }
        repo.registrarAuditoria(batchId, tenantId, Constants.EXPORT_AUDIT_GENERATED, null);
        log.info("Exportação {} do tenant {} pronta: {} tabelas", batchId, tenantId, arquivos.size());
        // Depois de tudo gravado: o aviso nunca lança (ExportNotificationService), então não vira retry do export.
        repo.buscar(batchId, tenantId).ifPresent(notificacao::avisarPronto);
    }

    private long exportarTabela(CatalogTable tabela, Long tenantId, ExportScope escopo, String formato, Path arquivo)
            throws IOException {
        boolean filtraPeriodo = tabela.dateColumn() != null;
        boolean temDe = filtraPeriodo && escopo.dataInicial() != null;
        boolean temAte = filtraPeriodo && escopo.dataFinal() != null;
        String sql = montarSql(tabela, temDe, temAte);

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setReadOnly(true);   // também mantém autocommit=false, requisito do cursor do Postgres
        try (RowSink sink = abrirSink(formato, arquivo, tabela.key())) {
            Long total = tx.execute(status -> jdbcTemplate.query(con -> {
                PreparedStatement ps = con.prepareStatement(sql);
                ps.setFetchSize(Constants.EXPORT_FETCH_SIZE);
                int i = 1;
                ps.setLong(i++, tenantId);
                if (temDe) ps.setObject(i++, escopo.dataInicial());
                if (temAte) ps.setObject(i, escopo.dataFinal());
                return ps;
            }, rs -> {
                try {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    List<String> colunas = new ArrayList<>(n);
                    for (int c = 1; c <= n; c++) colunas.add(md.getColumnName(c));
                    sink.cabecalho(colunas);
                    long cont = 0;
                    while (rs.next()) {
                        List<Object> linha = new ArrayList<>(n);
                        for (int c = 1; c <= n; c++) linha.add(ValorExport.normalizar(rs.getObject(c)));
                        sink.linha(linha);
                        cont++;
                    }
                    sink.concluir(metadata(tabela, tenantId, escopo, colunas, cont));
                    return cont;
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }));
            return total == null ? 0 : total;
        }
    }

    private RowSink abrirSink(String formato, Path arquivo, String nome) throws IOException {
        if (Constants.EXPORT_FORMATO_CSV.equals(formato)) {
            return new CsvRowSink(arquivo, arquivo.resolveSibling(nome + ".meta.json"), mapper);
        }
        return new XlsxRowSink(arquivo, nome);
    }

    private Map<String, Object> metadata(CatalogTable t, Long tenantId, ExportScope escopo, List<String> colunas, long linhas) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entity_type", t.key());
        m.put("schema_version", Constants.MIGRACAO_SCHEMA_VERSION_ATUAL);
        m.put("exported_at", Instant.now().toString());
        m.put("tenant_id", tenantId);
        m.put("row_count", linhas);
        m.put("date_from", t.dateColumn() != null ? escopo.dataInicial() : null);
        m.put("date_to", t.dateColumn() != null ? escopo.dataFinal() : null);
        m.put("fields", colunas);
        return m;
    }

    /** SELECT do catálogo + filtro de tenant + período (só se a tabela tem coluna de data). Placeholders: tenant, de, até. */
    static String montarSql(CatalogTable t, boolean temDe, boolean temAte) {
        StringBuilder sb = new StringBuilder("SELECT * FROM ")
                .append(ExportCatalogService.aspas(t.schema())).append('.').append(ExportCatalogService.aspas(t.table()))
                .append(" WHERE ").append(t.tenantFilter());
        if (t.dateColumn() != null) {
            String col = ExportCatalogService.aspas(t.dateColumn());
            if (temDe) sb.append(" AND ").append(col).append(" >= ?::date");
            if (temAte) sb.append(" AND ").append(col).append(" < (?::date + 1)");
        }
        return sb.toString();
    }

    static String extensao(String formato) {
        return Constants.EXPORT_FORMATO_CSV.equals(formato) ? ".csv" : ".xlsx";
    }

    private void apagarDiretorio(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> arvore = Files.walk(dir)) {
            arvore.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("Não consegui limpar {} após falha: {}", dir, e.getMessage());
        }
    }
}
