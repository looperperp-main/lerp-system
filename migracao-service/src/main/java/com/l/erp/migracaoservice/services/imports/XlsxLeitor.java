package com.l.erp.migracaoservice.services.imports;

import com.l.erp.migracaoservice.services.export.XlsxRowSink;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * XLSX em streaming (SAX, sem montar o workbook: um arquivo de 50 MB não cabe num heap de 256 MB
 * como XSSFWorkbook). Lê a aba "dados"; sem ela, a primeira aba que não seja técnica ("_syax_meta"
 * do export do próprio SYAX — spec migracao §6). Data vira AAAA-MM-DD e número sai sem notação científica.
 */
public class XlsxLeitor implements LeitorTabular {

    @Override
    public void ler(Path arquivo, ConsumidorLinha consumidor) throws IOException {
        try (OPCPackage pacote = OPCPackage.open(arquivo.toFile(), PackageAccess.READ)) {
            XSSFReader leitor = new XSSFReader(pacote);
            String alvo = escolherAba(leitor);
            ReadOnlySharedStringsTable textos = new ReadOnlySharedStringsTable(pacote);
            StylesTable estilos = leitor.getStylesTable();

            XSSFReader.SheetIterator abas = (XSSFReader.SheetIterator) leitor.getSheetsData();
            while (abas.hasNext()) {
                try (InputStream aba = abas.next()) {
                    if (!abas.getSheetName().equals(alvo)) continue;
                    XMLReader xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(new XSSFSheetXMLHandler(
                            estilos, textos, new Coletor(consumidor), new FormatadorIso(), false));
                    xml.parse(new InputSource(aba));
                    return;
                }
            }
        } catch (OpenXML4JException | SAXException | ParserConfigurationException
                 | OpenXML4JRuntimeException | POIXMLException | IllegalArgumentException e) {
            // O POI lança exceção não verificada em arquivo corrompido/não-OOXML; tudo vira "ilegível" (422).
            // Não pega BusinessException (limite de linhas), que precisa passar intacta.
            throw new IOException("Arquivo XLSX ilegível", e);
        }
    }

    private static String escolherAba(XSSFReader leitor) throws IOException, OpenXML4JException {
        List<String> nomes = new ArrayList<>();
        XSSFReader.SheetIterator abas = (XSSFReader.SheetIterator) leitor.getSheetsData();
        while (abas.hasNext()) {
            try (InputStream ignorada = abas.next()) {
                nomes.add(abas.getSheetName());
            }
        }
        return nomes.stream().filter(XlsxRowSink.ABA_DADOS::equalsIgnoreCase).findFirst()
                .or(() -> nomes.stream().filter(n -> !n.startsWith("_")).findFirst())
                .orElseThrow(() -> new IOException("Arquivo XLSX sem abas"));
    }

    /** Monta a lista de células de cada linha, preenchendo com vazio as células ausentes do meio. */
    private static final class Coletor implements SheetContentsHandler {
        private final ConsumidorLinha consumidor;
        private List<String> celulas = new ArrayList<>();
        private int linha;

        Coletor(ConsumidorLinha consumidor) {
            this.consumidor = consumidor;
        }

        @Override
        public void startRow(int rowNum) {
            celulas = new ArrayList<>();
            linha = rowNum + 1;                 // o SAX é base 0; a planilha mostra base 1
        }

        @Override
        public void endRow(int rowNum) {
            consumidor.aceitar(linha, celulas);
        }

        @Override
        public void cell(String referencia, String valor, XSSFComment comentario) {
            int coluna = referencia == null ? celulas.size() : new CellReference(referencia).getCol();
            while (celulas.size() <= coluna) celulas.add("");
            celulas.set(coluna, valor == null ? "" : valor);
        }
    }

    /** Independe de locale: data em ISO e número em notação simples (o Excel guardaria o CPF como 1,23457E+10). */
    private static final class FormatadorIso extends DataFormatter {
        @Override
        public String formatRawCellContents(double valor, int indiceFormato, String formato, boolean base1904) {
            if (DateUtil.isADateFormat(indiceFormato, formato)) {
                return DateUtil.getLocalDateTime(valor, base1904).toLocalDate().toString();
            }
            return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
        }
    }
}
