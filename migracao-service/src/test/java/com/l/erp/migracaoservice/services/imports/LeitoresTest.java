package com.l.erp.migracaoservice.services.imports;

import com.l.erp.migracaoservice.services.export.CsvRowSink;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitoresTest {

    @TempDir Path dir;

    private Map<Integer, List<String>> ler(LeitorTabular leitor, Path arquivo) throws IOException {
        Map<Integer, List<String>> linhas = new LinkedHashMap<>();
        leitor.ler(arquivo, (n, celulas) -> linhas.put(n, new ArrayList<>(celulas)));
        return linhas;
    }

    private Path csv(String conteudo) throws IOException {
        Path arquivo = dir.resolve("a.csv");
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        return arquivo;
    }

    // ───────────────────── Cabeçalho (Trilha 2) ─────────────────────

    @Test
    void leitorDeCabecalhoParaNaPrimeiraLinhaNaoVaziaNoCsvENoXlsx() throws IOException {
        assertThat(LeitorCabecalho.ler(new CsvLeitor(), csv("Cliente;CPF\nMaria;1\nJoao;2\n"))).containsExactly("Cliente", "CPF");
        assertThat(LeitorCabecalho.ler(new CsvLeitor(), csv(""))).isEmpty();

        Path xlsx = dir.resolve("a.xlsx");
        try (XSSFWorkbook pasta = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(xlsx)) {
            XSSFSheet aba = pasta.createSheet("dados");
            Row cabecalho = aba.createRow(0);
            cabecalho.createCell(0).setCellValue("Cliente");
            cabecalho.createCell(1).setCellValue("CPF");
            aba.createRow(1).createCell(0).setCellValue("Maria");
            pasta.write(out);
        }
        assertThat(LeitorCabecalho.ler(new XlsxLeitor(), xlsx)).containsExactly("Cliente", "CPF");
    }

    @Test
    void leitorPorExtensaoEscolheOFormato() {
        assertThat(LeitorTabular.para("xlsx")).isInstanceOf(XlsxLeitor.class);
        assertThat(LeitorTabular.para("csv")).isInstanceOf(CsvLeitor.class);
    }

    // ───────────────────────── CSV ─────────────────────────

    @Test
    void csvComVirgulaELinhaFinalSemQuebra() throws IOException {
        var linhas = ler(new CsvLeitor(), csv("a,b,c\r\n1,2,3\r\n4,5,6"));

        assertThat(linhas).containsOnlyKeys(1, 2, 3);
        assertThat(linhas.get(1)).containsExactly("a", "b", "c");
        assertThat(linhas.get(3)).containsExactly("4", "5", "6");
    }

    @Test
    void csvDoExcelBrasileiroUsaPontoEVirgulaEBom() throws IOException {
        var linhas = ler(new CsvLeitor(), csv("﻿nome;documento\n"
                + "Maria da Silva;52998224725\n"));

        assertThat(linhas.get(1)).containsExactly("nome", "documento");     // BOM não gruda no 1º cabeçalho
        assertThat(linhas.get(2)).containsExactly("Maria da Silva", "52998224725");
    }

    @Test
    void csvComAspasProtegeSeparadorAspasEQuebraDeLinha() throws IOException {
        var linhas = ler(new CsvLeitor(), csv("a,b\n\"x, y\",\"diz \"\"oi\"\"\"\n\"linha1\nlinha2\",ok\n"));

        assertThat(linhas.get(2)).containsExactly("x, y", "diz \"oi\"");
        assertThat(linhas.get(3)).containsExactly("linha1\nlinha2", "ok");
    }

    @Test
    void csvEntregaLinhaVaziaMasMantemANumeracao() throws IOException {
        var linhas = ler(new CsvLeitor(), csv("a,b\n\n1,2\n"));

        assertThat(linhas.get(2)).containsExactly("");
        assertThat(linhas.get(3)).containsExactly("1", "2");
    }

    @Test
    void csvDoExportDoSyaxVoltaIgualNoImport() throws IOException {
        Path arquivo = dir.resolve("export.csv");
        try (CsvRowSink sink = new CsvRowSink(arquivo, dir.resolve("export.meta.json"), JsonMapper.builder().build())) {
            sink.cabecalho(List.of("sku", "nome"));
            sink.linha(List.of("P-1", "Cabo \"HDMI\", 2m"));
            sink.concluir(Map.of());
        }

        var linhas = ler(new CsvLeitor(), arquivo);

        assertThat(linhas.get(2)).containsExactly("P-1", "Cabo \"HDMI\", 2m");
    }

    // ───────────────────────── XLSX ─────────────────────────

    private Path xlsx(boolean abaDadosPrimeiro) throws IOException {
        Path arquivo = dir.resolve("a.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(arquivo)) {
            if (!abaDadosPrimeiro) wb.createSheet("_syax_meta").createRow(0).createCell(0).setCellValue("lixo");
            XSSFSheet dados = wb.createSheet("dados");
            Row cab = dados.createRow(0);
            cab.createCell(0).setCellValue("nome");
            cab.createCell(1).setCellValue("documento");
            cab.createCell(2).setCellValue("nascimento");
            Row linha = dados.createRow(1);
            linha.createCell(0).setCellValue("Maria");
            linha.createCell(1).setCellValue(52998224725.0);                 // CPF digitado como número
            CellStyle data = wb.createCellStyle();
            data.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
            var celulaData = linha.createCell(2);
            celulaData.setCellValue(LocalDate.of(1990, 1, 15));
            celulaData.setCellStyle(data);
            Row pulada = dados.createRow(3);                                   // linha 3 não existe: numeração real
            pulada.createCell(0).setCellValue("Joao");
            pulada.createCell(2).setCellValue("01/02/2000");                   // célula 1 vazia no meio
            wb.write(out);
        }
        return arquivo;
    }

    @Test
    void xlsxLeNumeroSemNotacaoCientificaEDataEmIso() throws IOException {
        var linhas = ler(new XlsxLeitor(), xlsx(true));

        assertThat(linhas.get(1)).containsExactly("nome", "documento", "nascimento");
        assertThat(linhas.get(2)).containsExactly("Maria", "52998224725", "1990-01-15");
    }

    @Test
    void xlsxPreencheCelulaVaziaDoMeioENumeraComoAPlanilha() throws IOException {
        var linhas = ler(new XlsxLeitor(), xlsx(true));

        assertThat(linhas).containsOnlyKeys(1, 2, 4);                          // linha 3 não existe no arquivo
        assertThat(linhas.get(4)).containsExactly("Joao", "", "01/02/2000");
    }

    @Test
    void xlsxIgnoraAbaTecnicaDoExportDoSyax() throws IOException {
        var linhas = ler(new XlsxLeitor(), xlsx(false));                       // _syax_meta vem ANTES de "dados"

        assertThat(linhas.get(1)).containsExactly("nome", "documento", "nascimento");
    }

    @Test
    void xlsxSemAbaDadosUsaAPrimeiraAbaNaoTecnica() throws IOException {
        Path arquivo = dir.resolve("outra.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(arquivo)) {
            wb.createSheet("_syax_meta").createRow(0).createCell(0).setCellValue("lixo");
            wb.createSheet("Planilha1").createRow(0).createCell(0).setCellValue("sku");
            wb.write(out);
        }

        assertThat(ler(new XlsxLeitor(), arquivo).get(1)).containsExactly("sku");
    }

    @Test
    void arquivoQueNaoEXlsxViraIoExceptionEntendivel() throws IOException {
        Path falso = dir.resolve("falso.xlsx");
        Files.writeString(falso, "isso é um csv renomeado");

        assertThatThrownBy(() -> ler(new XlsxLeitor(), falso)).isInstanceOf(IOException.class);
    }
}
