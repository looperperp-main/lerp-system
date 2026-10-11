package com.l.erp.migracaoservice.api.dto;

import com.l.erp.common.util.Constants;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs do export agrupados num arquivo só (são records pequenos e sempre usados juntos). */
public final class ExportDtos {

    private ExportDtos() {}

    /** @param tabelas ids do catálogo ("cadastros.cliente"); datas omitidas = todo o histórico */
    public record ExportRequest(
            @NotEmpty(message = Constants.EXPORT_ESCOPO_VAZIO) List<String> tabelas,
            LocalDate dataInicial,
            LocalDate dataFinal,
            String formato,
            String motivo) {}

    public record ExportBatchResponse(
            UUID id,
            String status,
            String formato,
            List<String> tabelas,
            LocalDate dataInicial,
            LocalDate dataFinal,
            String motivo,
            UUID solicitadoPor,
            int tentativas,
            String mensagemFalha,
            Map<String, Long> linhasPorTabela,
            OffsetDateTime criadoEm,
            OffsetDateTime prontoEm,
            OffsetDateTime expiraEm,
            OffsetDateTime baixadoEm,
            /** true quando o dedupe devolveu uma exportação READY já existente com o mesmo escopo. */
            boolean reaproveitado,
            /** Linha do tempo do export_audit_log; só vem no detalhe, null na listagem. */
            List<EventoResponse> eventos) {}

    public record EventoResponse(String acao, UUID ator, OffsetDateTime em) {}

    /** Resposta leve do polling: sem token de download (ele só sai no clique em "Baixar"). */
    public record StatusResponse(UUID id, String status, String mensagemFalha) {}

    public record CatalogoGrupoResponse(String grupo, String rotulo, List<CatalogoTabelaResponse> tabelas) {}

    public record CatalogoTabelaResponse(String id, String rotulo, boolean filtraPorPeriodo) {}

    public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) {}
}
