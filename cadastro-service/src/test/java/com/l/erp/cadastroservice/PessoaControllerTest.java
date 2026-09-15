package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.dto.PessoaRequestDTO;
import com.l.erp.cadastroservice.api.dto.PessoaResponseDTO;
import com.l.erp.cadastroservice.api.mappers.PessoaAssembler;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.cadastroservice.services.PessoaService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PessoaController.class)
@AutoConfigureMockMvc(addFilters = false)
class PessoaControllerTest {

    private static final String BASE_URL = "/api/v1/pessoas";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PessoaService pessoaService;

    @MockitoBean
    private PessoaAssembler assembler;

    private PessoaRequestDTO buildDto() {
        return new PessoaRequestDTO(TipoPessoa.PF, "Fulano de Tal", null, "12345678900", null, null, null, null, true, null);
    }

    private PessoaResponseDTO buildResponseDto(UUID id) {
        PessoaResponseDTO dto = new PessoaResponseDTO();
        dto.setId(id);
        dto.setNomeRazao("Fulano de Tal");
        dto.add(Link.of("http://localhost" + BASE_URL + "/" + id, "self"));
        return dto;
    }

    @Test
    void shouldListarPessoas() throws Exception {
        when(pessoaService.findAllByTenant(org.mockito.ArgumentMatchers.eq(TENANT_ID), any())).thenReturn(new PageImpl<>(List.of(new Pessoa())));
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
    void shouldBuscarPessoaPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.findByIdAndTenant(id, TENANT_ID)).thenReturn(new Pessoa());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeRazao").value("Fulano de Tal"));
    }

    @Test
    void shouldReturn404WhenPessoaNaoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.findByIdAndTenant(id, TENANT_ID)).thenThrow(new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarPessoa() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.create(any(), any(), any())).thenReturn(new Pessoa());
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
        PessoaRequestDTO dto = new PessoaRequestDTO(null, "Fulano de Tal", null, "12345678900", null, null, null, null, true, null);

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarPessoa() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.update(any(), any(), any(), any())).thenReturn(new Pessoa());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(id));

        mockMvc.perform(put(BASE_URL + "/{id}", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeRazao").value("Fulano de Tal"));
    }

    @Test
    void shouldAtualizarStatusPessoa() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE_URL + "/{id}/status", id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isNoContent());
    }
}
