package com.l.erp.migracaoservice.services;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.infra.config.ExportProperties;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Download autenticado do export (spec §6): o token sozinho não basta, a sessão precisa ser do
 * tenant dono do batch. Tenant diferente responde 404 (igual a token inexistente) e grava
 * DOWNLOAD_DENIED. Um arquivo sai direto; vários saem num zip montado em streaming.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportDownloadService {

    private final ExportBatchRepository repo;
    private final ExportProperties props;
    private final ObjectMapper mapper;

    public record Arquivo(String nome, String contentType, StreamingResponseBody corpo) {}

    public Arquivo preparar(Long tenantId, UUID userId, String token) {
        ExportBatch batch = repo.buscarPorToken(token)
                .orElseThrow(() -> new BusinessException(Constants.EXPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));

        if (!batch.tenantId().equals(tenantId)) {
            repo.registrarAuditoria(batch.id(), batch.tenantId(), Constants.EXPORT_AUDIT_DOWNLOAD_DENIED, userId);
            log.warn("Download negado: token do batch {} usado por outro tenant", batch.id());
            throw new BusinessException(Constants.EXPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND);
        }
        boolean baixavel = Constants.EXPORT_STATUS_READY.equals(batch.status())
                || Constants.EXPORT_STATUS_DOWNLOADED.equals(batch.status());
        if (!baixavel || batch.expiresAt() == null || !batch.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(Constants.EXPORT_EXPIRADA, HttpStatus.GONE);
        }

        Map<String, Path> entradas = resolverArquivos(batch);
        Arquivo arquivo = entradas.size() == 1 ? arquivoUnico(batch, entradas) : zip(batch, entradas);

        repo.marcarBaixado(batch.id());
        repo.registrarAuditoria(batch.id(), tenantId, Constants.EXPORT_AUDIT_DOWNLOADED, userId);
        return arquivo;
    }

    /** nome no download → arquivo no disco; no CSV leva junto o .meta.json (o import precisa dele). */
    private Map<String, Path> resolverArquivos(ExportBatch batch) {
        Path base = props.baseDir().toAbsolutePath().normalize();
        @SuppressWarnings("unchecked")
        Map<String, String> caminhos = mapper.readValue(batch.filePathsJson(), Map.class);
        Map<String, Path> entradas = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : caminhos.entrySet()) {
            Path arquivo = base.resolve(e.getValue()).normalize();
            // Confinamento: o caminho gravado nunca pode sair do base-dir.
            if (!arquivo.startsWith(base) || !Files.isRegularFile(arquivo)) {
                log.error("Arquivo do batch {} ausente ou fora do base-dir: {}", batch.id(), e.getValue());
                throw new BusinessException(Constants.EXPORT_ARQUIVO_INDISPONIVEL, HttpStatus.GONE);
            }
            entradas.put(arquivo.getFileName().toString(), arquivo);
            Path meta = arquivo.resolveSibling(e.getKey() + ".meta.json");
            if (Constants.EXPORT_FORMATO_CSV.equals(batch.format()) && Files.isRegularFile(meta)) {
                entradas.put(meta.getFileName().toString(), meta);
            }
        }
        return entradas;
    }

    private Arquivo arquivoUnico(ExportBatch batch, Map<String, Path> entradas) {
        Map.Entry<String, Path> unico = entradas.entrySet().iterator().next();
        String tipo = Constants.EXPORT_FORMATO_CSV.equals(batch.format()) ? "text/csv; charset=UTF-8"
                : Constants.EXPORT_CONTENT_TYPE_XLSX;
        return new Arquivo(unico.getKey(), tipo, saida -> Files.copy(unico.getValue(), saida));
    }

    private Arquivo zip(ExportBatch batch, Map<String, Path> entradas) {
        String nome = "syax-exportacao-" + batch.createdAt().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip";
        return new Arquivo(nome, Constants.EXPORT_CONTENT_TYPE_ZIP, saida -> {
            try (ZipOutputStream zip = new ZipOutputStream(saida)) {
                for (Map.Entry<String, Path> e : entradas.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    Files.copy(e.getValue(), zip);
                    zip.closeEntry();
                }
            } catch (IOException ex) {
                log.warn("Download do batch {} interrompido: {}", batch.id(), ex.getMessage());
                throw ex;
            }
        });
    }
}
