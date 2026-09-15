package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.controllers.PrecoResolverController;
import com.l.erp.cadastroservice.api.dto.PrecoResolvidoDTO;
import com.l.erp.cadastroservice.domain.enumerators.OrigemPreco;
import com.l.erp.cadastroservice.services.PrecoResolverService;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PrecoResolverController.class)
@AutoConfigureMockMvc(addFilters = false)
class PrecoResolverControllerTest {

    private static final String BASE_URL = "/api/v1/precos/resolver";
    private static final Long TENANT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrecoResolverService service;

    @Test
    void shouldResolverPreco() throws Exception {
        UUID produtoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        PrecoResolvidoDTO resolvido = new PrecoResolvidoDTO(produtoId, clienteId, UUID.randomUUID(), OrigemPreco.PADRAO, BigDecimal.TEN, null);

        when(service.resolver(any(), any(), any(), any())).thenReturn(resolvido);

        mockMvc.perform(get(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .param("produtoId", produtoId.toString())
                        .param("clienteId", clienteId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preco").value(10));
    }

    @Test
    void shouldReturn401WhenResolverSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL).param("produtoId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn400WhenProdutoIdAusente() throws Exception {
        mockMvc.perform(get(BASE_URL).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isBadRequest());
    }
}
