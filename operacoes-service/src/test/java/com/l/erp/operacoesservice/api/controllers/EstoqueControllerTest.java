package com.l.erp.operacoesservice.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.EstoqueSaldoResponseDTO;
import com.l.erp.operacoesservice.api.dto.MovimentoEstoqueResponseDTO;
import com.l.erp.operacoesservice.api.mappers.EstoqueMapper;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.services.estoque.EstoqueService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test de EstoqueController (spec/estoque.md §5/§8.3, Fases E5-E6). */
@WebMvcTest(controllers = EstoqueController.class)
@AutoConfigureMockMvc(addFilters = false)
class EstoqueControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EstoqueService service;
    @MockitoBean
    private EstoqueMapper mapper;
    @MockitoBean
    private CadastroServiceClient cadastroServiceClient;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @WithMockUser(authorities = "ESTOQUE_VISUALIZAR")
    void saldosDeveRetornar200ComFiltros() throws Exception {
        UUID produtoId = UUID.randomUUID();
        EstoqueSaldo saldo = EstoqueSaldo.builder().id(UUID.randomUUID()).produtoId(produtoId)
                .depositoId(UUID.randomUUID()).quantidade(BigDecimal.TEN).build();
        Page<EstoqueSaldo> page = new PageImpl<>(List.of(saldo));
        when(service.buscarSaldos(eq(TENANT_ID), eq(produtoId), any(), anyBoolean(), any())).thenReturn(page);
        when(mapper.toSaldoResponseDto(any())).thenReturn(new EstoqueSaldoResponseDTO());
        when(cadastroServiceClient.buscarEstoqueConfig(any(), any(), any(), any())).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/estoque/saldos")
                        .param("produtoId", produtoId.toString())
                        .param("comSaldo", "true")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ESTOQUE_VISUALIZAR")
    void saldosDevePreencherBadgeAbaixoDoMinimo() throws Exception {
        // E6 (spec/estoque.md §5.1) — CadastroServiceClient devolve o mínimo, controller calcula o badge.
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        EstoqueSaldo saldo = EstoqueSaldo.builder().id(UUID.randomUUID()).produtoId(produtoId)
                .depositoId(depositoId).quantidade(BigDecimal.ONE).build();
        Page<EstoqueSaldo> page = new PageImpl<>(List.of(saldo));
        when(service.buscarSaldos(eq(TENANT_ID), any(), any(), anyBoolean(), any())).thenReturn(page);
        when(mapper.toSaldoResponseDto(any())).thenAnswer(inv -> {
            EstoqueSaldoResponseDTO dto = new EstoqueSaldoResponseDTO();
            dto.setProdutoId(produtoId);
            dto.setDepositoId(depositoId);
            dto.setQuantidade(BigDecimal.ONE);
            return dto;
        });
        when(cadastroServiceClient.buscarEstoqueConfig(any(), eq(depositoId), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(Map.of(produtoId, BigDecimal.TEN));

        mockMvc.perform(get("/api/v1/estoque/saldos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.saldos[0].estoqueMinimo").value(10))
                .andExpect(jsonPath("$._embedded.saldos[0].abaixoMinimo").value(true));
    }

    @Test
    @WithMockUser(authorities = "ESTOQUE_AJUSTAR")
    void saldosSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(get("/api/v1/estoque/saldos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ESTOQUE_VISUALIZAR")
    void movimentosDeveRetornar200ComFiltros() throws Exception {
        MovimentoEstoque movimento = MovimentoEstoque.builder().id(UUID.randomUUID())
                .produtoId(UUID.randomUUID()).depositoId(UUID.randomUUID())
                .tipo(TipoMovimentoEstoque.SAIDA_VENDA).origemTipo(OrigemMovimentoEstoque.PEDIDO_VENDA)
                .quantidade(BigDecimal.ONE).build();
        Page<MovimentoEstoque> page = new PageImpl<>(List.of(movimento));
        when(service.buscarMovimentos(eq(TENANT_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);
        when(mapper.toMovimentoResponseDto(any())).thenReturn(new MovimentoEstoqueResponseDTO());

        mockMvc.perform(get("/api/v1/estoque/movimentos")
                        .param("tipo", TipoMovimentoEstoque.SAIDA_VENDA.name())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ESTOQUE_AJUSTAR")
    void ajustarDeveRetornar204() throws Exception {
        String payload = """
                {"produtoId":"%s","depositoId":"%s","quantidadeContada":10,"origem":"AJUSTE","motivo":"contagem"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/estoque/ajustes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "ESTOQUE_AJUSTAR")
    void ajustarComPayloadInvalidoDeveRetornar400() throws Exception {
        // produtoId ausente viola @NotNull do AjusteEstoqueRequestDTO.
        String payload = """
                {"depositoId":"%s","quantidadeContada":10,"origem":"AJUSTE","motivo":"contagem"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/estoque/ajustes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ESTOQUE_VISUALIZAR")
    void ajustarSemAutoridadeDeveRetornar403() throws Exception {
        String payload = """
                {"produtoId":"%s","depositoId":"%s","quantidadeContada":10,"origem":"AJUSTE","motivo":"contagem"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/estoque/ajustes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}
