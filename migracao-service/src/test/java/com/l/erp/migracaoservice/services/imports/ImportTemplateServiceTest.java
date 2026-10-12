package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportTemplateServiceTest {

    private static final List<String> COLUNAS_CLIENTE =
            List.of("tipo", "nome_razao", "documento", "data_nascimento", "ind_ie_dest", "ativo");

    @TempDir Path dir;
    @Mock CanonicalSchemaRepository schema;

    private ImportTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ImportTemplateService(schema);
    }

    @Test
    void xlsxTemAbaDadosComAsColunasDoSchemaEAbaDeInstrucoes() throws IOException {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());

        var modelo = service.gerar("customer", null);

        assertThat(modelo.nome()).isEqualTo("modelo-clientes.xlsx");
        assertThat(modelo.contentType()).isEqualTo(Constants.EXPORT_CONTENT_TYPE_XLSX);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(modelo.conteudo()))) {
            assertThat(wb.getSheetName(0)).isEqualTo("dados");
            assertThat(wb.getSheetName(1)).isEqualTo("instrucoes");

            List<String> cabecalho = new ArrayList<>();
            wb.getSheetAt(0).getRow(0).forEach(c -> cabecalho.add(c.getStringCellValue()));
            assertThat(cabecalho).containsExactlyElementsOf(COLUNAS_CLIENTE);

            // listas suspensas: tipo (PF/PJ), ind_ie_dest e ativo (SIM/NAO)
            assertThat(wb.getSheetAt(0).getDataValidations()).hasSize(3);

            XSSFSheet instrucoes = wb.getSheetAt(1);
            Row documento = instrucoes.getRow(3);
            assertThat(documento.getCell(0).getStringCellValue()).isEqualTo("documento");
            assertThat(documento.getCell(1).getStringCellValue()).isEqualTo("Sim");
            assertThat(documento.getCell(2).getStringCellValue()).contains("CPF ou CNPJ").contains("Até 18 caracteres");
            assertThat(instrucoes.getRow(4).getCell(2).getStringCellValue()).contains("AAAA-MM-DD");   // data_nascimento
        }
    }

    @Test
    void oModeloBaixadoVoltaPeloLeitorDoImportComOCabecalhoCerto() throws IOException {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());
        Path arquivo = dir.resolve("modelo.xlsx");
        Files.write(arquivo, service.gerar("CUSTOMER", "xlsx").conteudo());

        List<List<String>> linhas = new ArrayList<>();
        new XlsxLeitor().ler(arquivo, (n, celulas) -> linhas.add(celulas));

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0)).containsExactlyElementsOf(COLUNAS_CLIENTE);
    }

    @Test
    void csvTemBomEPontoEVirgulaParaOExcelEmPortuguesAbrirDireto() throws IOException {
        when(schema.ativos("PRODUCT", "1.0")).thenReturn(CamposFixture.produto());

        var modelo = service.gerar("PRODUCT", "CSV");

        assertThat(modelo.nome()).isEqualTo("modelo-produtos.csv");
        assertThat(modelo.contentType()).isEqualTo(Constants.IMPORT_CONTENT_TYPE_CSV);
        String texto = new String(modelo.conteudo(), StandardCharsets.UTF_8);
        assertThat(texto).isEqualTo("﻿sku;nome;tipo;unidade;ncm;ean;fator\r\n");

        Path arquivo = dir.resolve("modelo.csv");
        Files.write(arquivo, modelo.conteudo());
        List<List<String>> linhas = new ArrayList<>();
        new CsvLeitor().ler(arquivo, (n, celulas) -> linhas.add(celulas));
        assertThat(linhas.get(0)).containsExactly("sku", "nome", "tipo", "unidade", "ncm", "ean", "fator");   // BOM não vaza
    }

    @Test
    void formatoEEntidadeInvalidosSao400() {
        assertThatThrownBy(() -> service.gerar("CUSTOMER", "pdf"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_FORMATO_INVALIDO);
                });
        assertThatThrownBy(() -> service.gerar("FORNECEDOR", "xlsx"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_ENTIDADE_INVALIDA));
    }

    @Test
    void entidadeSemCamposNoSchemaE400() {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(List.of());

        assertThatThrownBy(() -> service.gerar("CUSTOMER", "xlsx"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
