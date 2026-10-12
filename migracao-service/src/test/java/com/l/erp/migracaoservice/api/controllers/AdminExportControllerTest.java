package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.exception.handlers.GlobalExceptionHandler;
import com.l.erp.migracaoservice.api.dto.ExportDtos.AdminExportResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.services.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone: status HTTP, repasse dos filtros e ausência de token/caminho no corpo. O @PreAuthorize (APP_OWNER) fica p/ teste de integração. */
@ExtendWith(MockitoExtension.class)
class AdminExportControllerTest {

    @Mock ExportService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminExportController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private AdminExportResponse item(UUID id) {
        return new AdminExportResponse(7L, new ExportBatchResponse(id, "FAILED", "XLSX", List.of("cadastros.cliente"),
                null, null, null, UUID.randomUUID(), 3, "Não foi possível gerar a exportação", null,
                null, null, null, null, false, null));
    }

    @Test
    void listaRepassaOsFiltrosEDevolveSoMetadados() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listarAdmin(7L, "FAILED", 1, 10)).thenReturn(new Pagina<>(List.of(item(id)), 1, 1, 10));

        mvc.perform(get("/migracao/admin/exports").param("tenantId", "7").param("status", "FAILED")
                        .param("pagina", "1").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].tenantId").value(7))
                .andExpect(jsonPath("$.itens[0].exportacao.status").value("FAILED"))
                .andExpect(jsonPath("$.itens[0].exportacao.mensagemFalha").value("Não foi possível gerar a exportação"))
                .andExpect(jsonPath("$.itens[0].exportacao.downloadToken").doesNotExist())
                .andExpect(jsonPath("$.itens[0].exportacao.arquivos").doesNotExist());
    }

    @Test
    void semParametrosUsaPadroesENaoFiltra() throws Exception {
        when(service.listarAdmin(null, null, 0, 20)).thenReturn(new Pagina<>(List.of(), 0, 0, 20));

        mvc.perform(get("/migracao/admin/exports"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tamanho").value(20));
    }

    @Test
    void detalheInexistenteEh404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.detalharAdmin(id)).thenThrow(new BusinessException("Exportação não encontrada", HttpStatus.NOT_FOUND));

        mvc.perform(get("/migracao/admin/exports/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void statusInvalidoEh400() throws Exception {
        when(service.listarAdmin(null, "XYZ", 0, 20)).thenThrow(new BusinessException("Status inválido"));

        mvc.perform(get("/migracao/admin/exports").param("status", "XYZ")).andExpect(status().isBadRequest());
    }
}
