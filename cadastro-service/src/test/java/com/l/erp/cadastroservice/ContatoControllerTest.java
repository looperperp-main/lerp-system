package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.ContatoController;
import com.l.erp.cadastroservice.api.dto.ContatoRequestDTO;
import com.l.erp.cadastroservice.api.dto.ContatoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ContatoAssembler;
import com.l.erp.cadastroservice.domain.Contato;
import com.l.erp.cadastroservice.domain.enumerators.TipoContato;
import com.l.erp.cadastroservice.services.ContatoService;
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

@WebMvcTest(controllers = ContatoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContatoControllerTest {

    private static final String BASE_URL = "/api/v1/pessoas/{pessoaId}/contatos";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContatoService contatoService;

    @MockitoBean
    private ContatoAssembler assembler;

    private ContatoRequestDTO buildDto() {
        return new ContatoRequestDTO("Fulano de Tal", TipoContato.COMERCIAL, "Gerente", "fulano@teste.com", "11999999999", true, true);
    }

    private ContatoResponseDTO buildResponseDto(UUID pessoaId, UUID id) {
        ContatoResponseDTO dto = new ContatoResponseDTO();
        dto.setId(id);
        dto.setTenantId(TENANT_ID);
        dto.setNome("Fulano de Tal");
        dto.setTipo(TipoContato.COMERCIAL);
        dto.setPrincipal(true);
        dto.setAtivo(true);
        dto.add(Link.of("http://localhost" + BASE_URL.replace("{pessoaId}", pessoaId.toString()) + "/" + id));
        return dto;
    }

    @Test
    void shouldListarContatosPorPessoa() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(contatoService.findAllByPessoa(pessoaId, TENANT_ID)).thenReturn(List.of(new Contato()));
        when(assembler.toCollectionModel(any())).thenReturn(CollectionModel.of(List.of(buildResponseDto(pessoaId, id))));

        mockMvc.perform(get(BASE_URL, pessoaId).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fulano de Tal")));
    }

    @Test
    void shouldReturn401WhenListarSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldBuscarContatoPorId() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(contatoService.findById(id, pessoaId, TENANT_ID)).thenReturn(new Contato());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(get(BASE_URL + "/{id}", pessoaId, id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Fulano de Tal"));
    }

    @Test
    void shouldReturn404WhenContatoNaoEncontrado() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(contatoService.findById(id, pessoaId, TENANT_ID))
                .thenThrow(new BusinessException(Constants.CONTATO_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", pessoaId, id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarContato() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ContatoRequestDTO dto = buildDto();

        when(contatoService.create(any(), any(), any(), any())).thenReturn(new Contato());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(post(BASE_URL, pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(id.toString())))
                .andExpect(jsonPath("$.nome").value("Fulano de Tal"));
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
        ContatoRequestDTO dto = new ContatoRequestDTO("", null, null, null, null, null, null);

        mockMvc.perform(post(BASE_URL, pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarContato() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ContatoRequestDTO dto = buildDto();

        when(contatoService.update(any(), any(), any(), any(), any())).thenReturn(new Contato());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(put(BASE_URL + "/{id}", pessoaId, id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Fulano de Tal"));
    }

    @Test
    void shouldReturn404WhenAtualizarNaoEncontrado() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(contatoService.update(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(Constants.CONTATO_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(put(BASE_URL + "/{id}", pessoaId, id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isNotFound());
    }
}
