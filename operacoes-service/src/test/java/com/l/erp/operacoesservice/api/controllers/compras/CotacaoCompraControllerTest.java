package com.l.erp.operacoesservice.api.controllers.compras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarCotacaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRespostaItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRespostaRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraResponseDTO;
import com.l.erp.operacoesservice.api.dto.DeclinarCotacaoFornecedorRequestDTO;
import com.l.erp.operacoesservice.api.dto.EncerrarCotacaoRequestDTO;
import com.l.erp.operacoesservice.api.mappers.CotacaoCompraAssembler;
import com.l.erp.operacoesservice.api.mappers.CotacaoCompraMapper;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompra;
import com.l.erp.operacoesservice.services.compras.CotacaoCompraService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @WebMvcTest de CotacaoCompraController (spec/p2p-compras.md, Fase 5), mesmo padrão de
 * PedidoCompraControllerTest/RequisicaoCompraControllerTest. */
@WebMvcTest(controllers = CotacaoCompraController.class)
@AutoConfigureMockMvc(addFilters = false)
class CotacaoCompraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CotacaoCompraService service;
    @MockitoBean
    private CotacaoCompraMapper mapper;
    @MockitoBean
    private CotacaoCompraAssembler assembler;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private CotacaoCompra cotacao(UUID id) {
        CotacaoCompra cotacao = CotacaoCompra.builder().id(id).status(StatusCotacaoCompra.ABERTA).build();
        cotacao.setTenantId(TENANT_ID);
        return cotacao;
    }

    private CotacaoCompraResponseDTO responseDto(UUID id) {
        CotacaoCompraResponseDTO dto = new CotacaoCompraResponseDTO();
        dto.setId(id);
        dto.add(Link.of("http://localhost/api/v1/compras/cotacoes/" + id));
        return dto;
    }

    private CotacaoCompraRequestDTO requestDto() {
        return new CotacaoCompraRequestDTO(null, UUID.randomUUID(), null, List.of(UUID.randomUUID()),
                List.of(new CotacaoCompraItemRequestDTO(UUID.randomUUID(), BigDecimal.ONE)));
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void criarDeveRetornar201() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(CotacaoCompra.builder().build());
        when(mapper.toItemEntities(any())).thenReturn(List.of(
                CotacaoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).build()));
        when(service.criar(any(), any(), any(), eq(TENANT_ID), eq(USER_ID))).thenReturn(cotacao(id));
        when(assembler.toDetailModel(any(), any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/cotacoes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void criarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/compras/cotacoes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void buscarPorIdDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(cotacao(id));
        when(assembler.toDetailModel(any(), any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(get("/api/v1/compras/cotacoes/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void buscarPorIdSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(get("/api/v1/compras/cotacoes/{id}", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void listarDeveRetornar200() throws Exception {
        Page<CotacaoCompra> page = new PageImpl<>(List.of(cotacao(UUID.randomUUID())));
        when(service.listar(eq(TENANT_ID), any(), any(), any())).thenReturn(page);
        when(assembler.toModel(any())).thenReturn(responseDto(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/compras/cotacoes")
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void registrarRespostaDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID cotacaoFornecedorId = UUID.randomUUID();
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(UUID.randomUUID(), 5, BigDecimal.TEN, "obs",
                List.of(new CotacaoCompraRespostaItemRequestDTO(UUID.randomUUID(), BigDecimal.TEN)));
        when(service.registrarResposta(eq(id), eq(cotacaoFornecedorId), eq(TENANT_ID), eq(USER_ID), any())).thenReturn(cotacao(id));
        when(assembler.toDetailModel(any(), any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/fornecedores/{cotacaoFornecedorId}/responder", id, cotacaoFornecedorId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void registrarRespostaSemAutoridadeDeveRetornar403() throws Exception {
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(UUID.randomUUID(), 5, BigDecimal.TEN, "obs",
                List.of(new CotacaoCompraRespostaItemRequestDTO(UUID.randomUUID(), BigDecimal.TEN)));

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/fornecedores/{cotacaoFornecedorId}/responder",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void declinarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID cotacaoFornecedorId = UUID.randomUUID();
        DeclinarCotacaoFornecedorRequestDTO dto = new DeclinarCotacaoFornecedorRequestDTO("sem estoque");
        when(service.declinar(id, cotacaoFornecedorId, TENANT_ID, USER_ID, dto.motivo())).thenReturn(cotacao(id));
        when(assembler.toDetailModel(any(), any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/fornecedores/{cotacaoFornecedorId}/declinar", id, cotacaoFornecedorId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void declinarSemCorpoDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID cotacaoFornecedorId = UUID.randomUUID();
        when(service.declinar(id, cotacaoFornecedorId, TENANT_ID, USER_ID, null)).thenReturn(cotacao(id));
        when(assembler.toDetailModel(any(), any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/fornecedores/{cotacaoFornecedorId}/declinar", id, cotacaoFornecedorId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void encerrarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        EncerrarCotacaoRequestDTO dto = new EncerrarCotacaoRequestDTO(UUID.randomUUID());
        when(service.encerrar(id, dto.cotacaoFornecedorVencedorId(), TENANT_ID, USER_ID)).thenReturn(cotacao(id));
        when(assembler.toDetailModel(any(), any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/encerrar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void encerrarSemAutoridadeDeveRetornar403() throws Exception {
        EncerrarCotacaoRequestDTO dto = new EncerrarCotacaoRequestDTO(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/encerrar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_CRIAR")
    void cancelarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        CancelarCotacaoRequestDTO dto = new CancelarCotacaoRequestDTO("desistência");
        when(service.cancelar(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(cotacao(id));
        when(assembler.toDetailModel(any(), any(), any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/cancelar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void cancelarSemAutoridadeDeveRetornar403() throws Exception {
        CancelarCotacaoRequestDTO dto = new CancelarCotacaoRequestDTO("desistência");

        mockMvc.perform(post("/api/v1/compras/cotacoes/{id}/cancelar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }
}
