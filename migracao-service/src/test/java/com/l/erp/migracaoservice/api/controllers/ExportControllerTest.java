package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.exception.handlers.GlobalExceptionHandler;
import com.l.erp.migracaoservice.api.dto.ExportDtos.CatalogoGrupoResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.CatalogoTabelaResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ExportDtos.StatusResponse;
import com.l.erp.migracaoservice.services.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone: valida status HTTP, leitura dos headers do gateway e tradução de erro. A autorização (@PreAuthorize) é do gateway/IT. */
@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock ExportService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ExportController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private ExportBatchResponse resposta(String status, boolean reaproveitado) {
        return new ExportBatchResponse(UUID.randomUUID(), status, "XLSX", List.of("cadastros.cliente"), null, null,
                null, USER, 0, null, null, null, null, null, null, reaproveitado, null);
    }

    @Test
    void postNovo_devolve202() throws Exception {
        when(service.solicitar(eq(7L), eq(USER), any())).thenReturn(resposta("REQUESTED", false));

        mvc.perform(post("/migracao/exports").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tabelas\":[\"cadastros.cliente\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void postComDedupe_devolve200() throws Exception {
        when(service.solicitar(eq(7L), eq(USER), any())).thenReturn(resposta("READY", true));

        mvc.perform(post("/migracao/exports").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tabelas\":[\"cadastros.cliente\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reaproveitado").value(true));
    }

    @Test
    void postSemTabelas_devolve400() throws Exception {
        mvc.perform(post("/migracao/exports").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tabelas\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semHeadersDoGateway_devolve401() throws Exception {
        mvc.perform(post("/migracao/exports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tabelas\":[\"cadastros.cliente\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInexistente_devolve404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.detalhar(7L, id)).thenThrow(new BusinessException("Exportação não encontrada", HttpStatus.NOT_FOUND));

        mvc.perform(get("/migracao/exports/" + id).header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listaStatusECatalogo() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listar(7L, 0, 20)).thenReturn(new Pagina<>(List.of(), 0, 0, 20));
        when(service.status(7L, id)).thenReturn(new StatusResponse(id, "READY", null));
        when(service.catalogo()).thenReturn(List.of(new CatalogoGrupoResponse("vendas", "Vendas",
                List.of(new CatalogoTabelaResponse("vendas.pedido", "Pedido", true)))));

        mvc.perform(get("/migracao/exports").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tamanho").value(20));
        mvc.perform(get("/migracao/exports/" + id + "/status").header("X-Tenant-Id", "7").header("X-User-Id", USER.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        mvc.perform(get("/migracao/exports/catalogo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tabelas[0].id").value("vendas.pedido"));
    }
}
