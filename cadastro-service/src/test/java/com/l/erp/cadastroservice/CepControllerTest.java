package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.controllers.CepController;
import com.l.erp.cadastroservice.api.dto.ViaCepResponseDTO;
import com.l.erp.cadastroservice.services.CepService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CepController.class)
@AutoConfigureMockMvc(addFilters = false)
class CepControllerTest {

    private static final String BASE_URL = "/api/v1/cep/{cep}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CepService cepService;

    @Test
    void shouldBuscarCepComSucesso() throws Exception {
        ViaCepResponseDTO response = new ViaCepResponseDTO();
        response.setCep("01310-100");
        response.setLogradouro("Avenida Paulista");
        response.setLocalidade("São Paulo");
        response.setUf("SP");

        when(cepService.buscarCep("01310100")).thenReturn(response);

        mockMvc.perform(get(BASE_URL, "01310100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logradouro").value("Avenida Paulista"));
    }

    @Test
    void shouldReturn404WhenCepNaoEncontrado() throws Exception {
        when(cepService.buscarCep("00000000")).thenReturn(new ViaCepResponseDTO());

        mockMvc.perform(get(BASE_URL, "00000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenServicoExternoFalha() throws Exception {
        when(cepService.buscarCep("01310100")).thenThrow(new RuntimeException("timeout"));

        mockMvc.perform(get(BASE_URL, "01310100"))
                .andExpect(status().isBadRequest());
    }
}
