package com.l.erp.operacoesservice.api.controllers.compras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarPedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraResponseDTO;
import com.l.erp.operacoesservice.api.dto.ReprovarPedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.mappers.PedidoCompraAssembler;
import com.l.erp.operacoesservice.api.mappers.PedidoCompraMapper;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import com.l.erp.operacoesservice.services.compras.PedidoCompraService;
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

/** @WebMvcTest dos endpoints de PedidoCompraController (spec/p2p-compras.md, Fase 2), mesmo padrão
 * de RequisicaoCompraControllerTest (Fase 1b). */
@WebMvcTest(controllers = PedidoCompraController.class)
@AutoConfigureMockMvc(addFilters = false)
class PedidoCompraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PedidoCompraService service;
    @MockitoBean
    private PedidoCompraMapper mapper;
    @MockitoBean
    private PedidoCompraAssembler assembler;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private PedidoCompra pedido(UUID id) {
        return PedidoCompra.builder().id(id).status(StatusPedidoCompra.RASCUNHO).build();
    }

    private PedidoCompraResponseDTO responseDto(UUID id) {
        PedidoCompraResponseDTO dto = new PedidoCompraResponseDTO();
        dto.setId(id);
        dto.add(Link.of("http://localhost/api/v1/compras/pedidos/" + id).withSelfRel());
        return dto;
    }

    private PedidoCompraRequestDTO requestDto() {
        return new PedidoCompraRequestDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null, null,
                null, List.of(new PedidoCompraItemRequestDTO(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN)));
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void criarDeveRetornar201ComLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(PedidoCompra.builder().build());
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                PedidoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).precoUnitario(BigDecimal.TEN).build()));
        when(service.criar(any(), any(), eq(TENANT_ID), eq(USER_ID))).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void criarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/compras/pedidos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void atualizarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(PedidoCompra.builder().build());
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                PedidoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).precoUnitario(BigDecimal.TEN).build()));
        when(service.atualizar(eq(id), eq(TENANT_ID), eq(USER_ID), any(), any())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(put("/api/v1/compras/pedidos/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void buscarPorIdDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(get("/api/v1/compras/pedidos/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void buscarPorIdSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(get("/api/v1/compras/pedidos/{id}", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void listarDeveRetornar200() throws Exception {
        Page<PedidoCompra> page = new PageImpl<>(List.of(pedido(UUID.randomUUID())));
        when(service.listar(eq(TENANT_ID), any(), any(), any(), any(), any())).thenReturn(page);
        when(assembler.toModel(any())).thenReturn(responseDto(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/compras/pedidos")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void enviarParaAprovacaoDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.enviarParaAprovacao(id, TENANT_ID, USER_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/enviar-aprovacao", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_APROVAR_PEDIDO")
    void aprovarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.aprovar(id, TENANT_ID, USER_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/aprovar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void aprovarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/aprovar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_APROVAR_PEDIDO")
    void reprovarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        ReprovarPedidoCompraRequestDTO dto = new ReprovarPedidoCompraRequestDTO("preço acima do mercado");
        when(service.reprovar(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/reprovar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_APROVAR_PEDIDO")
    void reprovarSemMotivoDeveRetornar400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/reprovar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReprovarPedidoCompraRequestDTO(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void reabrirDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.reabrir(id, TENANT_ID, USER_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/reabrir", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void enviarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.enviar(id, TENANT_ID, USER_ID)).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/enviar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void cancelarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        CancelarPedidoCompraRequestDTO dto = new CancelarPedidoCompraRequestDTO("desistência");
        when(service.cancelar(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/cancelar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_APROVAR_PEDIDO")
    void cancelarSemAutoridadeDeveRetornar403() throws Exception {
        CancelarPedidoCompraRequestDTO dto = new CancelarPedidoCompraRequestDTO("desistência");

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/cancelar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void encerrarSaldoDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        com.l.erp.operacoesservice.api.dto.EncerrarSaldoPedidoCompraRequestDTO dto =
                new com.l.erp.operacoesservice.api.dto.EncerrarSaldoPedidoCompraRequestDTO("saldo residual não será entregue");
        when(service.encerrarSaldo(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(pedido(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/encerrar-saldo", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void encerrarSaldoSemMotivoDeveRetornar400() throws Exception {
        mockMvc.perform(post("/api/v1/compras/pedidos/{id}/encerrar-saldo", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.l.erp.operacoesservice.api.dto.EncerrarSaldoPedidoCompraRequestDTO(""))))
                .andExpect(status().isBadRequest());
    }
}
