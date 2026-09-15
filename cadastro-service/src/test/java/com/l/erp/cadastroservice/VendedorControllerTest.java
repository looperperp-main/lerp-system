package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.VendedorController;
import com.l.erp.cadastroservice.api.dto.VendedorDTO;
import com.l.erp.cadastroservice.api.dto.VendedorResponseDTO;
import com.l.erp.cadastroservice.api.mappers.VendedorAssembler;
import com.l.erp.cadastroservice.domain.Vendedor;
import com.l.erp.cadastroservice.services.VendedorService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VendedorController.class)
@AutoConfigureMockMvc(addFilters = false)
class VendedorControllerTest {

    private static final String BASE_URL = "/api/v1/vendedores";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VendedorService service;

    @MockitoBean
    private VendedorAssembler assembler;

    private VendedorDTO buildDto() {
        return new VendedorDTO(null, null, UUID.randomUUID(), "Vendedor A", BigDecimal.TEN, true, null, null, null, null);
    }

    private VendedorResponseDTO buildResponseDto(UUID id) {
        VendedorResponseDTO dto = new VendedorResponseDTO();
        dto.setId(id);
        dto.setNome("Vendedor A");
        dto.add(Link.of("http://localhost" + BASE_URL + "/" + id, "self"));
        return dto;
    }

    @Test
    void shouldListarVendedores() throws Exception {
        when(service.getAllVendedores(org.mockito.ArgumentMatchers.eq(TENANT_ID), any())).thenReturn(new PageImpl<>(List.of(new Vendedor())));
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
    void shouldBuscarVendedorPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenReturn(new Vendedor());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Vendedor A"));
    }

    @Test
    void shouldReturn404WhenVendedorNaoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, TENANT_ID)).thenThrow(new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarVendedor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.save(any(), any(), any())).thenReturn(new Vendedor());
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
    void shouldRejectCriarComPayloadInvalido() throws Exception {
        VendedorDTO dto = new VendedorDTO(null, null, UUID.randomUUID(), null, BigDecimal.TEN, true, null, null, null, null);

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarVendedor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any(), any())).thenReturn(new Vendedor());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Vendedor A"));
    }
}
