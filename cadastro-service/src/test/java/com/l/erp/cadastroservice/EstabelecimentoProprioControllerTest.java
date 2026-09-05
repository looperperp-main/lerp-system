package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.controllers.EstabelecimentoProprioController;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.services.EstabelecimentoService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tenant-wide de EstabelecimentoProprioController (Fase 6,
 * spec/estabelecimentos-filiais.md §6.1). Service mockado diretamente.
 */
@WebMvcTest(controllers = EstabelecimentoProprioController.class)
@AutoConfigureMockMvc(addFilters = false)
class EstabelecimentoProprioControllerTest {

    private static final String BASE_URL = "/api/v1/estabelecimentos/proprio";
    private static final Long TENANT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EstabelecimentoService estabelecimentoService;

    @Test
    void shouldBuscarProprio() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        Estabelecimento proprio = new Estabelecimento();
        proprio.setPessoa(pessoa);

        when(estabelecimentoService.buscarProprio(TENANT_ID)).thenReturn(proprio);

        mockMvc.perform(get(BASE_URL).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoaId").value(pessoaId.toString()));
    }

    @Test
    void shouldReturn404WhenProprioNaoEncontrado() throws Exception {
        when(estabelecimentoService.buscarProprio(TENANT_ID))
                .thenThrow(new BusinessException(Constants.ESTABELECIMENTO_PROPRIO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE_URL).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }
}
