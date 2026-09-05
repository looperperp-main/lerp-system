package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.EstabelecimentoController;
import com.l.erp.cadastroservice.api.dto.EstabelecimentoRequestDTO;
import com.l.erp.cadastroservice.api.dto.EstabelecimentoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.EstabelecimentoAssembler;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.services.EstabelecimentoService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD de filiais via EstabelecimentoController. Service e Assembler mockados
 * diretamente (mesmas dependências do controller) — sem repositórios reais.
 */
@WebMvcTest(controllers = EstabelecimentoController.class)
@AutoConfigureMockMvc(addFilters = false)
class EstabelecimentoControllerTest {

    private static final String BASE_URL = "/api/v1/pessoas/{pessoaId}/estabelecimentos";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EstabelecimentoService estabelecimentoService;

    @MockitoBean
    private EstabelecimentoAssembler assembler;

    private EstabelecimentoRequestDTO buildDto() {
        return new EstabelecimentoRequestDTO("12345678000276", "IE123", "IM456", true);
    }

    private EstabelecimentoResponseDTO buildResponseDto(UUID pessoaId, UUID id) {
        EstabelecimentoResponseDTO dto = new EstabelecimentoResponseDTO();
        dto.setId(id);
        dto.setTenantId(TENANT_ID);
        dto.setCnpjCompleto("12345678000276");
        dto.setOrdem("0002");
        dto.setMatriz(false);
        dto.setProprio(false);
        dto.add(Link.of("http://localhost" + BASE_URL.replace("{pessoaId}", pessoaId.toString()) + "/" + id));
        return dto;
    }

    @Test
    void shouldListarEstabelecimentosPorPessoa() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(estabelecimentoService.findAllByPessoa(pessoaId, TENANT_ID)).thenReturn(List.of(new Estabelecimento()));
        when(assembler.toCollectionModel(any())).thenReturn(CollectionModel.of(List.of(buildResponseDto(pessoaId, id))));

        mockMvc.perform(get(BASE_URL, pessoaId).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12345678000276")));
    }

    @Test
    void shouldRejectListarSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFindEstabelecimentoById() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(estabelecimentoService.findById(id, pessoaId, TENANT_ID)).thenReturn(new Estabelecimento());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(get(BASE_URL + "/{id}", pessoaId, id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpjCompleto").value("12345678000276"));
    }

    @Test
    void shouldReturn404WhenEstabelecimentoNaoEncontrado() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(estabelecimentoService.findById(id, pessoaId, TENANT_ID))
                .thenThrow(new BusinessException(Constants.ESTABELECIMENTO_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/{id}", pessoaId, id).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCriarEstabelecimento() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        EstabelecimentoRequestDTO dto = buildDto();

        when(estabelecimentoService.create(any(), any(), any(), any())).thenReturn(new Estabelecimento());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(post(BASE_URL, pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(id.toString())))
                .andExpect(jsonPath("$.cnpjCompleto").value("12345678000276"));
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
    void shouldRejectCriarComCnpjBlank() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        EstabelecimentoRequestDTO dto = new EstabelecimentoRequestDTO("", null, null, null);

        mockMvc.perform(post(BASE_URL, pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAtualizarEstabelecimento() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        EstabelecimentoRequestDTO dto = buildDto();

        when(estabelecimentoService.update(any(), any(), any(), any(), any())).thenReturn(new Estabelecimento());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(put(BASE_URL + "/{id}", pessoaId, id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpjCompleto").value("12345678000276"));
    }

    @Test
    void shouldMarcarProprio() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(estabelecimentoService.marcarProprio(pessoaId, TENANT_ID, USER_ID)).thenReturn(new Estabelecimento());
        when(assembler.toModel(any())).thenReturn(buildResponseDto(pessoaId, id));

        mockMvc.perform(patch(BASE_URL + "/matriz/proprio", pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpjCompleto").value("12345678000276"));
    }

    @Test
    void shouldRejectMarcarProprioWhenJaDefinido() throws Exception {
        UUID pessoaId = UUID.randomUUID();

        when(estabelecimentoService.marcarProprio(pessoaId, TENANT_ID, USER_ID))
                .thenThrow(new BusinessException(Constants.ESTABELECIMENTO_PROPRIO_JA_DEFINIDO, HttpStatus.CONFLICT));

        mockMvc.perform(patch(BASE_URL + "/matriz/proprio", pessoaId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn404WhenAtualizarNaoEncontrado() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        when(estabelecimentoService.update(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(Constants.ESTABELECIMENTO_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(put(BASE_URL + "/{id}", pessoaId, id)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto())))
                .andExpect(status().isNotFound());
    }
}
