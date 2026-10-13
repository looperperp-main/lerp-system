package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ErroLinha;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.RelatorioErros;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.domain.ErroCampo;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaComErro;
import com.l.erp.migracaoservice.util.ImportEntidade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Casos de uso do import — Trilha 1 (template). Upload: valida na borda, grava o ORIGINAL em disco,
 * lê o arquivo em streaming e grava o staging numa transação só (tudo ou nada), e dispara a validação
 * assíncrona. Erro estrutural (arquivo ilegível, colunas fora do modelo, acima do limite) vira batch
 * FAILED com mensagem acionável e 422 imediato — nunca um timeout (spec migracao §10).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    private static final int NOME_ARQUIVO_MAX = 255;

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final CanonicalSchemaRepository schema;
    private final ImportValidationService validacao;
    private final ImportPromotionService promocao;
    private final ImportProperties props;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public ImportBatchResponse enviar(Long tenantId, UUID userId, String entidadeInformada, MultipartFile arquivo) {
        String entidade = ImportEntidade.normalizar(entidadeInformada);
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessException(Constants.IMPORT_ARQUIVO_VAZIO, HttpStatus.BAD_REQUEST);
        }
        String nome = nomeSeguro(arquivo.getOriginalFilename());
        String extensao = extensao(nome);
        List<CampoCanonico> campos = schema.ativos(entidade, Constants.MIGRACAO_SCHEMA_VERSION_ATUAL);
        if (campos.isEmpty()) throw new BusinessException(Constants.IMPORT_ENTIDADE_INVALIDA, HttpStatus.BAD_REQUEST);

        UUID id = UUID.randomUUID();
        // O caminho é montado aqui, nunca a partir do nome enviado: sem path traversal possível.
        Path relativo = Path.of(tenantId.toString(), id.toString(), "original." + extensao);
        Path destino = props.baseDir().resolve(relativo);
        gravar(arquivo, destino);
        batches.inserir(id, tenantId, userId, entidade, Constants.MIGRACAO_SCHEMA_VERSION_ATUAL,
                Constants.IMPORT_TRILHA_TEMPLATE, nome, relativo.toString().replace('\\', '/'),
                OffsetDateTime.now().plusDays(props.retentionDays()));

        CarregadorStaging carregador = new CarregadorStaging(
                campos, rows, mapper, tenantId, id, entidade, Constants.IMPORT_TRILHA_TEMPLATE);
        try {
            batches.definirTotal(id, tenantId, carregar(id, tenantId, extensao, destino, carregador));
        } catch (ColunasForaDoModeloException e) {
            // Trilha 2 (spec §7): as colunas não batem com o modelo. Nada foi para o staging (o cabeçalho é a 1ª linha);
            // o arquivo fica em disco e o batch espera o cliente mapear as colunas.
            batches.aguardarMapeamento(id, tenantId);
            return converter(batches.buscar(id, tenantId).orElseThrow(), Map.of(), null);
        }
        validacao.validar(id, tenantId);
        return converter(batches.buscar(id, tenantId).orElseThrow(), Map.of(), carregador.colunasIgnoradas());
    }

    /**
     * Lê o arquivo para o staging numa transação só (tudo ou nada) e devolve o total de linhas. Erro do arquivo vira
     * FAILED + 422; falha de banco, FAILED + 5xx. Só a queda na Trilha 2 sai sem marcar o batch como falho.
     */
    int carregar(UUID id, Long tenantId, String extensao, Path arquivo, CarregadorStaging carregador) {
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                lerArquivo(extensao, arquivo, carregador);
                return carregador.concluir();
            }));
        } catch (ColunasForaDoModeloException e) {
            throw e;
        } catch (BusinessException e) {
            batches.falhar(id, tenantId, e.getMessage());
            throw e;
        } catch (UncheckedIOException e) {
            log.warn("[import {}] arquivo ilegível: {}", id, e.toString());
            batches.falhar(id, tenantId, Constants.IMPORT_ARQUIVO_ILEGIVEL);
            throw new BusinessException(Constants.IMPORT_ARQUIVO_ILEGIVEL, HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (RuntimeException e) {
            // Falha nossa (banco), não do arquivo: o 5xx e a stack saem no GlobalExceptionHandler.
            batches.falhar(id, tenantId, Constants.IMPORT_FALHA_CARGA);
            throw e;
        }
    }

    /**
     * Confirma a importação (spec §11): só um batch READY, exatamente uma vez (o UPDATE condicional é a guarda contra
     * duplo clique e contra duas abas). Entidade sem pré-requisito, então não há checagem de ordem; quando entrar
     * OPENING_BALANCE, o bloqueio de "plano de contas primeiro" entra aqui, antes de iniciar a promoção.
     */
    public ImportBatchResponse promover(Long tenantId, UUID userId, UUID id) {
        buscarOuFalhar(tenantId, id);
        if (!batches.iniciarPromocao(id, tenantId)) {
            throw new BusinessException(Constants.IMPORT_PROMOCAO_INDISPONIVEL, HttpStatus.CONFLICT);
        }
        promocao.promover(id, tenantId, userId);
        return detalhar(tenantId, id);
    }

    public Pagina<ImportBatchResponse> listar(Long tenantId, int pagina, int tamanho) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA);
        List<ImportBatch> lote = batches.listar(tenantId, t, p * t);
        Map<UUID, Map<String, Long>> contagens = rows.contarPorStatus(tenantId, lote.stream().map(ImportBatch::id).toList());
        List<ImportBatchResponse> itens = lote.stream()
                .map(b -> converter(b, contagens.getOrDefault(b.id(), Map.of()), null)).toList();
        return new Pagina<>(itens, batches.contar(tenantId), p, t);
    }

    public ImportBatchResponse detalhar(Long tenantId, UUID id) {
        ImportBatch batch = buscarOuFalhar(tenantId, id);
        return converter(batch, contagens(tenantId, id), null);
    }

    /** Relatório de erros acionável (spec §9): linha da planilha, campo, mensagem e o valor enviado. */
    @SuppressWarnings("unchecked")
    public RelatorioErros erros(Long tenantId, UUID id, int pagina, int tamanho) {
        ImportBatch batch = buscarOuFalhar(tenantId, id);
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamanho, 1), Constants.IMPORT_PAGINA_ERROS_MAX);
        Map<String, Long> contagem = contagens(tenantId, id);

        List<ErroLinha> erros = new ArrayList<>();
        for (LinhaComErro linha : rows.listarErros(id, tenantId, t, p * t)) {
            if (linha.errosJson() == null) continue;
            Map<String, String> valores = mapper.readValue(linha.rawJson(), Map.class);
            for (ErroCampo erro : mapper.readValue(linha.errosJson(), ErroCampo[].class)) {
                erros.add(new ErroLinha(linha.numero(), erro.field(), erro.message(), valores.get(erro.field())));
            }
        }
        return new RelatorioErros(id, total(batch), validas(contagem),
                contagem.getOrDefault(Constants.IMPORT_LINHA_ERROR, 0L), erros, p, t);
    }

    private ImportBatch buscarOuFalhar(Long tenantId, UUID id) {
        // Sempre filtrado por tenant: id de outro tenant é 404, igual a id inexistente.
        return batches.buscar(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.IMPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
    }

    private Map<String, Long> contagens(Long tenantId, UUID id) {
        return rows.contarPorStatus(tenantId, List.of(id)).getOrDefault(id, Map.of());
    }

    private ImportBatchResponse converter(ImportBatch b, Map<String, Long> contagem, List<String> colunasIgnoradas) {
        String falha = Constants.IMPORT_STATUS_FAILED.equals(b.status()) ? b.failureReason() : null;
        return new ImportBatchResponse(b.id(), b.entityType(), b.status(), b.sourceTrack(), b.originalFileName(),
                total(b), validas(contagem), contagem.getOrDefault(Constants.IMPORT_LINHA_ERROR, 0L),
                contagem.getOrDefault(Constants.IMPORT_LINHA_PROMOVIDA, 0L),
                contagem.getOrDefault(Constants.IMPORT_LINHA_IGNORADA, 0L), falha, colunasIgnoradas,
                b.createdAt(), b.updatedAt());
    }

    /** Linha que passou na validação continua "válida" depois de promovida ou ignorada (o total do relatório não encolhe). */
    private static long validas(Map<String, Long> contagem) {
        return contagem.getOrDefault(Constants.IMPORT_LINHA_VALID, 0L)
                + contagem.getOrDefault(Constants.IMPORT_LINHA_PROMOVIDA, 0L)
                + contagem.getOrDefault(Constants.IMPORT_LINHA_IGNORADA, 0L);
    }

    private static int total(ImportBatch b) {
        return b.rowCount() == null ? 0 : b.rowCount();
    }

    private void lerArquivo(String extensao, Path arquivo, CarregadorStaging carregador) {
        try {
            LeitorTabular.para(extensao).ler(arquivo, carregador);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void gravar(MultipartFile arquivo, Path destino) {
        try {
            Files.createDirectories(destino.getParent());
            arquivo.transferTo(destino);
        } catch (IOException e) {
            throw new UncheckedIOException(e);     // disco: falha nossa, vira 500 no handler global
        }
    }

    /** Navegadores antigos mandam o caminho completo; fica só o último segmento, limitado ao tamanho da coluna. */
    private static String nomeSeguro(String original) {
        String nome = original == null ? "" : original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1).strip();
        return nome.length() > NOME_ARQUIVO_MAX ? nome.substring(nome.length() - NOME_ARQUIVO_MAX) : nome;
    }

    private static String extensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        String ext = ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        if (!ext.equals(Constants.IMPORT_EXTENSAO_XLSX) && !ext.equals(Constants.IMPORT_EXTENSAO_CSV)) {
            throw new BusinessException(Constants.IMPORT_FORMATO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
        return ext;
    }
}
