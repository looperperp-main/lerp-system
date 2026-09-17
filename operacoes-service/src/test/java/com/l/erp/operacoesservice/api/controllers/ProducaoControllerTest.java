package com.l.erp.operacoesservice.api.controllers;

import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.FichaTecnicaResponseDTO;
import com.l.erp.operacoesservice.api.dto.OrdemProducaoResponseDTO;
import com.l.erp.operacoesservice.api.mappers.ProducaoMapper;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnica;
import com.l.erp.operacoesservice.domain.estoque.OrdemProducao;
import com.l.erp.operacoesservice.domain.estoque.enumerators.StatusOrdemProducao;
import com.l.erp.operacoesservice.services.estoque.ProducaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test de ProducaoController (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
@WebMvcTest(controllers = ProducaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProducaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProducaoService service;
    @MockitoBean
    private ProducaoMapper mapper;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @WithMockUser(authorities = "PRODUCAO_GERENCIAR")
    void criarFichaTecnicaDeveRetornar201() throws Exception {
        FichaTecnica ficha = FichaTecnica.builder().id(UUID.randomUUID()).produtoAcabadoId(UUID.randomUUID()).ativo(true).build();
        when(service.criarFichaTecnica(any(), any(), any(), any())).thenReturn(ficha);
        when(service.buscarItens(any())).thenReturn(List.of());
        when(mapper.toFichaTecnicaResponseDto(any())).thenReturn(new FichaTecnicaResponseDTO());

        String payload = """
                {"produtoAcabadoId":"%s","itens":[{"produtoComponenteId":"%s","quantidade":2}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/producao/fichas-tecnicas")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "PRODUCAO_GERENCIAR")
    void criarFichaTecnicaSemItensDeveRetornar400() throws Exception {
        String payload = """
                {"produtoAcabadoId":"%s","itens":[]}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/producao/fichas-tecnicas")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PRODUCAO_VISUALIZAR")
    void fichasTecnicasSemAutoridadeGerenciarDeveRetornar403NoPost() throws Exception {
        String payload = """
                {"produtoAcabadoId":"%s","itens":[{"produtoComponenteId":"%s","quantidade":2}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/producao/fichas-tecnicas")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PRODUCAO_VISUALIZAR")
    void fichasTecnicasDeveRetornar200() throws Exception {
        Page<FichaTecnica> page = new PageImpl<>(List.of());
        when(service.buscarFichasTecnicas(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/producao/fichas-tecnicas")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRODUCAO_GERENCIAR")
    void criarOrdemDeveRetornar201() throws Exception {
        OrdemProducao ordem = OrdemProducao.builder().id(UUID.randomUUID()).status(StatusOrdemProducao.ABERTA).build();
        when(service.criarOrdemProducao(any(), any(), any(), any(), any())).thenReturn(ordem);
        when(mapper.toOrdemResponseDto(any())).thenReturn(new OrdemProducaoResponseDTO());

        String payload = """
                {"produtoAcabadoId":"%s","quantidadePlanejada":10,"depositoId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/producao/ordens")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "PRODUCAO_VISUALIZAR")
    void ordensDeveRetornar200() throws Exception {
        Page<OrdemProducao> page = new PageImpl<>(List.of());
        when(service.buscarOrdens(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/producao/ordens")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRODUCAO_GERENCIAR")
    void apontarDeveRetornar204() throws Exception {
        mockMvc.perform(post("/api/v1/producao/ordens/{id}/apontar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadeProduzida\":5}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "PRODUCAO_VISUALIZAR")
    void apontarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/producao/ordens/{id}/apontar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadeProduzida\":5}"))
                .andExpect(status().isForbidden());
    }
}
