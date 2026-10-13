package com.l.erp.migracaoservice.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    /** Campo do modelo que o cliente pode associar a uma coluna do arquivo (vem de canonical_schema_field). */
    public record CampoAlvo(String campo, String rotulo, String tipo, boolean obrigatorio) {}

    /**
     * Tela de mapeamento da Trilha 2 (spec §7).
     *
     * @param colunasArquivo cabeçalhos do arquivo enviado, na ordem em que aparecem
     * @param sugestao       campo → coluna: o último mapeamento deste tenant e as colunas com o mesmo nome do campo
     */
    public record MapeamentoResponse(UUID id, String entidade, List<String> colunasArquivo, List<CampoAlvo> campos,
                                     Map<String, String> sugestao) {}

    /** @param mapeamento campo do modelo → nome da coluna do arquivo; campo sem coluna fica de fora */
    public record MapeamentoRequest(Map<String, String> mapeamento) {}

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
