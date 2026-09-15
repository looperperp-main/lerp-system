package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.TabelaPrecoController;
import com.l.erp.cadastroservice.api.dto.TabelaPrecoDTO;
import com.l.erp.cadastroservice.api.dto.TabelaPrecoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.TabelaPrecoAssembler;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.services.TabelaPrecoService;
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

import java.time.LocalDate;
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

@WebMvcTest(controllers = TabelaPrecoController.class)
@AutoConfigureMockMvc(addFilters = false)
class TabelaPrecoControllerTest {

    private static final String BASE_URL = "/api/v1/tabelas-preco";
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TabelaPrecoService service;

    @MockitoBean
    private TabelaPrecoAssembler assembler;

    private TabelaPrecoDTO buildDto() {
        return new TabelaPrecoDTO(null, null, "Tabela Padrão", "BRL", true, true, LocalDate.now(), null, null, null, null, null);
    }

    private TabelaPrecoResponseDTO buildResponseDto(UUID id) {
        TabelaPrecoResponseDTO dto = new TabelaPrecoResponseDTO();
        dto.setId(id);
        dto.setNome("Tabela Padrão");
        dto.setMoeda("BRL");
        dto.setAtiva(true);
        return dto;
    }

    @Test
    void shouldListarTabelasDePreco() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getAll(any())).thenReturn(new PageImpl<>(List.of(new TabelaPreco())));
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tabela Padrão")));
    }

    @Test
    void shouldBuscarTabelaPrecoPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(new TabelaPreco());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Tabela Padrão"));
    }

    @Test
    void shouldReturn404WhenTabelaPrecoNaoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new BusinessException(Constants.TABELA_PRECO_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarTabelaPreco() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.save(any(), any())).thenReturn(new TabelaPreco());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(id.toString())))
                .andExpect(jsonPath("$.nome").value("Tabela Padrão"));
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
        TabelaPrecoDTO dto = new TabelaPrecoDTO(null, null, "", "", null, null, null, null, null, null, null, null);

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarTabelaPreco() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any())).thenReturn(new TabelaPreco());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Tabela Padrão"));
    }

    @Test
    void shouldReturn404WhenAtualizarNaoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(), any(), any()))
                .thenThrow(new BusinessException(Constants.TABELA_PRECO_NOT_FOUND, HttpStatus.NOT_FOUND));

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
