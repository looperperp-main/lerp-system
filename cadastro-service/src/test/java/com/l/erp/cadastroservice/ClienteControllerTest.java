package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.ClienteController;
import com.l.erp.cadastroservice.api.dto.ClienteDTO;
import com.l.erp.cadastroservice.api.dto.ClienteResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ClienteAssembler;
import com.l.erp.cadastroservice.domain.Cliente;
import com.l.erp.cadastroservice.services.ClienteService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClienteController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClienteControllerTest {

    private static final String BASE_URL = "/api/v1/clientes";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClienteService service;

    @MockitoBean
    private ClienteAssembler assembler;

    private ClienteDTO buildDto() {
        return new ClienteDTO(null, null, UUID.randomUUID(), "CLI-001", null, null, null, null,
                BigDecimal.TEN, "BAIXO", 30, true, null, null, null, null);
    }

    private ClienteResponseDTO buildResponseDto(UUID id) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(id);
        dto.setTenantId(TENANT_ID);
        dto.setCodigoInterno("CLI-001");
        dto.setAtivo(true);
        dto.add(Link.of("http://localhost" + BASE_URL + "/" + id, "self"));
        return dto;
    }

    @Test
    void shouldListarClientes() throws Exception {
        Cliente cliente = new Cliente();
        when(service.getAllClientes(org.mockito.ArgumentMatchers.eq(TENANT_ID), any())).thenReturn(new PageImpl<>(List.of(cliente)));
        when(assembler.toModel(any())).thenReturn(buildResponseDto(UUID.randomUUID()));

        mockMvc.perform(get(BASE_URL).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectListarSemTenant() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldBuscarClientePorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenReturn(new Cliente());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoInterno").value("CLI-001"));
    }

    @Test
    void shouldReturn404WhenClienteNaoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenThrow(new BusinessException(Constants.CLIENTE_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarCliente() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.save(any(), any(), any())).thenReturn(new Cliente());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(id.toString())));
    }

    @Test
    void shouldRejectCriarSemUserIdHeader() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCriarComPayloadInvalido() throws Exception {
        ClienteDTO dto = new ClienteDTO(null, null, UUID.randomUUID(), "CLI-002", null, null, null, null,
                BigDecimal.TEN, "BAIXO", 30, null, null, null, null, null);

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarCliente() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any(), any())).thenReturn(new Cliente());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoInterno").value("CLI-001"));
    }

    @Test
    void shouldAtualizarStatusCliente() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE_URL + "/{id}/status", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldDeletarCliente() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isNoContent());
    }
}
