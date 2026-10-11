package com.l.erp.migracaoservice.services.export;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * XLSX em streaming (SXSSF: só 100 linhas em memória). Dados na aba "dados"; metadata em aba
 * separada "_syax_meta" (spec export §8). Teto: 1.048.575 linhas por aba — acima disso falha com
 * mensagem pedindo CSV (ponytail: sem partir em várias abas; o caminho é o CSV).
 */
public class XlsxRowSink implements RowSink {

    public static final String ABA_DADOS = "dados";
    public static final String ABA_META = "_syax_meta";

    private final Path arquivo;
    private final String nome;
    private final SXSSFWorkbook wb = new SXSSFWorkbook(100);
    private final Sheet dados = wb.createSheet(ABA_DADOS);
    private int proximaLinha = 0;

    public XlsxRowSink(Path arquivo, String nome) {
        this.arquivo = arquivo;
        this.nome = nome;
    }

    @Override
    public void cabecalho(List<String> colunas) {
        Row r = dados.createRow(proximaLinha++);
        for (int i = 0; i < colunas.size(); i++) r.createCell(i).setCellValue(colunas.get(i));
    }

    @Override
    public void linha(List<Object> valores) {
        if (proximaLinha > Constants.EXPORT_XLSX_MAX_LINHAS) {
            throw new BusinessException(String.format(Constants.EXPORT_XLSX_LIMITE_EXCEDIDO, nome), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Row r = dados.createRow(proximaLinha++);
        for (int i = 0; i < valores.size(); i++) {
            Object v = valores.get(i);
            if (v == null) continue;
            Cell c = r.createCell(i);
            switch (v) {
                case Boolean b -> c.setCellValue(b);
                case BigDecimal bd -> c.setCellValue(bd.doubleValue());
                case Number n -> c.setCellValue(n.doubleValue());
                default -> c.setCellValue(v.toString());
            }
        }
    }

    @Override
    public void concluir(Map<String, Object> metadata) throws IOException {
        Sheet meta = wb.createSheet(ABA_META);
        int i = 0;
        for (Map.Entry<String, Object> e : metadata.entrySet()) {
            Row r = meta.createRow(i++);
            r.createCell(0).setCellValue(e.getKey());
            r.createCell(1).setCellValue(String.valueOf(e.getValue()));
        }
        try (OutputStream os = Files.newOutputStream(arquivo)) {
            wb.write(os);
        }
    }

    @Override
    public void close() throws IOException {
        wb.dispose();   // apaga os temporários do SXSSF mesmo se falhou antes do concluir()
        wb.close();
    }
}
