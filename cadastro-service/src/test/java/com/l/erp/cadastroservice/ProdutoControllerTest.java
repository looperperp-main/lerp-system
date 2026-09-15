package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.ProdutoController;
import com.l.erp.cadastroservice.api.dto.ProdutoDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ProdutoAssembler;
import com.l.erp.cadastroservice.api.mappers.ProdutoPrecoMapper;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.services.ProdutoService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProdutoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProdutoControllerTest {

    private static final String BASE_URL = "/api/v1/produtos";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProdutoService service;

    @MockitoBean
    private ProdutoAssembler assembler;

    @MockitoBean
    private ProdutoPrecoMapper produtoPrecoMapper;

    private ProdutoDTO buildDto() {
        return new ProdutoDTO(
                null, null, null,      // id, tenantId, categoriaId
                "SKU-1", null,         // sku, codigoExterno
                "Produto Teste", null, // nome, descricao
                null,                  // tipo
                "UN", null,            // unidade, unidadeSecundaria
                null, null, null,      // fatorConversao, ncm, codigoServico
                null, null, null, null,// classTrib, ean, cest, origem
                null, null, null, null, null, // pesoBruto, pesoLiquido, altura, largura, comprimento
                true,                  // ativo
                null, null, null, null,// createdAt, updatedAt, createdBy, lastUpdatedBy
                null, null, null);     // precos, fornecedores, estoqueConfigs
    }

    private ProdutoResponseDTO buildResponseDto(UUID id) {
        ProdutoResponseDTO dto = new ProdutoResponseDTO();
        dto.setId(id);
        dto.setNome("Produto Teste");
        dto.add(Link.of("http://localhost" + BASE_URL + "/" + id, "self"));
        return dto;
    }

    @Test
    void shouldListarProdutos() throws Exception {
        when(service.findAll(org.mockito.ArgumentMatchers.eq(TENANT_ID), any())).thenReturn(new PageImpl<>(List.of(new Produto())));
        when(assembler.toModel(any())).thenReturn(buildResponseDto(UUID.randomUUID()));

        mockMvc.perform(get(BASE_URL).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectListarSemTenant() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldBuscarProdutoPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenReturn(new Produto());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Produto Teste"));
    }

    @Test
    void shouldReturn404WhenProdutoNaoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenThrow(new BusinessException(Constants.PRODUTO_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarProduto() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any(), any())).thenReturn(new Produto());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAtualizarStatusProduto() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE_URL + "/{id}/status", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldAtualizarProduto() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any(), any())).thenReturn(new Produto());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Produto Teste"));
    }

    @Test
    void shouldListarPrecosDoProduto() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenReturn(new Produto());

        mockMvc.perform(get(BASE_URL + "/{id}/precos", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldRetornarFornecedoresMock() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get(BASE_URL + "/{id}/fornecedores", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRetornarEstoqueConfigMock() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get(BASE_URL + "/{id}/estoque-config", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk());
    }
}
