package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ImportDtos.AdminImportResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.OnboardingSla;
import com.l.erp.migracaoservice.api.dto.ImportDtos.PainelSla;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository.OnboardingLinha;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.util.ImportEntidade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Trilha 3 — concierge (spec migracao §8) e medição do SLA (§13). O cliente pede ajuda explicitamente (nunca há
 * roteamento automático para cá); o pedido entra na fila com prazo de 48h. O suporte devolve uma prévia no formato do
 * modelo, que segue o fluxo normal (validação → READY): aprovar é confirmar a importação, recusar devolve o pedido à
 * fila. A partir da 3ª recusa o pedido é marcado como escalado (revisão sênior).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportConciergeService {

    private static final int ONBOARDINGS_MAX = 200;
    private static final int FILA_MAX = 100;
    private static final int NOME_ARQUIVO_MAX = 255;

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final CanonicalSchemaRepository schema;
    private final ImportService importacao;
    private final ImportValidationService validacao;
    private final ImportProperties props;
    private final ObjectMapper mapper;

    // ───────────────────────── Cliente ─────────────────────────

    /** O arquivo é opcional: o cliente pode estar sem planilha (PDF escaneado, papel) e combinar o envio com o suporte. */
    public ImportBatchResponse solicitar(Long tenantId, UUID userId, String entidadeInformada, MultipartFile arquivo) {
        String entidade = ImportEntidade.normalizar(entidadeInformada);
        if (schema.ativos(entidade, Constants.MIGRACAO_SCHEMA_VERSION_ATUAL).isEmpty()) {
            throw new BusinessException(Constants.IMPORT_ENTIDADE_INVALIDA, HttpStatus.BAD_REQUEST);
        }
        boolean comArquivo = arquivo != null && !arquivo.isEmpty();
        String nome = comArquivo ? nomeSeguro(arquivo.getOriginalFilename()) : null;
        String extensao = comArquivo ? extensaoConcierge(nome) : null;

        UUID id = UUID.randomUUID();
        Path relativo = comArquivo ? Path.of(tenantId.toString(), id.toString(), "original." + extensao) : null;
        if (comArquivo) gravar(arquivo, props.baseDir().resolve(relativo));
        batches.inserirConcierge(id, tenantId, userId, entidade, Constants.MIGRACAO_SCHEMA_VERSION_ATUAL, nome,
                relativo == null ? null : relativo.toString().replace('\\', '/'),
                OffsetDateTime.now().plusDays(props.retentionDays()), Constants.IMPORT_CONCIERGE_PRAZO_HORAS);
        return importacao.detalhar(tenantId, id);
    }

    /** Recusa a prévia: apaga o staging, conta a recusa e devolve o pedido à fila com prazo novo. */
    public ImportBatchResponse recusar(Long tenantId, UUID id) {
        ImportBatch batch = batches.buscar(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.IMPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
        if (!Constants.IMPORT_TRILHA_CONCIERGE.equals(batch.sourceTrack())
                || !batches.recusarPrevia(id, tenantId, Constants.IMPORT_CONCIERGE_PRAZO_HORAS)) {
            throw new BusinessException(Constants.IMPORT_CONCIERGE_REJEICAO_INDISPONIVEL, HttpStatus.CONFLICT);
        }
        rows.purgarLinhas(id, tenantId);
        return importacao.detalhar(tenantId, id);
    }

    // ───────────────────────── Suporte da plataforma ─────────────────────────

    public Pagina<AdminImportResponse> listarFila(int pagina, int tamanho) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamanho, 1), FILA_MAX);
        return new Pagina<>(batches.listarFila(t, p * t).stream().map(this::paraAdmin).toList(), batches.contarFila(), p, t);
    }

    /** O concierge entrega o arquivo já normalizado para o modelo do SYAX; a validação é a mesma das outras trilhas. */
    public AdminImportResponse carregarPrevia(UUID id, MultipartFile arquivo) {
        ImportBatch batch = buscarConcierge(id);
        if (arquivo == null || arquivo.isEmpty()) throw new BusinessException(Constants.IMPORT_ARQUIVO_VAZIO, HttpStatus.BAD_REQUEST);
        String extensao = extensaoModelo(nomeSeguro(arquivo.getOriginalFilename()));
        Long tenantId = batch.tenantId();
        if (!batches.iniciarPrevia(id, tenantId)) {
            throw new BusinessException(Constants.IMPORT_CONCIERGE_PREVIA_INDISPONIVEL, HttpStatus.CONFLICT);
        }
        Path destino = props.baseDir().resolve(Path.of(tenantId.toString(), id.toString(), "previa." + extensao));
        try {
            gravar(arquivo, destino);
            List<CampoCanonico> campos = schema.ativos(batch.entityType(), batch.schemaVersion());
            CarregadorStaging carregador = new CarregadorStaging(
                    campos, rows, mapper, tenantId, id, batch.entityType(), Constants.IMPORT_TRILHA_CONCIERGE);
            batches.definirTotal(id, tenantId, importacao.carregar(id, tenantId, extensao, destino, carregador));
        } catch (RuntimeException e) {
            batches.reabrirFila(id, tenantId);   // o erro é do arquivo do concierge: o pedido do cliente não pode virar FAILED
            throw e;
        }
        validacao.validar(id, tenantId);
        return paraAdmin(batches.buscarAdmin(id).orElseThrow());
    }

    /** Arquivo original do cliente: só para o concierge trabalhar o pedido (e só de lote da Trilha 3). */
    public Resource arquivoDoCliente(UUID id) {
        ImportBatch batch = buscarConcierge(id);
        if (batch.rawFilePath() == null) throw new BusinessException(Constants.IMPORT_CONCIERGE_SEM_ARQUIVO, HttpStatus.NOT_FOUND);
        Path arquivo = props.baseDir().resolve(batch.rawFilePath()).normalize();
        if (!arquivo.startsWith(props.baseDir().normalize()) || !Files.isRegularFile(arquivo)) {
            throw new BusinessException(Constants.IMPORT_CONCIERGE_SEM_ARQUIVO, HttpStatus.NOT_FOUND);
        }
        return new FileSystemResource(arquivo);
    }

    public String nomeDoArquivo(UUID id) {
        return buscarConcierge(id).originalFileName();
    }

    // ───────────────────────── SLA (spec §13) ─────────────────────────

    public PainelSla painel() {
        List<AdminImportResponse> fila = batches.listarFila(FILA_MAX, 0).stream().map(this::paraAdmin).toList();
        List<OnboardingSla> onboardings = batches.onboardings(Constants.IMPORT_ONBOARDING_OBRIGATORIAS, ONBOARDINGS_MAX).stream()
                .map(ImportConciergeService::paraOnboarding).toList();
        return new PainelSla(batches.contarFila(), batches.listarVencidos().size(),
                batches.contarEscalados(Constants.IMPORT_CONCIERGE_MAX_REJEICOES), fila, onboardings);
    }

    /** Sem alerta, "SLA de 48h" é só uma frase num documento (spec §8): um job diário lista o que venceu. */
    @Scheduled(cron = "${syax.import.cron-sla:0 0 8 * * *}")
    public void alertarSla() {
        for (ImportBatch b : batches.listarVencidos()) {
            log.error(Constants.IMPORT_ALERTA_CONCIERGE_VENCIDO, b.id(), b.tenantId(), b.conciergeDeadlineAt());
        }
        for (OnboardingLinha o : batches.onboardings(Constants.IMPORT_ONBOARDING_OBRIGATORIAS, ONBOARDINGS_MAX)) {
            if (!concluido(o) && o.iniciadoEm().isBefore(OffsetDateTime.now().minusDays(Constants.IMPORT_ONBOARDING_ALERTA_DIAS))) {
                log.error(Constants.IMPORT_ALERTA_ONBOARDING_ABERTO, Constants.IMPORT_ONBOARDING_ALERTA_DIAS, o.tenantId(), o.iniciadoEm());
            }
        }
    }

    // ───────────────────────── interno ─────────────────────────

    private static boolean concluido(OnboardingLinha o) {
        return o.concluidas().containsAll(Constants.IMPORT_ONBOARDING_OBRIGATORIAS);
    }

    private static OnboardingSla paraOnboarding(OnboardingLinha o) {
        boolean concluido = concluido(o);
        OffsetDateTime fim = concluido ? o.ultimoDone() : OffsetDateTime.now();
        long horas = Duration.between(o.iniciadoEm(), fim).toHours();
        return new OnboardingSla(o.tenantId(), o.iniciadoEm(), concluido ? o.ultimoDone() : null, horas, o.concluidas(),
                horas > Constants.IMPORT_ONBOARDING_ALERTA_DIAS * 24L);
    }

    private AdminImportResponse paraAdmin(ImportBatch b) {
        boolean vencido = b.conciergeDeadlineAt() != null && b.conciergeDeadlineAt().isBefore(OffsetDateTime.now());
        return new AdminImportResponse(b.tenantId(), importacao.resumir(b), vencido);
    }

    private ImportBatch buscarConcierge(UUID id) {
        ImportBatch batch = batches.buscarAdmin(id)
                .orElseThrow(() -> new BusinessException(Constants.IMPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
        if (!Constants.IMPORT_TRILHA_CONCIERGE.equals(batch.sourceTrack())) {
            throw new BusinessException(Constants.IMPORT_CONCIERGE_PREVIA_INDISPONIVEL, HttpStatus.CONFLICT);
        }
        return batch;
    }

    private static void gravar(MultipartFile arquivo, Path destino) {
        try {
            Files.createDirectories(destino.getParent());
            arquivo.transferTo(destino);
        } catch (IOException e) {
            throw new UncheckedIOException(e);     // disco: falha nossa, vira 500 no handler global
        }
    }

    private static String nomeSeguro(String original) {
        String nome = original == null ? "" : original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1).strip();
        return nome.length() > NOME_ARQUIVO_MAX ? nome.substring(nome.length() - NOME_ARQUIVO_MAX) : nome;
    }

    private static String extensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    private static String extensaoConcierge(String nome) {
        String ext = extensao(nome);
        if (!Constants.IMPORT_CONCIERGE_EXTENSOES.contains(ext)) {
            throw new BusinessException(Constants.IMPORT_CONCIERGE_FORMATO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
        return ext;
    }

    private static String extensaoModelo(String nome) {
        String ext = extensao(nome);
        if (!ext.equals(Constants.IMPORT_EXTENSAO_XLSX) && !ext.equals(Constants.IMPORT_EXTENSAO_CSV)) {
            throw new BusinessException(Constants.IMPORT_FORMATO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
        return ext;
    }
}
