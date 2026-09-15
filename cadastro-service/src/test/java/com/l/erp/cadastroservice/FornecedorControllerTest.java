package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.FornecedorController;
import com.l.erp.cadastroservice.api.dto.FornecedorDto;
import com.l.erp.cadastroservice.api.dto.FornecedorResponseDTO;
import com.l.erp.cadastroservice.api.mappers.FornecedorAssembler;
import com.l.erp.cadastroservice.domain.Fornecedor;
import com.l.erp.cadastroservice.services.FornecedorService;
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

@WebMvcTest(controllers = FornecedorController.class)
@AutoConfigureMockMvc(addFilters = false)
class FornecedorControllerTest {

    private static final String BASE_URL = "/api/v1/fornecedores";
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FornecedorService service;

    @MockitoBean
    private FornecedorAssembler assembler;

    private FornecedorDto buildDto() {
        return new FornecedorDto(null, null, UUID.randomUUID(), true, null, null, null, null, null);
    }

    private FornecedorResponseDTO buildResponseDto(UUID id) {
        FornecedorResponseDTO dto = new FornecedorResponseDTO();
        dto.setId(id);
        dto.setPessoaNomeRazao("Fornecedor Teste");
        dto.setAtivo(true);
        dto.add(Link.of("http://localhost" + BASE_URL.replace("{FornecedorId}", id.toString()) + "/" + id));
        return dto;
    }

    @Test
    void shouldListarFornecedores() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getAllFornecedores(any())).thenReturn(new PageImpl<>(List.of(new Fornecedor())));
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fornecedor Teste")));
    }

    @Test
    void shouldBuscarFornecedorPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(new Fornecedor());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoaNomeRazao").value("Fornecedor Teste"));
    }

    @Test
    void shouldReturn404WhenFornecedorNaoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new BusinessException(Constants.FORNECEDORES_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarFornecedor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.save(any(), any())).thenReturn(new Fornecedor());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(id.toString())))
                .andExpect(jsonPath("$.pessoaNomeRazao").value("Fornecedor Teste"));
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
        FornecedorDto dto = new FornecedorDto(null, null, UUID.randomUUID(), null, null, null, null, null, null);

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarFornecedor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any())).thenReturn(new Fornecedor());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoaNomeRazao").value("Fornecedor Teste"));
    }

    @Test
    void shouldReturn404WhenAtualizarNaoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any()))
                .thenThrow(new BusinessException(Constants.FORNECEDORES_NOT_FOUND, HttpStatus.NOT_FOUND));

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
