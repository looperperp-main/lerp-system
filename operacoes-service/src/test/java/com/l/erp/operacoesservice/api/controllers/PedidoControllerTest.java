package com.l.erp.operacoesservice.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarPedidoRequestDTO;
import com.l.erp.operacoesservice.api.dto.ExpedirPedidoRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoResponseDTO;
import com.l.erp.operacoesservice.api.mappers.PedidoAssembler;
import com.l.erp.operacoesservice.api.mappers.PedidoMapper;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.ModalidadeFrete;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.infra.client.FiscalServiceClient;
import com.l.erp.operacoesservice.services.vendas.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.hateoas.Link;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @WebMvcTest dos 10 endpoints de PedidoController (spec/o2c-vendas.md §5/§10, Fase 4). */
@WebMvcTest(controllers = PedidoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PedidoService service;
    @MockitoBean
    private CadastroServiceClient cadastroServiceClient;
    @MockitoBean
    private FiscalServiceClient fiscalServiceClient;
    @MockitoBean
    private PedidoMapper mapper;
    @MockitoBean
    private PedidoAssembler assembler;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();

    private Pedido pedido(UUID id) {
        return Pedido.builder().id(id).clienteId(CLIENTE_ID).status(StatusPedido.ORCAMENTO).build();
    }

    private PedidoResponseDTO responseDto(UUID id) {
        PedidoResponseDTO dto = new PedidoResponseDTO();
        dto.setId(id);
        dto.add(Link.of("http://localhost/api/v1/pedidos/" + id).withSelfRel());
        return dto;
    }

    private PedidoRequestDTO requestDto() {
        return new PedidoRequestDTO(CLIENTE_ID, null, null, null, null, ModalidadeFrete.SEM_FRETE, null,
                List.of(new PedidoItemRequestDTO(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN, null)));
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_ESCRITA")
    void criarDeveRetornar201ComLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(Pedido.builder().build());
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                PedidoItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE)
                        .precoUnitario(BigDecimal.TEN).build()));
        when(cadastroServiceClient.buscarProduto(any(), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new CadastroServiceClient.ProdutoRef("MERCADORIA", null, true, null, null));
        when(service.criarOrcamento(any(), any(), eq(TENANT_ID), eq(USER_ID))).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/pedidos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_LEITURA")
    void criarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_ESCRITA")
    void atualizarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(Pedido.builder().build());
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                PedidoItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE)
                        .precoUnitario(BigDecimal.TEN).build()));
        when(cadastroServiceClient.buscarProduto(any(), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new CadastroServiceClient.ProdutoRef("MERCADORIA", null, true, null, null));
        when(service.atualizar(eq(id), eq(TENANT_ID), eq(USER_ID), any(), any())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(put("/api/v1/pedidos/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_ESCRITA")
    void criarComProdutoInativoDeveRetornar400() throws Exception {
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                PedidoItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE)
                        .precoUnitario(BigDecimal.TEN).build()));
        when(cadastroServiceClient.buscarProduto(any(), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new CadastroServiceClient.ProdutoRef("MERCADORIA", null, false, null, null));

        mockMvc.perform(post("/api/v1/pedidos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_LEITURA")
    void buscarPorIdDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(get("/api/v1/pedidos/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_LEITURA")
    void listarDeveRetornar200() throws Exception {
        Page<Pedido> page = new PageImpl<>(List.of(pedido(UUID.randomUUID())));
        when(service.listar(eq(TENANT_ID), any(), any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(assembler.toModel(any())).thenReturn(responseDto(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/pedidos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_CONFIRMACAO")
    void confirmarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(pedido(id));
        when(cadastroServiceClient.buscarLimiteCredito(CLIENTE_ID, TENANT_ID, USER_ID)).thenReturn(BigDecimal.TEN);
        when(service.confirmar(eq(id), eq(TENANT_ID), eq(USER_ID), eq(false), any())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/pedidos/{id}/confirmar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_EXPEDICAO")
    void expedirDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        ExpedirPedidoRequestDTO dto = new ExpedirPedidoRequestDTO(UUID.randomUUID(), null, null, null);
        when(service.expedir(eq(id), eq(TENANT_ID), eq(USER_ID), any(), any(), any(), any())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/pedidos/{id}/expedir", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_FATURAMENTO")
    void faturarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID condicaoId = UUID.randomUUID();
        Pedido pedidoComCondicao = Pedido.builder().id(id).clienteId(CLIENTE_ID)
                .status(StatusPedido.EXPEDIDO).condicaoPagamentoId(condicaoId).build();
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(pedidoComCondicao);
        when(cadastroServiceClient.buscarParcelas(condicaoId, TENANT_ID, USER_ID)).thenReturn(List.of());
        when(service.listarItens(id)).thenReturn(List.of());
        when(service.faturar(eq(id), eq(TENANT_ID), eq(USER_ID), any(), any()))
                .thenReturn(new PedidoService.FaturamentoResultado(pedidoComCondicao, List.of()));
        when(assembler.toFaturamentoModel(any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/pedidos/{id}/faturar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_FATURAMENTO")
    void faturarSemCondicaoPagamentoDeveRetornar400() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(pedido(id));

        mockMvc.perform(post("/api/v1/pedidos/{id}/faturar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_CANCELAMENTO")
    void cancelarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        CancelarPedidoRequestDTO dto = new CancelarPedidoRequestDTO("Cliente desistiu");
        when(service.cancelar(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/pedidos/{id}/cancelar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_ESCRITA")
    void recalcularPrecosDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.recalcularPrecos(id, TENANT_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/pedidos/{id}/recalcular-precos", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_ESCRITA")
    void reabrirDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.reabrir(id, TENANT_ID, USER_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/pedidos/{id}/reabrir", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PEDIDO_ESCRITA")
    void buscarPorIdSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(get("/api/v1/pedidos/{id}", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }
}
