package com.l.erp.migracaoservice.services;

import com.l.erp.migracaoservice.domain.CatalogTable;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.infra.config.ExportProperties;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** executar() com o JDBC mockado: máquina de estados, arquivos no disco e tratamento de falha. */
class ExportExtractionExecucaoTest {

    private static final Long TENANT = 7L;
    private static final UUID ID = UUID.randomUUID();
    private static final CatalogTable CLIENTE =
            new CatalogTable("cadastros", "cliente", "Cadastros", "Cliente", "\"tenant_id\" = ?", "created_at");

    @TempDir
    Path base;

    ExportBatchRepository repo;
    ExportCatalogService catalogo;
    JdbcTemplate jdbc;
    ExportExtractionService service;

    @BeforeEach
    void setUp() {
        repo = mock(ExportBatchRepository.class);
        catalogo = mock(ExportCatalogService.class);
        jdbc = mock(JdbcTemplate.class);
        service = new ExportExtractionService(repo, catalogo, jdbc, mock(PlatformTransactionManager.class),
                new ExportProperties(base, 7), JsonMapper.builder().build());

        when(repo.buscar(ID, TENANT)).thenReturn(Optional.of(new ExportBatch(ID, TENANT, UUID.randomUUID(),
                "{\"tabelas\":[\"cadastros.cliente\"],\"dataInicial\":null,\"dataFinal\":null}", "CSV", "PROCESSING",
                null, null, 1, null, null, OffsetDateTime.now(), null, null, null)));
        when(catalogo.descobrir()).thenReturn(List.of(CLIENTE));
    }

    /** Faz o JdbcTemplate entregar duas linhas ao extractor, sem banco. */
    private void duasLinhas() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(md);
        when(md.getColumnCount()).thenReturn(2);
        when(md.getColumnName(1)).thenReturn("id");
        when(md.getColumnName(2)).thenReturn("nome");
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getObject(1)).thenReturn(1L, 2L);
        when(rs.getObject(2)).thenReturn("Ana", "Bia");
        when(jdbc.query(any(PreparedStatementCreator.class), ArgumentMatchers.<ResultSetExtractor<Long>>any()))
                .thenAnswer(inv -> inv.<ResultSetExtractor<Long>>getArgument(1).extractData(rs));
    }

    @Test
    void caminhoFeliz_gravaArquivosEMarcaPronto() throws Exception {
        when(repo.marcarProcessando(ID)).thenReturn(true);
        duasLinhas();

        service.executar(ID, TENANT);

        Path dir = base.resolve("7").resolve(ID.toString());
        assertThat(Files.readString(dir.resolve("cadastros.cliente.csv")))
                .isEqualTo("id,nome\r\n1,Ana\r\n2,Bia\r\n");
        assertThat(Files.readString(dir.resolve("cadastros.cliente.meta.json")))
                .contains("\"row_count\"").contains("cadastros.cliente");
        // caminho gravado é relativo ao base-dir (vale local e no OCI)
        verify(repo).marcarPronto(eq(ID), contains("7/" + ID + "/cadastros.cliente.csv"), contains("2"), anyString(), any());
        verify(repo).registrarAuditoria(ID, TENANT, "GENERATED", null);
        verify(repo, never()).marcarFalhou(any(), anyString());
    }

    @Test
    void batchJaAssumidoPorOutraExecucaoEhIgnorado() {
        when(repo.marcarProcessando(ID)).thenReturn(false);

        service.executar(ID, TENANT);

        verify(repo, never()).buscar(any(), any());
        verify(repo, never()).marcarPronto(any(), any(), any(), any(), any());
    }

    @Test
    void tabelaQueSaiuDoCatalogoFalhaComMensagemClaraELimpaODiretorio() {
        when(repo.marcarProcessando(ID)).thenReturn(true);
        when(catalogo.descobrir()).thenReturn(List.of());

        service.executar(ID, TENANT);

        verify(repo).marcarFalhou(eq(ID), contains("cadastros.cliente"));
        assertThat(base.resolve("7").resolve(ID.toString())).doesNotExist();
    }

    @Test
    void erroInesperadoNaoVazaDetalheInternoELimpaOsArquivos() {
        when(repo.marcarProcessando(ID)).thenReturn(true);
        when(jdbc.query(any(PreparedStatementCreator.class), ArgumentMatchers.<ResultSetExtractor<Long>>any()))
                .thenThrow(new IllegalStateException("conexão caiu em 10.0.0.5"));

        service.executar(ID, TENANT);

        verify(repo).marcarFalhou(ID, "Não foi possível gerar a exportação");
        verify(repo, never()).marcarPronto(any(), any(), any(), any(), any());
        assertThat(base.resolve("7").resolve(ID.toString())).doesNotExist();
    }

    @Test
    void colisaoDeTokenRepeteComOutroToken() throws Exception {
        when(repo.marcarProcessando(ID)).thenReturn(true);
        duasLinhas();
        Mockito.doThrow(new DuplicateKeyException("dup"))
                .doNothing()
                .when(repo).marcarPronto(any(), any(), any(), any(), any());

        service.executar(ID, TENANT);

        verify(repo, Mockito.times(2)).marcarPronto(any(), any(), any(), any(), any());
        verify(repo, never()).marcarFalhou(any(), anyString());
    }
}
