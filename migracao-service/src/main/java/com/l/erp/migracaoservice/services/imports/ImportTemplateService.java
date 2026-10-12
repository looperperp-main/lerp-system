package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.services.export.XlsxRowSink;
import com.l.erp.migracaoservice.util.ImportEntidade;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gera o modelo (template) da Trilha 1 a partir de canonical_schema_field — a mesma tabela que o
 * parser e o validador usam, então modelo e validação nunca divergem (spec migracao §4.3).
 * XLSX: aba "dados" (cabeçalho, colunas em texto para o Excel não comer zero à esquerda do CPF/NCM,
 * listas suspensas nos campos de valor fixo) e aba "instrucoes". CSV: cabeçalho com ';' e BOM, que o
 * Excel em português abre direto.
 */
@Service
@RequiredArgsConstructor
public class ImportTemplateService {

    public record Modelo(String nome, String contentType, byte[] conteudo) {}

    private static final Map<String, List<String>> LISTAS = Map.of(
            Constants.IMPORT_REGRA_ENUM_PF_PJ, List.of("PF", "PJ"),
            Constants.IMPORT_REGRA_ENUM_TIPO_PRODUTO, List.of("MERCADORIA", "SERVICO"),
            Constants.IMPORT_REGRA_ENUM_IND_IE_DEST, List.of("CONTRIBUINTE_ICMS", "ISENTO", "NAO_CONTRIBUINTE"));
    private static final List<String> SIM_NAO = List.of("SIM", "NAO");
    private static final int LARGURA_COLUNA = 24 * 256;

    private final CanonicalSchemaRepository schema;

    public Modelo gerar(String entidade, String formato) {
        String e = ImportEntidade.normalizar(entidade);
        String ext = formato == null || formato.isBlank() ? Constants.IMPORT_EXTENSAO_XLSX : formato.strip().toLowerCase(Locale.ROOT);
        if (!ext.equals(Constants.IMPORT_EXTENSAO_XLSX) && !ext.equals(Constants.IMPORT_EXTENSAO_CSV)) {
            throw new BusinessException(Constants.IMPORT_FORMATO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
        List<CampoCanonico> campos = schema.ativos(e, Constants.MIGRACAO_SCHEMA_VERSION_ATUAL);
        if (campos.isEmpty()) throw new BusinessException(Constants.IMPORT_ENTIDADE_INVALIDA, HttpStatus.BAD_REQUEST);

        String nome = "modelo-" + Constants.IMPORT_ROTULO_ARQUIVO.get(e) + "." + ext;
        return ext.equals(Constants.IMPORT_EXTENSAO_XLSX)
                ? new Modelo(nome, Constants.EXPORT_CONTENT_TYPE_XLSX, xlsx(campos))
                : new Modelo(nome, Constants.IMPORT_CONTENT_TYPE_CSV, csv(campos));
    }

    private static byte[] csv(List<CampoCanonico> campos) {
        String cabecalho = String.join(";", campos.stream().map(CampoCanonico::nome).toList());
        return ("﻿" + cabecalho + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xlsx(List<CampoCanonico> campos) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle texto = wb.createCellStyle();
            texto.setDataFormat(wb.createDataFormat().getFormat("@"));
            CellStyle negrito = wb.createCellStyle();
            Font fonte = wb.createFont();
            fonte.setBold(true);
            negrito.setFont(fonte);

            XSSFSheet dados = wb.createSheet(XlsxRowSink.ABA_DADOS);
            DataValidationHelper helper = dados.getDataValidationHelper();
            Row cabecalho = dados.createRow(0);
            for (int i = 0; i < campos.size(); i++) {
                CampoCanonico campo = campos.get(i);
                var celula = cabecalho.createCell(i);
                celula.setCellValue(campo.nome());
                celula.setCellStyle(negrito);
                dados.setDefaultColumnStyle(i, texto);
                dados.setColumnWidth(i, LARGURA_COLUNA);
                List<String> opcoes = opcoes(campo);
                if (opcoes != null) {
                    DataValidation lista = helper.createValidation(
                            helper.createExplicitListConstraint(opcoes.toArray(String[]::new)),
                            new CellRangeAddressList(1, Constants.IMPORT_MAX_LINHAS, i, i));
                    lista.setShowErrorBox(true);
                    dados.addValidationData(lista);
                }
            }
            dados.createFreezePane(0, 1);

            XSSFSheet instrucoes = wb.createSheet(Constants.IMPORT_ABA_INSTRUCOES);
            Row titulo = instrucoes.createRow(0);
            String[] colunas = {"Campo", "Obrigatório", "Como preencher"};
            for (int i = 0; i < colunas.length; i++) {
                titulo.createCell(i).setCellValue(colunas[i]);
                titulo.getCell(i).setCellStyle(negrito);
            }
            for (int i = 0; i < campos.size(); i++) {
                CampoCanonico campo = campos.get(i);
                Row linha = instrucoes.createRow(i + 1);
                linha.createCell(0).setCellValue(campo.nome());
                linha.createCell(1).setCellValue(campo.obrigatorio() ? "Sim" : "Não");
                linha.createCell(2).setCellValue(instrucao(campo));
            }
            instrucoes.setColumnWidth(0, LARGURA_COLUNA);
            instrucoes.setColumnWidth(1, 14 * 256);
            instrucoes.setColumnWidth(2, 90 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Valores fixos do campo (lista suspensa no Excel), ou null quando o campo é livre. */
    private static List<String> opcoes(CampoCanonico campo) {
        if (Constants.IMPORT_TIPO_BOOLEAN.equals(campo.tipo())) return SIM_NAO;
        return campo.regra() == null ? null : LISTAS.get(campo.regra());
    }

    private static String instrucao(CampoCanonico campo) {
        StringBuilder texto = new StringBuilder(campo.ajuda() == null ? "" : campo.ajuda()).append('.');
        if (Constants.IMPORT_TIPO_DATE.equals(campo.tipo())) texto.append(" Use AAAA-MM-DD ou DD/MM/AAAA.");
        List<String> opcoes = opcoes(campo);
        if (opcoes != null) texto.append(" Valores aceitos: ").append(String.join(", ", opcoes)).append('.');
        if (campo.tamanhoMaximo() != null) texto.append(" Até ").append(campo.tamanhoMaximo()).append(" caracteres.");
        return texto.toString();
    }
}
