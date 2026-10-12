package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.domain.ErroCampo;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.AsyncConfig;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaStaging;
import com.l.erp.migracaoservice.repository.ImportRowRepository.Resultado;
import com.l.erp.migracaoservice.util.DocumentoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Validação assíncrona do staging (spec migracao §3.2): lê as linhas em lotes, valida cada uma
 * contra canonical_schema_field, aponta chave natural repetida no arquivo e fecha o batch em
 * READY (tudo válido) ou AWAITING_FIX (há erros). Falha inesperada vira FAILED com mensagem — o
 * @Async engole exceção, então o try/catch aqui é o que impede o batch de ficar preso em VALIDATING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportValidationService {

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final CanonicalSchemaRepository schema;
    private final ValidadorLinha validador;
    private final ObjectMapper mapper;

    @Async(AsyncConfig.IMPORT_EXECUTOR)
    public void validar(UUID batchId, Long tenantId) {
        // UPDATE condicional por status: a única guarda contra duas threads no mesmo batch.
        if (!batches.iniciarValidacao(batchId, tenantId)) {
            log.info("[import {}] validação ignorada: batch já em andamento ou fora de estado", batchId);
            return;
        }
        try {
            executar(batchId, tenantId);
        } catch (Exception e) {
            log.error("[import {}] falha na validação", batchId, e);
            batches.falhar(batchId, tenantId, Constants.IMPORT_FALHA_VALIDACAO);
        }
    }

    @SuppressWarnings("unchecked")
    private void executar(UUID batchId, Long tenantId) {
        ImportBatch batch = batches.buscar(batchId, tenantId).orElseThrow();
        List<CampoCanonico> campos = schema.ativos(batch.entityType(), batch.schemaVersion());
        String campoChave = Constants.IMPORT_CHAVE_NATURAL.get(batch.entityType());
        Map<String, Integer> vistos = new HashMap<>();   // ponytail: 50.000 chaves em memória; cabe no teto de linhas
        boolean temErro = false;
        long depoisDoId = 0;

        List<LinhaStaging> lote;
        while (!(lote = rows.proximas(batchId, tenantId, depoisDoId, Constants.IMPORT_LOTE)).isEmpty()) {
            List<Resultado> resultados = new ArrayList<>(lote.size());
            for (LinhaStaging staging : lote) {
                Map<String, String> linha = mapper.readValue(staging.rawJson(), Map.class);
                List<ErroCampo> erros = new ArrayList<>(validador.validar(campos, linha));
                chaveRepetida(batch.entityType(), campoChave, linha, staging.numero(), vistos).ifPresent(erros::add);
                temErro |= !erros.isEmpty();
                resultados.add(erros.isEmpty()
                        ? new Resultado(staging.id(), Constants.IMPORT_LINHA_VALID, null)
                        : new Resultado(staging.id(), Constants.IMPORT_LINHA_ERROR, mapper.writeValueAsString(erros)));
            }
            rows.gravarResultados(tenantId, resultados);
            depoisDoId = lote.getLast().id();
        }
        batches.concluirValidacao(batchId, tenantId,
                temErro ? Constants.IMPORT_STATUS_AWAITING_FIX : Constants.IMPORT_STATUS_READY);
    }

    /** A 2ª ocorrência da chave natural no mesmo arquivo é o erro (a 1ª é a que vale), com a linha da 1ª. */
    private static Optional<ErroCampo> chaveRepetida(String entidade, String campoChave, Map<String, String> linha,
                                                     int numero, Map<String, Integer> vistos) {
        String bruto = linha.get(campoChave);
        if (bruto == null || bruto.isBlank()) return Optional.empty();
        String chave = Constants.MIGRACAO_ENTIDADE_CLIENTE.equals(entidade)
                ? DocumentoValidator.normalizar(bruto) : bruto.strip();
        Integer anterior = vistos.putIfAbsent(chave, numero);
        return anterior == null ? Optional.empty()
                : Optional.of(new ErroCampo(campoChave, String.format(Constants.IMPORT_CHAVE_REPETIDA, anterior)));
    }
}
