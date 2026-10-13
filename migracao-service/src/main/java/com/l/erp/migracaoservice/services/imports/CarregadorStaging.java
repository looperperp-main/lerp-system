package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaNova;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consome as linhas do leitor: a primeira não vazia é o cabeçalho (Trilha 1: as colunas têm de bater
 * com o template), as demais viram raw_json com as chaves de canonical_schema_field, gravadas em lotes.
 * Roda dentro de UMA transação aberta pelo chamador: qualquer erro desfaz o staging inteiro do batch.
 */
class CarregadorStaging implements LeitorTabular.ConsumidorLinha {

    private final List<CampoCanonico> campos;
    private final ImportRowRepository linhas;
    private final ObjectMapper mapper;
    private final Long tenantId;
    private final UUID batchId;
    private final String entidade;
    private final String trilha;

    private Map<String, Integer> colunaDoCampo;
    private final List<String> ignoradas = new ArrayList<>();
    private final List<LinhaNova> lote = new ArrayList<>();
    private int total;

    /** Campo canônico → nome da coluna no arquivo (Trilha 2); null na Trilha 1, onde a coluna tem o nome do campo. */
    private final Map<String, String> mapeamento;

    CarregadorStaging(List<CampoCanonico> campos, ImportRowRepository linhas, ObjectMapper mapper,
                      Long tenantId, UUID batchId, String entidade, String trilha) {
        this(campos, linhas, mapper, tenantId, batchId, entidade, trilha, null);
    }

    CarregadorStaging(List<CampoCanonico> campos, ImportRowRepository linhas, ObjectMapper mapper,
                      Long tenantId, UUID batchId, String entidade, String trilha, Map<String, String> mapeamento) {
        this.mapeamento = mapeamento;
        this.campos = campos;
        this.linhas = linhas;
        this.mapper = mapper;
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.entidade = entidade;
        this.trilha = trilha;
    }

    @Override
    public void aceitar(int numeroLinha, List<String> celulas) {
        if (celulas.stream().allMatch(c -> c == null || c.isBlank())) return;
        if (colunaDoCampo == null) {
            resolverCabecalho(celulas);
            return;
        }
        if (++total > Constants.IMPORT_MAX_LINHAS) {
            throw new BusinessException(Constants.IMPORT_LIMITE_LINHAS, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        lote.add(new LinhaNova(numeroLinha, mapper.writeValueAsString(montar(celulas))));
        if (lote.size() >= Constants.IMPORT_LOTE) descarregar();
    }

    /** Grava o que sobrou e devolve o total de linhas de dados. Arquivo sem linhas de dados é erro. */
    int concluir() {
        if (colunaDoCampo == null || total == 0) {
            throw new BusinessException(Constants.IMPORT_ARQUIVO_SEM_DADOS, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        descarregar();
        return total;
    }

    /** Colunas do arquivo que não são campos do modelo e foram deixadas de fora (o cliente é avisado). */
    List<String> colunasIgnoradas() {
        return ignoradas;
    }

    private void resolverCabecalho(List<String> cabecalho) {
        Map<String, Integer> porNome = new LinkedHashMap<>();
        for (int i = 0; i < cabecalho.size(); i++) {
            String nome = normalizarCabecalho(cabecalho.get(i));
            if (!nome.isEmpty()) porNome.putIfAbsent(nome, i);
        }
        // Nome (normalizado) da coluna que alimenta cada campo: o próprio campo (Trilha 1) ou o que o cliente mapeou (Trilha 2).
        Map<String, String> colunaProcurada = new LinkedHashMap<>();
        for (CampoCanonico campo : campos) {
            colunaProcurada.put(campo.nome(), mapeamento == null ? campo.nome() : normalizarCabecalho(mapeamento.get(campo.nome())));
        }
        Set<String> conhecidos = Set.copyOf(colunaProcurada.values());
        colunaDoCampo = new LinkedHashMap<>();
        for (CampoCanonico campo : campos) {
            Integer indice = porNome.get(colunaProcurada.get(campo.nome()));
            if (indice != null) colunaDoCampo.put(campo.nome(), indice);
        }
        List<String> faltando = campos.stream()
                .filter(c -> c.obrigatorio() && !colunaDoCampo.containsKey(c.nome()))
                .map(CampoCanonico::nome).toList();
        if (!faltando.isEmpty()) {
            // Sem mapeamento é a queda automática na Trilha 2; com mapeamento o chamador já o validou, então é falha de verdade.
            if (mapeamento == null) throw new ColunasForaDoModeloException(faltando);
            throw new BusinessException(String.format(Constants.IMPORT_COLUNAS_AUSENTES, String.join(", ", faltando)),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        porNome.forEach((nome, indice) -> {
            if (!conhecidos.contains(nome)) ignoradas.add(cabecalho.get(indice).strip());
        });
    }

    /** Todos os campos do modelo entram no JSON (null quando a coluna não veio ou a célula está vazia). */
    private Map<String, String> montar(List<String> celulas) {
        Map<String, String> linha = new LinkedHashMap<>();
        for (CampoCanonico campo : campos) {
            Integer indice = colunaDoCampo.get(campo.nome());
            String valor = indice != null && indice < celulas.size() ? celulas.get(indice) : null;
            linha.put(campo.nome(), valor == null || valor.isBlank() ? null : valor.strip());
        }
        return linha;
    }

    private void descarregar() {
        if (lote.isEmpty()) return;
        linhas.inserirLote(tenantId, batchId, entidade, trilha, new ArrayList<>(lote));
        lote.clear();
    }

    /** "Nome Razão" → "nome_razao": sem BOM, acento, caixa ou espaço, para tolerar o cabeçalho digitado à mão. */
    static String normalizarCabecalho(String cabecalho) {
        if (cabecalho == null) return "";
        String semAcento = Normalizer.normalize(cabecalho.replace("﻿", ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }
}
