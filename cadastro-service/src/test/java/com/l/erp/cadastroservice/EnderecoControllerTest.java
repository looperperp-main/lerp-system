package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.EnderecoController;
import com.l.erp.cadastroservice.api.dto.EnderecoRequestDTO;
import com.l.erp.cadastroservice.api.dto.EnderecoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.EnderecoAssembler;
import com.l.erp.cadastroservice.domain.Endereco;
import com.l.erp.cadastroservice.domain.enumerators.TipoEndereco;
import com.l.erp.cadastroservice.services.EnderecoService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.hateoas.CollectionModel;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EnderecoController.class)
@AutoConfigureMockMvc(addFilters = false)
class EnderecoControllerTest {

    private static final String BASE_URL = "/api/v1/pessoas/{pessoaId}/enderecos";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EnderecoService enderecoService;

    @MockitoBean
    private EnderecoAssembler assembler;

    private EnderecoRequestDTO buildDto() {
        return new EnderecoRequestDTO(TipoEndereco.FISCAL, "Rua Teste", "100", null, "Centro", "Cidade", "SP", "12345678", null, null, true);
    }

    private EnderecoResponseDTO buildResponseDto(UUID pessoaId, UUID id) {
        EnderecoResponseDTO dto = new EnderecoResponseDTO();
        dto.setId(id);
        dto.setTenantId(TENANT_ID);
        dto.setCidade("Cidade");
        dto.add(Link.of("http://localhost" + BASE_URL.replace("{pessoaId}", pessoaId.toString()) + "/" + id, "self"));
        return dto;
    }

    @Test
    void shouldListarEnderecosDaPessoa() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        when(enderecoService.findAllByPessoa(pessoaId, TENANT_ID)).thenReturn(List.of(new Endereco()));
        when(assembler.toCollectionModel(any())).thenReturn(CollectionModel.of(List.of(buildResponseDto(pessoaId, UUID.randomUUID()))));

        mockMvc.perform(get(BASE_URL, pessoaId).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cidade")));
    }

    @Test
    void shouldRejectListarSemTenant() throws Exception {
        mockMvc.perform(get(BASE_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldBuscarEnderecoPorId() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(enderecoService.findById(id, pessoaId, TENANT_ID)).thenReturn(new Endereco());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(get(BASE_URL + "/{id}", pessoaId, id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidade").value("Cidade"));
    }

    @Test
    void shouldReturn404WhenEnderecoNaoEncontrado() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(enderecoService.findById(id, pessoaId, TENANT_ID))
                .thenThrow(new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", pessoaId, id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarEndereco() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(enderecoService.create(any(), any(), any(), any())).thenReturn(new Endereco());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(post(BASE_URL, pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(id.toString())));
    }

    @Test
    void shouldRejectCriarSemUserIdHeader() throws Exception {
        UUID pessoaId = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL, pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCriarComPayloadInvalido() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        EnderecoRequestDTO dto = new EnderecoRequestDTO(TipoEndereco.FISCAL, "", "100", null, "Centro", "Cidade", "SP", "12345678", null, null, true);

        mockMvc.perform(post(BASE_URL, pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarEndereco() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(enderecoService.update(any(), any(), any(), any(), any())).thenReturn(new Endereco());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(put(BASE_URL + "/{id}", pessoaId, id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidade").value("Cidade"));
    }

    @Test
    void shouldReturn404WhenAtualizarNaoEncontrado() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(enderecoService.update(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(put(BASE_URL + "/{id}", pessoaId, id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isNotFound());
    }
}
