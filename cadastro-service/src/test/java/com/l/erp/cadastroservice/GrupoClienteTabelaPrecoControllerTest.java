package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.GrupoClienteTabelaPrecoController;
import com.l.erp.cadastroservice.api.dto.GrupoClienteTabelaPrecoRequestDTO;
import com.l.erp.cadastroservice.api.dto.GrupoClienteTabelaPrecoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.GrupoClienteTabelaPrecoAssembler;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoCliente;
import com.l.erp.cadastroservice.services.GrupoClienteTabelaPrecoService;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GrupoClienteTabelaPrecoController.class)
@AutoConfigureMockMvc(addFilters = false)
class GrupoClienteTabelaPrecoControllerTest {

    private static final String BASE_URL = "/api/v1/grupos-clientes/{grupoClienteId}/tabelas-preco";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GrupoClienteTabelaPrecoService service;

    @MockitoBean
    private GrupoClienteTabelaPrecoAssembler assembler;

    private GrupoClienteTabelaPrecoResponseDTO buildResponseDto(UUID grupoClienteId) {
        GrupoClienteTabelaPrecoResponseDTO dto = new GrupoClienteTabelaPrecoResponseDTO();
        dto.setGrupoClienteId(grupoClienteId);
        dto.setTabelaPrecoId(UUID.randomUUID());
        dto.setTenantId(TENANT_ID);
        dto.setTabelaPrecoNome("Tabela Padrão");
        return dto;
    }

    @Test
    void shouldListarAssociacoes() throws Exception {
        UUID grupoClienteId = UUID.randomUUID();

        when(service.getAssociacoes(grupoClienteId, TENANT_ID)).thenReturn(List.of(new TabelaPrecoGrupoCliente()));
        when(assembler.toCollectionModel(any())).thenReturn(CollectionModel.of(List.of(buildResponseDto(grupoClienteId))));

        mockMvc.perform(get(BASE_URL, grupoClienteId).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tabela Padrão")));
    }

    @Test
    void shouldReturn401WhenListarSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldSincronizarAssociacoes() throws Exception {
        UUID grupoClienteId = UUID.randomUUID();
        GrupoClienteTabelaPrecoRequestDTO dto = new GrupoClienteTabelaPrecoRequestDTO(List.of(UUID.randomUUID()));

        mockMvc.perform(put(BASE_URL, grupoClienteId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectSincronizarSemUserIdHeader() throws Exception {
        UUID grupoClienteId = UUID.randomUUID();
        GrupoClienteTabelaPrecoRequestDTO dto = new GrupoClienteTabelaPrecoRequestDTO(List.of(UUID.randomUUID()));

        mockMvc.perform(put(BASE_URL, grupoClienteId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSincronizarComPayloadInvalido() throws Exception {
        UUID grupoClienteId = UUID.randomUUID();
        GrupoClienteTabelaPrecoRequestDTO dto = new GrupoClienteTabelaPrecoRequestDTO(null);

        mockMvc.perform(put(BASE_URL, grupoClienteId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
