package com.l.erp.migracaoservice.services;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.infra.config.ExportProperties;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Download: regra de tenant/validade, confinamento de caminho, arquivo único x zip e auditoria. */
class ExportDownloadServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID ID = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @TempDir
    Path base;

    ExportBatchRepository repo;
    ExportDownloadService service;

    @BeforeEach
    void setUp() {
        repo = mock(ExportBatchRepository.class);
        service = new ExportDownloadService(repo, new ExportProperties(base, 7), JsonMapper.builder().build());
    }

    private ExportBatch batch(Long tenant, String status, String formato, String paths, OffsetDateTime expira) {
        return new ExportBatch(ID, tenant, USER, "{}", formato, status, paths, "{}", 1, null, null,
                OffsetDateTime.parse("2026-10-11T10:00:00Z"), OffsetDateTime.now(), expira, null);
    }

    private Path arquivo(String rel, String conteudo) throws Exception {
        Path p = base.resolve(rel);
        Files.createDirectories(p.getParent());
        return Files.writeString(p, conteudo);
    }

    private byte[] ler(ExportDownloadService.Arquivo a) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        a.corpo().writeTo(out);
        return out.toByteArray();
    }

    private void esperaStatus(String token, HttpStatus esperado) {
        assertThatThrownBy(() -> service.preparar(TENANT, USER, token))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(esperado));
    }

    @Test
    void xlsxUnico_saiDiretoEMarcaBaixadoComAuditoria() throws Exception {
        arquivo("7/" + ID + "/cadastros.cliente.xlsx", "conteudo");
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(batch(TENANT, "READY", "XLSX",
                "{\"cadastros.cliente\":\"7/" + ID + "/cadastros.cliente.xlsx\"}", OffsetDateTime.now().plusDays(1))));

        var a = service.preparar(TENANT, USER, "tok");

        assertThat(a.nome()).isEqualTo("cadastros.cliente.xlsx");
        assertThat(a.contentType()).contains("spreadsheetml");
        assertThat(ler(a)).isEqualTo("conteudo".getBytes());
        verify(repo).marcarBaixado(ID);
        verify(repo).registrarAuditoria(ID, TENANT, "DOWNLOADED", USER);
    }

    @Test
    void porId_baixaOExportDoProprioTenantSemPrecisarDoToken() throws Exception {
        arquivo("7/" + ID + "/cadastros.cliente.xlsx", "conteudo");
        when(repo.buscar(ID, TENANT)).thenReturn(Optional.of(batch(TENANT, "READY", "XLSX",
                "{\"cadastros.cliente\":\"7/" + ID + "/cadastros.cliente.xlsx\"}", OffsetDateTime.now().plusDays(1))));

        var a = service.prepararPorId(TENANT, USER, ID);

        assertThat(ler(a)).isEqualTo("conteudo".getBytes());
        verify(repo).marcarBaixado(ID);
        verify(repo).registrarAuditoria(ID, TENANT, "DOWNLOADED", USER);
    }

    @Test
    void porId_deOutroTenantEh404SemMarcarBaixado() {
        when(repo.buscar(ID, TENANT)).thenReturn(Optional.empty());      // o filtro por tenant está no próprio buscar

        assertThatThrownBy(() -> service.prepararPorId(TENANT, USER, ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repo, never()).marcarBaixado(any());
    }

    @Test
    void porId_expiradoEh410() {
        when(repo.buscar(ID, TENANT)).thenReturn(Optional.of(batch(TENANT, "EXPIRED", "XLSX", "{}", OffsetDateTime.now().minusDays(1))));

        assertThatThrownBy(() -> service.prepararPorId(TENANT, USER, ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
        verify(repo, never()).marcarBaixado(any());
    }

    @Test
    void csvLevaOMetaJsonEViraZip() throws Exception {
        arquivo("7/" + ID + "/cadastros.cliente.csv", "id\r\n");
        arquivo("7/" + ID + "/cadastros.cliente.meta.json", "{}");
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(batch(TENANT, "DOWNLOADED", "CSV",
                "{\"cadastros.cliente\":\"7/" + ID + "/cadastros.cliente.csv\"}", OffsetDateTime.now().plusDays(1))));

        var a = service.preparar(TENANT, USER, "tok");

        assertThat(a.nome()).isEqualTo("syax-exportacao-20261011.zip");
        assertThat(a.contentType()).isEqualTo("application/zip");
        List<String> nomes = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ler(a)))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) nomes.add(e.getName());
        }
        assertThat(nomes).containsExactly("cadastros.cliente.csv", "cadastros.cliente.meta.json");
    }

    @Test
    void csvSemMetaNoDisco_saiComoArquivoUnico() throws Exception {
        arquivo("7/" + ID + "/cadastros.cliente.csv", "id\r\n");
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(batch(TENANT, "READY", "CSV",
                "{\"cadastros.cliente\":\"7/" + ID + "/cadastros.cliente.csv\"}", OffsetDateTime.now().plusDays(1))));

        var a = service.preparar(TENANT, USER, "tok");

        assertThat(a.nome()).isEqualTo("cadastros.cliente.csv");
        assertThat(a.contentType()).startsWith("text/csv");
    }

    @Test
    void tokenInexistente_404() {
        when(repo.buscarPorToken("x")).thenReturn(Optional.empty());

        esperaStatus("x", HttpStatus.NOT_FOUND);
    }

    @Test
    void tokenDeOutroTenant_404EAuditaDownloadDenied() {
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(batch(99L, "READY", "XLSX", "{}", OffsetDateTime.now().plusDays(1))));

        esperaStatus("tok", HttpStatus.NOT_FOUND);

        // auditado no tenant DONO do batch, com quem tentou
        verify(repo).registrarAuditoria(ID, 99L, "DOWNLOAD_DENIED", USER);
        verify(repo, never()).marcarBaixado(any());
    }

    @Test
    void expiradoOuForaDeEstadoBaixavel_410() {
        when(repo.buscarPorToken("velho")).thenReturn(Optional.of(batch(TENANT, "READY", "XLSX", "{}", OffsetDateTime.now().minusMinutes(1))));
        when(repo.buscarPorToken("falho")).thenReturn(Optional.of(batch(TENANT, "FAILED", "XLSX", "{}", OffsetDateTime.now().plusDays(1))));

        esperaStatus("velho", HttpStatus.GONE);
        esperaStatus("falho", HttpStatus.GONE);
        verify(repo, never()).marcarBaixado(any());
    }

    @Test
    void arquivoAusenteNoDisco_410SemMarcarBaixado() {
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(batch(TENANT, "READY", "XLSX",
                "{\"cadastros.cliente\":\"7/" + ID + "/sumiu.xlsx\"}", OffsetDateTime.now().plusDays(1))));

        esperaStatus("tok", HttpStatus.GONE);
        verify(repo, never()).marcarBaixado(any());
    }

    private ExportBatch cancelamento(String status, OffsetDateTime expira) {
        return new ExportBatch(ID, TENANT, USER, "{}", "XLSX", status,
                "{\"cadastros.cliente\":\"7/" + ID + "/cadastros.cliente.xlsx\"}", "{}", 1, "CANCELLATION", null,
                OffsetDateTime.parse("2026-10-11T10:00:00Z"), OffsetDateTime.now(), expira, null);
    }

    @Test
    void posse_exportDeCancelamentoBaixaSemSessaoEAuditaComIp() throws Exception {
        arquivo("7/" + ID + "/cadastros.cliente.xlsx", "conteudo");
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(cancelamento("READY", OffsetDateTime.now().plusDays(1))));

        var a = service.prepararPosse("tok", "203.0.113.9");

        assertThat(ler(a)).isEqualTo("conteudo".getBytes());
        verify(repo).marcarBaixado(ID);
        // ator nulo (não há sessão) e o IP é a prova de quem baixou; o tenant gravado é o do batch, não o de um chamador
        verify(repo).registrarAuditoria(ID, TENANT, "DOWNLOADED", null, "203.0.113.9");
    }

    @Test
    void posse_exportQueNaoEDeCancelamentoE404ComDownloadDeniedEIp() {
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(batch(TENANT, "READY", "XLSX", "{}", OffsetDateTime.now().plusDays(1))));

        assertThatThrownBy(() -> service.prepararPosse("tok", "203.0.113.9"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(repo).registrarAuditoria(ID, TENANT, "DOWNLOAD_DENIED", null, "203.0.113.9");
        verify(repo, never()).marcarBaixado(any());
    }

    @Test
    void posse_tokenInexistenteE404SemAuditoriaPoisNaoHaTenant() {
        when(repo.buscarPorToken("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepararPosse("x", "203.0.113.9"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(repo, never()).registrarAuditoria(any(), any(), any(), any(), any());
    }

    @Test
    void posse_expiradoOuNaoBaixavelE410SemMarcarBaixado() {
        when(repo.buscarPorToken("velho")).thenReturn(Optional.of(cancelamento("READY", OffsetDateTime.now().minusMinutes(1))));
        when(repo.buscarPorToken("expirado")).thenReturn(Optional.of(cancelamento("EXPIRED", OffsetDateTime.now().plusDays(1))));

        for (String token : List.of("velho", "expirado")) {
            assertThatThrownBy(() -> service.prepararPosse(token, null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
        }
        verify(repo, never()).marcarBaixado(any());
    }

    @Test
    void posse_ipEmBrancoViraNuloEIpGrandeDemaisECortadoNoTamanhoDaColuna() throws Exception {
        arquivo("7/" + ID + "/cadastros.cliente.xlsx", "c");
        when(repo.buscarPorToken("tok")).thenReturn(Optional.of(cancelamento("READY", OffsetDateTime.now().plusDays(1))));

        service.prepararPosse("tok", "   ");
        service.prepararPosse("tok", "9".repeat(60));

        verify(repo).registrarAuditoria(ID, TENANT, "DOWNLOADED", null, null);
        verify(repo).registrarAuditoria(ID, TENANT, "DOWNLOADED", null, "9".repeat(45));   // export_audit_log.ip_address é VARCHAR(45)
    }

    @Test
    void caminhoQueFogeDoBaseDirEhRecusado() throws Exception {
        Path fora = Files.writeString(base.getParent().resolve("segredo-" + ID + ".txt"), "nao pode sair");
        try {
            when(repo.buscarPorToken("tok")).thenReturn(Optional.of(batch(TENANT, "READY", "XLSX",
                    "{\"cadastros.cliente\":\"../" + fora.getFileName() + "\"}", OffsetDateTime.now().plusDays(1))));

            esperaStatus("tok", HttpStatus.GONE);
        } finally {
            Files.deleteIfExists(fora);
        }
    }
}
