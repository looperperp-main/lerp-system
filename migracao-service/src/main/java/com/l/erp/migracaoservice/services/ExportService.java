package com.l.erp.migracaoservice.services;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.AdminExportResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.CatalogoGrupoResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.CatalogoTabelaResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.EventoResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportRequest;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ExportDtos.StatusResponse;
import com.l.erp.migracaoservice.domain.CatalogTable;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.domain.ExportScope;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import com.l.erp.migracaoservice.util.ExportScopeHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** Casos de uso do export. Sem etapa manual: o pedido já dispara a extração (LGPD — spec export §2). */
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ExportBatchRepository repo;
    private final ExportCatalogService catalogo;
    private final ExportExtractionService extracao;
    private final ObjectMapper mapper;

    @Transactional
    public ExportBatchResponse solicitar(Long tenantId, UUID userId, ExportRequest req) {
        String formato = normalizarFormato(req.formato());
        String motivo = normalizarMotivo(req.motivo());
        validarPeriodo(req.dataInicial(), req.dataFinal());

        Map<String, CatalogTable> disponiveis = catalogo.descobrir().stream()
                .collect(Collectors.toMap(CatalogTable::key, t -> t));
        List<String> tabelas = req.tabelas().stream().map(String::trim).distinct().sorted().toList();
        for (String t : tabelas) {
            if (!disponiveis.containsKey(t)) {
                throw new BusinessException(String.format(Constants.EXPORT_TABELA_INDISPONIVEL, t), HttpStatus.BAD_REQUEST);
            }
        }

        ExportScope escopo = new ExportScope(tabelas, req.dataInicial(), req.dataFinal());
        String hash = ExportScopeHasher.hash(escopo, formato);
        Optional<ExportBatch> existente = repo.buscarProntoPorEscopo(tenantId, hash);
        if (existente.isPresent()) {
            return converter(existente.get(), true, null);
        }

        UUID id = UUID.randomUUID();
        repo.inserir(id, tenantId, userId, mapper.writeValueAsString(escopo), hash, formato, motivo);
        repo.registrarAuditoria(id, tenantId, Constants.EXPORT_AUDIT_REQUESTED, userId);
        // Só depois do commit: o @Async roda noutra thread e precisa enxergar a linha REQUESTED.
        aposCommit(() -> extracao.executar(id, tenantId));
        return converter(repo.buscar(id, tenantId).orElseThrow(), false, null);
    }

    public Pagina<ExportBatchResponse> listar(Long tenantId, int pagina, int tamanho) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA);
        List<ExportBatchResponse> itens = repo.listar(tenantId, t, p * t).stream()
                .map(b -> converter(b, false, null)).toList();
        return new Pagina<>(itens, repo.contar(tenantId), p, t);
    }

    public ExportBatchResponse detalhar(Long tenantId, UUID id) {
        ExportBatch b = buscarOuFalhar(tenantId, id);
        List<EventoResponse> eventos = repo.listarAuditoria(id, tenantId).stream()
                .map(a -> new EventoResponse(a.acao(), a.ator(), a.em())).toList();
        return converter(b, false, eventos);
    }

    /** Suporte: lista exports de qualquer tenant (filtros opcionais), só metadados. */
    public Pagina<AdminExportResponse> listarAdmin(Long tenantId, String status, int pagina, int tamanho) {
        String statusNormalizado = null;
        if (status != null && !status.isBlank()) {
            statusNormalizado = status.trim().toUpperCase();
            if (!Constants.EXPORT_STATUS_TODOS.contains(statusNormalizado)) {
                throw new BusinessException(Constants.EXPORT_STATUS_INVALIDO, HttpStatus.BAD_REQUEST);
            }
        }
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA);
        List<AdminExportResponse> itens = repo.listarAdmin(tenantId, statusNormalizado, t, p * t).stream()
                .map(b -> new AdminExportResponse(b.tenantId(), converter(b, false, null))).toList();
        return new Pagina<>(itens, repo.contarAdmin(tenantId, statusNormalizado), p, t);
    }

    public AdminExportResponse detalharAdmin(UUID id) {
        ExportBatch b = repo.buscarDeQualquerTenant(id)
                .orElseThrow(() -> new BusinessException(Constants.EXPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
        List<EventoResponse> eventos = repo.listarAuditoria(id, b.tenantId()).stream()
                .map(a -> new EventoResponse(a.acao(), a.ator(), a.em())).toList();
        return new AdminExportResponse(b.tenantId(), converter(b, false, eventos));
    }

    public StatusResponse status(Long tenantId, UUID id) {
        ExportBatch b = buscarOuFalhar(tenantId, id);
        return new StatusResponse(b.id(), b.status(), mensagemFalha(b));
    }

    public List<CatalogoGrupoResponse> catalogo() {
        Map<String, List<CatalogTable>> porSchema = catalogo.descobrir().stream()
                .collect(Collectors.groupingBy(CatalogTable::schema, TreeMap::new, Collectors.toList()));
        return porSchema.entrySet().stream().map(e -> new CatalogoGrupoResponse(
                e.getKey(), e.getValue().get(0).groupLabel(),
                e.getValue().stream().map(t -> new CatalogoTabelaResponse(t.key(), t.label(), t.dateColumn() != null)).toList()
        )).toList();
    }

    private ExportBatch buscarOuFalhar(Long tenantId, UUID id) {
        // Sempre filtrado por tenant: id de outro tenant é 404, igual a id inexistente.
        return repo.buscar(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.EXPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
    }

    private ExportBatchResponse converter(ExportBatch b, boolean reaproveitado, List<EventoResponse> eventos) {
        ExportScope escopo = mapper.readValue(b.scopeJson(), ExportScope.class);
        return new ExportBatchResponse(b.id(), b.status(), b.format(), escopo.tabelas(), escopo.dataInicial(),
                escopo.dataFinal(), b.reason(), b.requestedBy(), b.attemptCount(), mensagemFalha(b), contagens(b),
                b.createdAt(), b.readyAt(), b.expiresAt(), b.downloadedAt(), reaproveitado, eventos);
    }

    private String mensagemFalha(ExportBatch b) {
        return Constants.EXPORT_STATUS_FAILED.equals(b.status()) ? b.failureReason() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> contagens(ExportBatch b) {
        if (b.tableCountsJson() == null) return null;
        Map<String, Long> out = new LinkedHashMap<>();
        ((Map<String, Object>) mapper.readValue(b.tableCountsJson(), Map.class))
                .forEach((k, v) -> out.put(k, ((Number) v).longValue()));
        return out;
    }

    private String normalizarFormato(String formato) {
        if (formato == null || formato.isBlank()) return Constants.EXPORT_FORMATO_XLSX;
        String f = formato.trim().toUpperCase();
        if (!f.equals(Constants.EXPORT_FORMATO_XLSX) && !f.equals(Constants.EXPORT_FORMATO_CSV)) {
            throw new BusinessException(Constants.EXPORT_FORMATO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
        return f;
    }

    /** Motivo é opcional e nunca bloqueia a geração (spec §5): valor desconhecido é recusado, ausente passa. */
    private String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) return null;
        String m = motivo.trim().toUpperCase();
        if (!Constants.EXPORT_MOTIVOS.contains(m)) {
            throw new BusinessException(Constants.EXPORT_MOTIVO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
        return m;
    }

    private void validarPeriodo(LocalDate de, LocalDate ate) {
        if (de != null && ate != null && ate.isBefore(de)) {
            throw new BusinessException(Constants.EXPORT_PERIODO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
    }

    private void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }
}
