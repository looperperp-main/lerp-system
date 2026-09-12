package com.l.erp.operacoesservice.api.controllers.compras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarRecebimentoRequestDTO;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaRequestDTO;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaResponseDTO;
import com.l.erp.operacoesservice.api.mappers.RecebimentoMercadoriaAssembler;
import com.l.erp.operacoesservice.api.mappers.RecebimentoMercadoriaMapper;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoFiscal;
import com.l.erp.operacoesservice.services.compras.RecebimentoMercadoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.hateoas.Link;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @WebMvcTest de RecebimentoMercadoriaController (spec/p2p-compras.md, Fase 3), mesmo padrão de
 * PedidoCompraControllerTest (Fase 2). */
@WebMvcTest(controllers = RecebimentoMercadoriaController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecebimentoMercadoriaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private RecebimentoMercadoriaService service;
    @MockitoBean private RecebimentoMercadoriaMapper mapper;
    @MockitoBean private RecebimentoMercadoriaAssembler assembler;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private RecebimentoMercadoria recebimento(UUID id) {
        return RecebimentoMercadoria.builder().id(id).status(StatusRecebimentoMercadoria.EM_CONFERENCIA).build();
    }

    private RecebimentoMercadoriaResponseDTO responseDto(UUID id) {
        RecebimentoMercadoriaResponseDTO dto = new RecebimentoMercadoriaResponseDTO();
        dto.setId(id);
        dto.add(Link.of("http://localhost/api/v1/compras/recebimentos/" + id, "self"));
        return dto;
    }

    private RecebimentoMercadoriaRequestDTO requestDto() {
        return new RecebimentoMercadoriaRequestDTO(UUID.randomUUID(), LocalDate.now(), TipoDocumentoFiscal.NFE,
                "123", "1", "35250612345678000195550010000001231234567890", null, LocalDate.now(), BigDecimal.TEN,
                UUID.randomUUID(), null,
                List.of(new RecebimentoMercadoriaItemRequestDTO(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN)));
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_RECEBER")
    void criarDeveRetornar201() throws Exception {
        UUID id = UUID.randomUUID();
        when(mapper.toEntity(any())).thenReturn(RecebimentoMercadoria.builder().build());
        when(service.criar(any(), any(), any(), eq(TENANT_ID), eq(USER_ID))).thenReturn(recebimento(id));
        when(assembler.toDetailModel(any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/pedidos/{pedidoId}/recebimentos", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void criarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/compras/pedidos/{pedidoId}/recebimentos", UUID.randomUUID())
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
        when(service.buscarPorId(id, TENANT_ID)).thenReturn(recebimento(id));
        when(assembler.toDetailModel(any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(get("/api/v1/compras/recebimentos/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_RECEBER")
    void confirmarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.confirmar(id, TENANT_ID, USER_ID)).thenReturn(recebimento(id));
        when(assembler.toDetailModel(any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/recebimentos/{id}/confirmar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_VISUALIZAR")
    void confirmarSemAutoridadeDeveRetornar403() throws Exception {
        mockMvc.perform(post("/api/v1/compras/recebimentos/{id}/confirmar", UUID.randomUUID())
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPRAS_RECEBER")
    void cancelarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        CancelarRecebimentoRequestDTO dto = new CancelarRecebimentoRequestDTO("NF cancelada pelo fornecedor");
        when(service.cancelar(id, TENANT_ID, USER_ID, dto.motivo())).thenReturn(recebimento(id));
        when(assembler.toDetailModel(any(), any())).thenReturn(responseDto(id));

        mockMvc.perform(post("/api/v1/compras/recebimentos/{id}/cancelar", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
