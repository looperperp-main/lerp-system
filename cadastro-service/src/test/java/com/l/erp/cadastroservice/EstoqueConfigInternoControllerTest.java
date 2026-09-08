package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.controllers.EstoqueConfigInternoController;
import com.l.erp.cadastroservice.domain.Deposito;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.ProdutoEstoqueConfig;
import com.l.erp.cadastroservice.repository.ProdutoEstoqueConfigRepository;
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

/** spec/estoque.md §5.1/E6 — badge "abaixo do mínimo" chamado pelo operacoes-service. */
@WebMvcTest(controllers = EstoqueConfigInternoController.class)
@AutoConfigureMockMvc(addFilters = false)
class EstoqueConfigInternoControllerTest {

    private static final String BASE_URL = "/api/v1/interno/estoque-config";
    private static final Long TENANT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoEstoqueConfigRepository repository;

    @Test
    void shouldRetornarConfigsDoLote() throws Exception {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        Produto produto = new Produto();
        produto.setId(produtoId);
        Deposito deposito = new Deposito();
        deposito.setId(depositoId);
        ProdutoEstoqueConfig config = new ProdutoEstoqueConfig();
        config.setProduto(produto);
        config.setDeposito(deposito);
        config.setEstoqueMinimo(new BigDecimal("5.0000"));

        when(repository.buscarPorProdutosEDeposito(eq(TENANT_ID), eq(depositoId), any()))
                .thenReturn(List.of(config));

        mockMvc.perform(get(BASE_URL)
                        .param("produtoIds", produtoId.toString())
                        .param("depositoId", depositoId.toString())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].produtoId").value(produtoId.toString()))
                .andExpect(jsonPath("$[0].estoqueMinimo").value(5.0));
    }

    @Test
    void shouldRejectSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("produtoIds", UUID.randomUUID().toString())
                        .param("depositoId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
