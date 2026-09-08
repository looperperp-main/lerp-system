package com.l.erp.operacoesservice.api.controllers.compras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarRequisicaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.ReprovarRequisicaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraResponseDTO;
import com.l.erp.operacoesservice.api.mappers.RequisicaoCompraAssembler;
import com.l.erp.operacoesservice.api.mappers.RequisicaoCompraMapper;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRequisicaoCompra;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.services.compras.RequisicaoCompraService;
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

/** @WebMvcTest dos endpoints de RequisicaoCompraController (spec/p2p-compras.md, Fase 1b), mesmo padrão
 * de PedidoControllerTest (vendas). */
@WebMvcTest(controllers = RequisicaoCompraController.class)
@AutoConfigureMockMvc(addFilters = false)
class RequisicaoCompraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RequisicaoCompraService service;
    @MockitoBean
    private CadastroServiceClient cadastroServiceClient;
    @MockitoBean
    private RequisicaoCompraMapper mapper;
    @MockitoBean
    private RequisicaoCompraAssembler assembler;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private RequisicaoCompra requisicao(UUID id) {
        return RequisicaoCompra.builder().id(id).status(StatusRequisicaoCompra.RASCUNHO).build();
    }

    private RequisicaoCompraResponseDTO responseDto(UUID id) {
        RequisicaoCompraResponseDTO dto = new RequisicaoCompraResponseDTO();
        dto.setId(id);
        dto.add(Link.of("http://localhost/api/v1/compras/requisicoes/" + id).withSelfRel());
        return dto;
    }

    private RequisicaoCompraRequestDTO requestDto() {
        return new RequisicaoCompraRequestDTO(UUID.randomUUID(), null, "justificativa", null,
                List.of(new RequisicaoCompraItemRequestDTO(UUID.randomUUID(), BigDecimal.ONE, null)));
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void criarDeveRetornar201ComLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(RequisicaoCompra.builder().build());
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                RequisicaoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).build()));
        when(cadastroServiceClient.buscarProduto(any(), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new CadastroServiceClient.ProdutoRef("SERVICO", null, true, null, null, "Serviço Teste"));
        when(service.criar(any(), any(), eq(TENANT_ID), eq(USER_ID), eq(false))).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/requisicoes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void criarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/compras/requisicoes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void criarComProdutoInativoDeveRetornar400() throws Exception {
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                RequisicaoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).build()));
        when(cadastroServiceClient.buscarProduto(any(), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new CadastroServiceClient.ProdutoRef("SERVICO", null, false, null, null, "Serviço Inativo"));

        mockMvc.perform(post("/api/v1/compras/requisicoes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void atualizarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(RequisicaoCompra.builder().build());
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                RequisicaoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).build()));
        when(cadastroServiceClient.buscarProduto(any(), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new CadastroServiceClient.ProdutoRef("SERVICO", null, true, null, null, "Serviço Teste"));
        when(service.atualizar(eq(id), eq(TENANT_ID), eq(USER_ID), any(), any(), eq(false))).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(put("/api/v1/compras/requisicoes/{id}", id)
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
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(get("/api/v1/compras/requisicoes/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void buscarPorIdSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(get("/api/v1/compras/requisicoes/{id}", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void listarDeveRetornar200() throws Exception {
        Page<RequisicaoCompra> page = new PageImpl<>(List.of(requisicao(UUID.randomUUID())));
        when(service.listar(eq(TENANT_ID), any(), any(), any(), any(), any())).thenReturn(page);
        when(assembler.toModel(any())).thenReturn(responseDto(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/compras/requisicoes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void enviarParaAprovacaoDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.enviarParaAprovacao(id, TENANT_ID, USER_ID)).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/requisicoes/{id}/enviar-aprovacao", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_APROVAR_PEDIDO")
    void aprovarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.aprovar(id, TENANT_ID, USER_ID)).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/requisicoes/{id}/aprovar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void aprovarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/compras/requisicoes/{id}/aprovar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_APROVAR_PEDIDO")
    void reprovarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        ReprovarRequisicaoRequestDTO dto = new ReprovarRequisicaoRequestDTO("preço acima do mercado");
        when(service.reprovar(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/requisicoes/{id}/reprovar", id)
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

        mockMvc.perform(post("/api/v1/compras/requisicoes/{id}/reprovar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReprovarRequisicaoRequestDTO(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void cancelarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        CancelarRequisicaoRequestDTO dto = new CancelarRequisicaoRequestDTO("desistência");
        when(service.cancelar(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/requisicoes/{id}/cancelar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void reabrirDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.reabrir(id, TENANT_ID, USER_ID)).thenReturn(requisicao(id));
        when(assembler.toDetailModel(any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/requisicoes/{id}/reabrir", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }
}
