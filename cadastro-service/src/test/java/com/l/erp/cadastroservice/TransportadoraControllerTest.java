package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.TransportadoraController;
import com.l.erp.cadastroservice.api.dto.TransportadoraDTO;
import com.l.erp.cadastroservice.api.dto.TransportadoraResponseDTO;
import com.l.erp.cadastroservice.api.mappers.TransportadoraAssembler;
import com.l.erp.cadastroservice.domain.Transportadora;
import com.l.erp.cadastroservice.services.TransportadoraService;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransportadoraController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransportadoraControllerTest {

    private static final String BASE_URL = "/api/v1/transportadoras";
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransportadoraService service;

    @MockitoBean
    private TransportadoraAssembler assembler;

    private TransportadoraDTO buildDto() {
        return new TransportadoraDTO(null, null, UUID.randomUUID(), "Transportes Rápidos", "RNTRC123", "Rodoviário", true, null, null, null, null);
    }

    private TransportadoraResponseDTO buildResponseDto(UUID id) {
        TransportadoraResponseDTO dto = new TransportadoraResponseDTO();
        dto.setId(id);
        dto.setPessoaNomeRazao("Transportes Rápidos");
        dto.setAtivo(true);
        dto.add(Link.of("http://localhost" + BASE_URL.replace("{TransportadoraId}", id.toString()) + "/" + id));
        return dto;
    }

    @Test
    void shouldListarTransportadoras() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getAllTransportadoras(any())).thenReturn(new PageImpl<>(List.of(new Transportadora())));
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Transportes Rápidos")));
    }

    @Test
    void shouldBuscarTransportadoraPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(new Transportadora());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoaNomeRazao").value("Transportes Rápidos"));
    }

    @Test
    void shouldReturn404WhenTransportadoraNaoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new BusinessException(Constants.TRANSPORTADORA_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarTransportadora() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.save(any(), any())).thenReturn(new Transportadora());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(id.toString())))
                .andExpect(jsonPath("$.pessoaNomeRazao").value("Transportes Rápidos"));
    }

    @Test
    void shouldRejectCriarSemUserIdHeader() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCriarComPayloadInvalido() throws Exception {
        TransportadoraDTO dto = new TransportadoraDTO(null, null, UUID.randomUUID(), "Transportes Rápidos", "RNTRC123", "Rodoviário", null, null, null, null, null);

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarTransportadora() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any())).thenReturn(new Transportadora());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoaNomeRazao").value("Transportes Rápidos"));
    }

    @Test
    void shouldReturn404WhenAtualizarNaoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any()))
                .thenThrow(new BusinessException(Constants.TRANSPORTADORA_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAtualizarStatus() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE_URL + "/{id}/status", id)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn401WhenAtualizarStatusSemUserIdHeader() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE_URL + "/{id}/status", id))
                .andExpect(status().isUnauthorized());
    }
}
