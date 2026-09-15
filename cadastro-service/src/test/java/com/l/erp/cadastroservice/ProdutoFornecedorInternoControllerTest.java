package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.controllers.ProdutoFornecedorInternoController;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.ProdutoFornecedor;
import com.l.erp.cadastroservice.repository.ProdutoFornecedorRepository;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** spec/p2p-compras.md RN-P2P-04 — alerta de preço fora da faixa chamado pelo operacoes-service. */
@WebMvcTest(controllers = ProdutoFornecedorInternoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProdutoFornecedorInternoControllerTest {

    private static final String BASE_URL = "/api/v1/interno/produto-fornecedor";
    private static final Long TENANT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoFornecedorRepository repository;

    @Test
    void shouldRetornarVinculosDoLote() throws Exception {
        UUID produtoId = UUID.randomUUID();
        UUID fornecedorId = UUID.randomUUID();
        Produto produto = new Produto();
        produto.setId(produtoId);
        ProdutoFornecedor vinculo = new ProdutoFornecedor();
        vinculo.setProduto(produto);
        vinculo.setPrecoCusto(new BigDecimal("42.50"));

        when(repository.buscarPorProdutosEFornecedor(eq(TENANT_ID), eq(fornecedorId), any()))
                .thenReturn(List.of(vinculo));

        mockMvc.perform(get(BASE_URL)
                        .param("produtoIds", produtoId.toString())
                        .param("fornecedorId", fornecedorId.toString())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].produtoId").value(produtoId.toString()))
                .andExpect(jsonPath("$[0].precoCusto").value(42.50));
    }

    @Test
    void shouldRejectSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("produtoIds", UUID.randomUUID().toString())
                        .param("fornecedorId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
