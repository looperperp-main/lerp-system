package com.l.erp.migracaoservice.services.export;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowSinkTest {

    @TempDir
    Path dir;

    private Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entity_type", "cadastros.cliente");
        m.put("row_count", 2);
        return m;
    }

    @Test
    void csvEscapaVirgulaAspasEQuebraDeLinhaEGravaMetaIrmao() throws Exception {
        Path csv = dir.resolve("cadastros.cliente.csv");
        Path meta = dir.resolve("cadastros.cliente.meta.json");
        try (CsvRowSink sink = new CsvRowSink(csv, meta, JsonMapper.builder().build())) {
            sink.cabecalho(List.of("id", "nome"));
            sink.linha(Arrays.asList(1L, "Silva, \"Zé\""));
            sink.linha(Arrays.asList(2L, null));
            sink.concluir(meta());
        }

        String conteudo = Files.readString(csv, StandardCharsets.UTF_8);
        assertThat(conteudo).isEqualTo("id,nome\r\n1,\"Silva, \"\"Zé\"\"\"\r\n2,\r\n");
        assertThat(Files.readString(meta)).contains("\"entity_type\"").contains("cadastros.cliente");
        // metadata nunca dentro do CSV (quebraria o parser da Trilha 1 do import)
        assertThat(conteudo).doesNotContain("entity_type");
    }

    @Test
    void xlsxGravaDadosEMetaEmAbasSeparadas() throws Exception {
        Path xlsx = dir.resolve("cadastros.cliente.xlsx");
        try (XlsxRowSink sink = new XlsxRowSink(xlsx, "cadastros.cliente")) {
            sink.cabecalho(List.of("id", "nome", "ativo"));
            sink.linha(Arrays.asList(7L, "Ana", true));
            sink.linha(Arrays.asList(8L, null, false));
            sink.concluir(meta());
        }

        try (InputStream in = Files.newInputStream(xlsx); Workbook wb = new XSSFWorkbook(in)) {
            Sheet dados = wb.getSheet(XlsxRowSink.ABA_DADOS);
            assertThat(dados.getLastRowNum()).isEqualTo(2);
            assertThat(dados.getRow(0).getCell(1).getStringCellValue()).isEqualTo("nome");
            assertThat(dados.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(7.0);
            assertThat(dados.getRow(1).getCell(2).getBooleanCellValue()).isTrue();
            assertThat(dados.getRow(2).getCell(1)).isNull();   // null = célula vazia

            Sheet meta = wb.getSheet(XlsxRowSink.ABA_META);
            assertThat(meta.getRow(0).getCell(0).getStringCellValue()).isEqualTo("entity_type");
            assertThat(meta.getRow(0).getCell(1).getStringCellValue()).isEqualTo("cadastros.cliente");
        }
    }

    @Test
    void xlsxFalhaComMensagemQuandoPassaDoLimiteDeLinhas() throws Exception {
        try (XlsxRowSink sink = new XlsxRowSink(dir.resolve("x.xlsx"), "vendas.pedido_item")) {
            sink.cabecalho(List.of("id"));
            // Pula direto ao teto da aba em vez de gravar 1 milhão de linhas no teste.
            var campo = XlsxRowSink.class.getDeclaredField("proximaLinha");
            campo.setAccessible(true);
            campo.setInt(sink, Constants.EXPORT_XLSX_MAX_LINHAS + 1);

            assertThatThrownBy(() -> sink.linha(List.of(1L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("vendas.pedido_item")
                    .hasMessageContaining("CSV");
        }
    }
}
