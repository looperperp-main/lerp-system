package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ImportDtos.CampoAlvo;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.MapeamentoResponse;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Trilha 2 — mapeamento self-service (spec migracao §7). O arquivo cujas colunas não batem com o modelo fica em
 * disco com o batch em AWAITING_MAPPING; aqui o cliente vê as colunas do arquivo, associa cada uma a um campo do
 * modelo e confirma. O mapeamento é gravado no batch (e sugerido nos próximos imports DO MESMO tenant), o staging é
 * carregado já mapeado e o fluxo segue igual ao da Trilha 1 (validação assíncrona, relatório de erros, promoção).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportMappingService {

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final CanonicalSchemaRepository schema;
    private final ImportService importacao;
    private final ImportValidationService validacao;
    private final ImportProperties props;
    private final ObjectMapper mapper;

    /** Dados da tela: colunas do arquivo, campos do modelo e o mapeamento sugerido. */
    public MapeamentoResponse consultar(Long tenantId, UUID id) {
        ImportBatch batch = aguardandoMapeamento(tenantId, id);
        List<CampoCanonico> campos = schema.ativos(batch.entityType(), batch.schemaVersion());
        List<String> colunas = colunasDoArquivo(batch);
        return new MapeamentoResponse(id, batch.entityType(), colunas,
                campos.stream().map(c -> new CampoAlvo(c.nome(), c.ajuda(), c.tipo(), c.obrigatorio())).toList(),
                sugerir(tenantId, batch.entityType(), campos, colunas));
    }

    /**
     * Confirma o mapeamento: a transição AWAITING_MAPPING → UPLOADED é o UPDATE condicional que impede o segundo
     * clique de carregar o arquivo duas vezes. Mapeamento inválido é 400 e o batch continua aguardando.
     */
    public ImportBatchResponse aplicar(Long tenantId, UUID id, Map<String, String> pedido) {
        ImportBatch batch = aguardandoMapeamento(tenantId, id);
        List<CampoCanonico> campos = schema.ativos(batch.entityType(), batch.schemaVersion());
        Map<String, String> mapeamento = validar(campos, colunasDoArquivo(batch), pedido);
        if (!batches.iniciarMapeamento(id, tenantId, mapper.writeValueAsString(mapeamento))) {
            throw new BusinessException(Constants.IMPORT_MAPEAMENTO_INDISPONIVEL, HttpStatus.CONFLICT);
        }
        CarregadorStaging carregador = new CarregadorStaging(campos, rows, mapper, tenantId, id, batch.entityType(),
                Constants.IMPORT_TRILHA_SELF_MAPPED, mapeamento);
        batches.definirTotal(id, tenantId,
                importacao.carregar(id, tenantId, extensao(batch), caminho(batch), carregador));
        validacao.validar(id, tenantId);
        return importacao.detalhar(tenantId, id);
    }

    private ImportBatch aguardandoMapeamento(Long tenantId, UUID id) {
        // Sempre filtrado por tenant: id de outro tenant é 404, igual a id inexistente.
        ImportBatch batch = batches.buscar(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.IMPORT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
        if (!Constants.IMPORT_STATUS_AWAITING_MAPPING.equals(batch.status())) {
            throw new BusinessException(Constants.IMPORT_MAPEAMENTO_INDISPONIVEL, HttpStatus.CONFLICT);
        }
        return batch;
    }

    /**
     * Valida o pedido do cliente e o devolve com o nome EXATO da coluna do arquivo (o que o cliente digitou pode
     * diferir em caixa/acento). Erro aqui é do cliente (400): a mensagem diz qual campo ou coluna ajustar.
     */
    private Map<String, String> validar(List<CampoCanonico> campos, List<String> colunas, Map<String, String> pedido) {
        if (pedido == null || pedido.isEmpty()) {
            throw new BusinessException(Constants.IMPORT_MAPEAMENTO_VAZIO, HttpStatus.BAD_REQUEST);
        }
        Set<String> nomes = campos.stream().map(CampoCanonico::nome).collect(Collectors.toSet());
        for (String campo : pedido.keySet()) {
            if (!nomes.contains(campo)) {
                throw new BusinessException(String.format(Constants.IMPORT_MAPEAMENTO_CAMPO_DESCONHECIDO, campo), HttpStatus.BAD_REQUEST);
            }
        }
        Map<String, String> porNome = colunasPorNome(colunas);
        Map<String, String> resolvido = new LinkedHashMap<>();
        Set<String> usadas = new HashSet<>();
        for (CampoCanonico campo : campos) {
            String informado = pedido.get(campo.nome());
            if (informado == null || informado.isBlank()) continue;
            String coluna = porNome.get(CarregadorStaging.normalizarCabecalho(informado));
            if (coluna == null) {
                throw new BusinessException(String.format(Constants.IMPORT_MAPEAMENTO_COLUNA_INEXISTENTE, informado.strip()), HttpStatus.BAD_REQUEST);
            }
            if (!usadas.add(coluna)) {
                throw new BusinessException(String.format(Constants.IMPORT_MAPEAMENTO_COLUNA_REPETIDA, coluna), HttpStatus.BAD_REQUEST);
            }
            resolvido.put(campo.nome(), coluna);
        }
        List<String> faltando = campos.stream()
                .filter(c -> c.obrigatorio() && !resolvido.containsKey(c.nome()))
                .map(CampoCanonico::nome).toList();
        if (!faltando.isEmpty()) {
            throw new BusinessException(String.format(Constants.IMPORT_MAPEAMENTO_OBRIGATORIOS, String.join(", ", faltando)), HttpStatus.BAD_REQUEST);
        }
        return resolvido;
    }

    /** Último mapeamento do tenant (só as colunas que existem neste arquivo) e, no que sobrar, coluna com o mesmo nome do campo. */
    @SuppressWarnings("unchecked")
    private Map<String, String> sugerir(Long tenantId, String entidade, List<CampoCanonico> campos, List<String> colunas) {
        Map<String, String> porNome = colunasPorNome(colunas);
        Map<String, String> salvo = batches.ultimoMapeamento(tenantId, entidade)
                .map(json -> (Map<String, String>) mapper.readValue(json, Map.class)).orElse(Map.of());
        Map<String, String> sugestao = new LinkedHashMap<>();
        Set<String> usadas = new HashSet<>();
        for (CampoCanonico campo : campos) {
            String coluna = porNome.get(CarregadorStaging.normalizarCabecalho(salvo.get(campo.nome())));
            if (coluna == null) coluna = porNome.get(campo.nome());
            if (coluna != null && usadas.add(coluna)) sugestao.put(campo.nome(), coluna);
        }
        return sugestao;
    }

    /** Nome normalizado → nome como aparece no arquivo; na repetição vale a 1ª coluna, a mesma que o carregador usa. */
    private static Map<String, String> colunasPorNome(List<String> colunas) {
        Map<String, String> porNome = new LinkedHashMap<>();
        for (String coluna : colunas) porNome.putIfAbsent(CarregadorStaging.normalizarCabecalho(coluna), coluna);
        return porNome;
    }

    private List<String> colunasDoArquivo(ImportBatch batch) {
        try {
            return LeitorCabecalho.ler(LeitorTabular.para(extensao(batch)), caminho(batch)).stream()
                    .map(String::strip).filter(c -> !c.isEmpty()).distinct().toList();
        } catch (IOException e) {
            log.warn("[import {}] não foi possível reler o cabeçalho: {}", batch.id(), e.toString());
            throw new BusinessException(Constants.IMPORT_ARQUIVO_ILEGIVEL, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private Path caminho(ImportBatch batch) {
        return props.baseDir().resolve(batch.rawFilePath());
    }

    private static String extensao(ImportBatch batch) {
        String caminho = batch.rawFilePath();
        return caminho.substring(caminho.lastIndexOf('.') + 1);
    }
}
