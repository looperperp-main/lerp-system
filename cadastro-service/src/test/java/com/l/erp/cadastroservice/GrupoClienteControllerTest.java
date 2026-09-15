package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.GrupoClienteController;
import com.l.erp.cadastroservice.api.dto.GrupoClienteDTO;
import com.l.erp.cadastroservice.services.GrupoClienteService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GrupoClienteController.class)
@AutoConfigureMockMvc(addFilters = false)
class GrupoClienteControllerTest {

    private static final String BASE_URL = "/api/v1/grupos-clientes";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GrupoClienteService service;

    private GrupoClienteDTO buildDto() {
        return new GrupoClienteDTO(null, "Grupo A", "descricao", true, null, null, null, null);
    }

    @Test
    void shouldListarGrupos() throws Exception {
        when(service.getAllGroups(org.mockito.ArgumentMatchers.eq(TENANT_ID), any())).thenReturn(new PageImpl<>(List.of(buildDto())));

        mockMvc.perform(get(BASE_URL).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void shouldFalharListarSemTenant() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldBuscarGrupoPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenReturn(buildDto());

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Grupo A"));
    }

    @Test
    void shouldReturn404WhenGrupoNaoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenThrow(new BusinessException(Constants.USUARIO_UUID_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarGrupo() throws Exception {
        when(service.save(any(), any(), any())).thenReturn(buildDto());

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Grupo A"));
    }

    @Test
    void shouldAtualizarGrupo() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any(), any())).thenReturn(buildDto());

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Grupo A"));
    }

    @Test
    void shouldAtualizarStatusGrupo() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE_URL + "/{id}/status", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isNoContent());
    }
}
