package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.CatalogoGrupoResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportRequest;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ExportDtos.StatusResponse;
import com.l.erp.migracaoservice.services.ExportDownloadService;
import com.l.erp.migracaoservice.services.ExportService;
import com.l.erp.migracaoservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Export de dados do tenant (self-service, sem aprovação). Inclui o download autenticado por token. */
@RestController
@RequestMapping("/migracao/exports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Constants.MIGRACAO_AUTH_EXPORTAR + "')")
public class ExportController {

    private final ExportService service;
    private final ExportDownloadService downloadService;

    /** Token + sessão do tenant dono (spec §6). Arquivo único sai direto; vários, num zip. */
    @GetMapping("/download/{token}")
    public ResponseEntity<StreamingResponseBody> baixar(@PathVariable String token) {
        ExportDownloadService.Arquivo arquivo = downloadService.preparar(
                SecurityUtils.requireTenantId(), SecurityUtils.requireUserId(), token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(arquivo.corpo());
    }

    /** Download pela tela do cliente: por id do export, sem expor o token (que só circula no e-mail de posse). */
    @GetMapping("/{id}/arquivo")
    public ResponseEntity<StreamingResponseBody> baixarPorId(@PathVariable UUID id) {
        ExportDownloadService.Arquivo arquivo = downloadService.prepararPorId(
                SecurityUtils.requireTenantId(), SecurityUtils.requireUserId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(arquivo.corpo());
    }

    /**
     * Download de posse (spec §10.1): SEM sessão, para o dono do dado cujo tenant já foi desativado. O token é a
     * credencial; o gateway deixa a rota pública e entrega o IP real em X-Client-Ip. Só exports de cancelamento.
     * O @PreAuthorize do método sobrepõe o da classe (que exige a permissão MIGRACAO_EXPORTAR).
     */
    @PreAuthorize("permitAll()")
    @GetMapping("/posse/{token}")
    public ResponseEntity<StreamingResponseBody> baixarPorPosse(@PathVariable String token,
                                                                @RequestHeader(value = Constants.HEADER_CLIENT_IP, required = false) String ip) {
        ExportDownloadService.Arquivo arquivo = downloadService.prepararPosse(token, ip);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(arquivo.corpo());
    }

    /** 202 quando a extração foi iniciada; 200 quando o dedupe devolveu uma exportação pronta idêntica. */
    @PostMapping
    public ResponseEntity<ExportBatchResponse> solicitar(@Valid @RequestBody ExportRequest request) {
        ExportBatchResponse resposta = service.solicitar(
                SecurityUtils.requireTenantId(), SecurityUtils.requireUserId(), request);
        return ResponseEntity.status(resposta.reaproveitado() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(resposta);
    }

    @GetMapping("/catalogo")
    public List<CatalogoGrupoResponse> catalogo() {
        return service.catalogo();
    }

    @GetMapping
    public Pagina<ExportBatchResponse> listar(@RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(SecurityUtils.requireTenantId(), pagina, tamanho);
    }

    @GetMapping("/{id}")
    public ExportBatchResponse detalhar(@PathVariable UUID id) {
        return service.detalhar(SecurityUtils.requireTenantId(), id);
    }

    @GetMapping("/{id}/status")
    public StatusResponse status(@PathVariable UUID id) {
        return service.status(SecurityUtils.requireTenantId(), id);
    }
}
