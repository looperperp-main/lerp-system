package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.MapeamentoRequest;
import com.l.erp.migracaoservice.api.dto.ImportDtos.MapeamentoResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.RelatorioErros;
import com.l.erp.migracaoservice.services.imports.ImportConciergeService;
import com.l.erp.migracaoservice.services.imports.ImportMappingService;
import com.l.erp.migracaoservice.services.imports.ImportService;
import com.l.erp.migracaoservice.services.imports.ImportTemplateService;
import com.l.erp.migracaoservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Import de dados do tenant, Trilha 1 (template do SYAX): modelo, upload, estado e relatório de erros. */
@RestController
@RequestMapping("/migracao/imports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Constants.MIGRACAO_AUTH_IMPORTAR + "')")
public class ImportController {

    private final ImportService service;
    private final ImportTemplateService modelos;
    private final ImportMappingService mapeamentos;
    private final ImportConciergeService concierge;

    /** Modelo em branco com as colunas exatas do schema canônico (xlsx por padrão, ou csv). */
    @GetMapping("/modelo/{entidade}")
    public ResponseEntity<byte[]> modelo(@PathVariable String entidade,
                                         @RequestParam(defaultValue = Constants.IMPORT_EXTENSAO_XLSX) String formato) {
        ImportTemplateService.Modelo modelo = modelos.gerar(entidade, formato);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(modelo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(modelo.nome()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(modelo.conteudo());
    }

    /** 202: o arquivo foi lido e o staging gravado; a validação segue em segundo plano (consultar o estado). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportBatchResponse> enviar(@RequestParam("entidade") String entidade,
                                                      @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                service.enviar(SecurityUtils.requireTenantId(), SecurityUtils.requireUserId(), entidade, arquivo));
    }

    /** 202: confirma a importação; a gravação no cadastro segue em segundo plano (consultar o estado até DONE ou FAILED). */
    @PostMapping("/{id}/promover")
    public ResponseEntity<ImportBatchResponse> promover(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                service.promover(SecurityUtils.requireTenantId(), SecurityUtils.requireUserId(), id));
    }

    /** Trilha 2: colunas do arquivo, campos do modelo e a sugestão de mapeamento (só com o batch em AWAITING_MAPPING). */
    @GetMapping("/{id}/mapeamento")
    public MapeamentoResponse mapeamento(@PathVariable UUID id) {
        return mapeamentos.consultar(SecurityUtils.requireTenantId(), id);
    }

    /** 202: o mapeamento foi aplicado e o staging gravado; a validação segue em segundo plano (consultar o estado). */
    @PostMapping("/{id}/mapeamento")
    public ResponseEntity<ImportBatchResponse> mapear(@PathVariable UUID id, @RequestBody MapeamentoRequest pedido) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                mapeamentos.aplicar(SecurityUtils.requireTenantId(), id, pedido.mapeamento()));
    }

    /**
     * Trilha 3: o cliente pede ajuda (ação explícita, nunca automática). 202: o pedido entra na fila do concierge com
     * prazo de 48h. O arquivo é opcional.
     */
    @PostMapping(path = "/concierge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportBatchResponse> pedirAjuda(@RequestParam("entidade") String entidade,
                                                          @RequestParam(value = "arquivo", required = false) MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                concierge.solicitar(SecurityUtils.requireTenantId(), SecurityUtils.requireUserId(), entidade, arquivo));
    }

    /** O cliente recusa a prévia do concierge: o pedido volta à fila (a 3ª recusa escala para revisão sênior). */
    @PostMapping("/{id}/concierge/recusar")
    public ImportBatchResponse recusarPrevia(@PathVariable UUID id) {
        return concierge.recusar(SecurityUtils.requireTenantId(), id);
    }

    @GetMapping
    public Pagina<ImportBatchResponse> listar(@RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(SecurityUtils.requireTenantId(), pagina, tamanho);
    }

    @GetMapping("/{id}")
    public ImportBatchResponse detalhar(@PathVariable UUID id) {
        return service.detalhar(SecurityUtils.requireTenantId(), id);
    }

    @GetMapping("/{id}/erros")
    public RelatorioErros erros(@PathVariable UUID id,
                                @RequestParam(defaultValue = "0") int pagina,
                                @RequestParam(defaultValue = "100") int tamanho) {
        return service.erros(SecurityUtils.requireTenantId(), id, pagina, tamanho);
    }
}
