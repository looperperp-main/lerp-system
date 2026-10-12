package com.l.erp.migracaoservice.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs do import agrupados num arquivo só (mesmo molde do ExportDtos). */
public final class ImportDtos {

    private ImportDtos() {}

    /**
     * @param linhasValidas    linhas que passaram na validação, inclusive as já promovidas e as ignoradas
     * @param linhasPromovidas linhas gravadas no cadastro (só depois de confirmar a importação)
     * @param linhasIgnoradas  linhas válidas cujo registro já existia; o cadastro existente foi mantido
     * @param colunasIgnoradas colunas do arquivo que não fazem parte do modelo e foram deixadas de fora;
     *                         só vem na resposta do upload (não é persistido), null nas consultas
     */
    public record ImportBatchResponse(
            UUID id,
            String entidade,
            String status,
            String trilha,
            String arquivo,
            int totalLinhas,
            long linhasValidas,
            long linhasComErro,
            long linhasPromovidas,
            long linhasIgnoradas,
            String mensagemFalha,
            List<String> colunasIgnoradas,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm) {}

    /** Um erro acionável: linha da planilha, campo, o que fazer e o valor que o cliente enviou (spec §9). */
    public record ErroLinha(int linha, String campo, String mensagem, String valorInformado) {}

    /**
     * Página do relatório de erros. A paginação é por LINHA com erro; uma linha pode ter vários erros,
     * então {@code erros.size()} pode passar de {@code tamanho}.
     */
    public record RelatorioErros(
            UUID id,
            int totalLinhas,
            long linhasValidas,
            long linhasComErro,
            List<ErroLinha> erros,
            int pagina,
            int tamanho) {}
}
