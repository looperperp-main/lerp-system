package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ImportDtos.AdminImportResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.PainelSla;
import com.l.erp.migracaoservice.services.imports.ImportConciergeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Suporte da plataforma na Trilha 3 (concierge): fila de pedidos de qualquer tenant, painel de SLA, arquivo do cliente
 * (só de pedido do concierge, para o suporte trabalhar) e envio da prévia normalizada. Só ROLE_APP_OWNER.
 */
@RestController
@RequestMapping("/migracao/admin/imports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Constants.MIGRACAO_AUTH_ADMIN + "')")
public class AdminImportController {

    private final ImportConciergeService concierge;

    @GetMapping
    public Pagina<AdminImportResponse> fila(@RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "20") int tamanho) {
        return concierge.listarFila(pagina, tamanho);
    }

    @GetMapping("/sla")
    public PainelSla sla() {
        return concierge.painel();
    }

    @GetMapping("/{id}/arquivo")
    public ResponseEntity<Resource> arquivo(@PathVariable UUID id) {
        Resource recurso = concierge.arquivoDoCliente(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(concierge.nomeDoArquivo(id)).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(recurso);
    }

    /** O concierge envia o arquivo já no formato do modelo; a validação segue em segundo plano e o cliente vê a prévia. */
    @PostMapping(path = "/{id}/previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminImportResponse previa(@PathVariable UUID id, @RequestParam("arquivo") MultipartFile arquivo) {
        return concierge.carregarPrevia(id, arquivo);
    }
}
