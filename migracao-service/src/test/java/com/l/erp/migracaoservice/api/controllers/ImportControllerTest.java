package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.exception.dto.StandardError;
import com.l.erp.common.exception.handlers.GlobalExceptionHandler;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ErroLinha;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.RelatorioErros;
import com.l.erp.migracaoservice.services.imports.ImportService;
import com.l.erp.migracaoservice.services.imports.ImportTemplateService;
import com.l.erp.migracaoservice.services.imports.ImportTemplateService.Modelo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone: status HTTP, headers do gateway, multipart e tradução de erro. A autorização (@PreAuthorize) é do gateway/IT. */
@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @Mock ImportService service;
    @Mock ImportTemplateService modelos;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ImportController(service, modelos))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static ImportBatchResponse resposta(String status) {
        return new ImportBatchResponse(ID, "CUSTOMER", status, "TEMPLATE", "clientes.csv", 2, 0, 0, 0, 0, null,
                List.of("obs"), OffsetDateTime.now(), OffsetDateTime.now());
    }

    private static MockMultipartFile arquivo() {
        return new MockMultipartFile("arquivo", "clientes.csv", "text/csv", "tipo;nome_razao;documento\n".getBytes());
    }

    @Test
    void uploadDevolve202ComOEstadoEAsColunasIgnoradas() throws Exception {
        when(service.enviar(eq(7L), eq(USER), eq("CUSTOMER"), any())).thenReturn(resposta("UPLOADED"));

        mvc.perform(multipart("/migracao/imports").file(arquivo()).param("entidade", "CUSTOMER")
                        .header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.colunasIgnoradas[0]").value("obs"));
    }

    @Test
    void uploadSemOArquivoE400ENaoChamaOServico() throws Exception {
        mvc.perform(multipart("/migracao/imports").param("entidade", "CUSTOMER")
                        .header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Requisição incompleta. Confira se todos os campos e o arquivo foram enviados."));

        verifyNoInteractions(service);
    }

    @Test
    void uploadSemHeadersDoGatewayE401() throws Exception {
        mvc.perform(multipart("/migracao/imports").file(arquivo()).param("entidade", "CUSTOMER"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void erroDeNegocioDoServicoViraStatusEMensagemCertos() throws Exception {
        when(service.enviar(any(), any(), any(), any()))
                .thenThrow(new BusinessException("Colunas obrigatórias ausentes: documento", HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(multipart("/migracao/imports").file(arquivo()).param("entidade", "CUSTOMER")
                        .header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Colunas obrigatórias ausentes: documento"));
    }

    @Test
    void uploadAcimaDoLimiteDoMultipartViraRespostaDe413EmPortugues() {
        ResponseEntity<StandardError> resposta = new GlobalExceptionHandler()
                .handleMaxUpload(new MaxUploadSizeExceededException(50L * 1024 * 1024), new MockHttpServletRequest("POST", "/migracao/imports"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(413);
        assertThat(resposta.getBody().getMessage()).isEqualTo("O arquivo enviado excede o tamanho máximo permitido.");
        assertThat(resposta.getBody().getPath()).isEqualTo("/migracao/imports");
    }

    @Test
    void modeloBaixaComNomeCertoESemCache() throws Exception {
        when(modelos.gerar("CUSTOMER", "csv")).thenReturn(new Modelo("modelo-clientes.csv", "text/csv; charset=UTF-8", "a;b\r\n".getBytes()));

        mvc.perform(get("/migracao/imports/modelo/CUSTOMER").param("formato", "csv")
                        .header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("modelo-clientes.csv")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes("a;b\r\n".getBytes()));
    }

    @Test
    void modeloSemFormatoUsaXlsx() throws Exception {
        when(modelos.gerar("PRODUCT", "xlsx")).thenReturn(new Modelo("modelo-produtos.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1}));

        mvc.perform(get("/migracao/imports/modelo/PRODUCT").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml.sheet")));
    }

    @Test
    void listarDetalharEErrosUsamOTenantDoHeader() throws Exception {
        when(service.listar(7L, 0, 20)).thenReturn(new Pagina<>(List.of(resposta("READY")), 1, 0, 20));
        when(service.detalhar(7L, ID)).thenReturn(resposta("READY"));
        when(service.erros(7L, ID, 0, 100)).thenReturn(new RelatorioErros(ID, 2, 1, 1,
                List.of(new ErroLinha(47, "documento", "CPF ou CNPJ inválido", "123")), 0, 100));

        mvc.perform(get("/migracao/imports").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].status").value("READY"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/migracao/imports/" + ID).header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidade").value("CUSTOMER"));
        mvc.perform(get("/migracao/imports/" + ID + "/erros").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhasComErro").value(1))
                .andExpect(jsonPath("$.erros[0].linha").value(47))
                .andExpect(jsonPath("$.erros[0].valorInformado").value("123"));
    }

    @Test
    void promoverDevolve202ComOEstadoEUsaOTenantEOUsuarioDoHeader() throws Exception {
        when(service.promover(7L, USER, ID)).thenReturn(resposta("PROMOTING"));

        mvc.perform(post("/migracao/imports/" + ID + "/promover")
                        .header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROMOTING"))
                .andExpect(jsonPath("$.linhasPromovidas").value(0));
    }

    @Test
    void promoverBatchQueNaoEstaProntoE409ComMensagemEmPortugues() throws Exception {
        when(service.promover(any(), any(), any()))
                .thenThrow(new BusinessException(Constants.IMPORT_PROMOCAO_INDISPONIVEL, HttpStatus.CONFLICT));

        mvc.perform(post("/migracao/imports/" + ID + "/promover")
                        .header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Constants.IMPORT_PROMOCAO_INDISPONIVEL));
    }

    @Test
    void promoverSemHeadersDoGatewayE401() throws Exception {
        mvc.perform(post("/migracao/imports/" + ID + "/promover")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void idMalFormadoE400() throws Exception {
        mvc.perform(get("/migracao/imports/nao-e-uuid").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isBadRequest());
    }
}
