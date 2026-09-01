package com.l.erp.fiscalservice.api.controllers;

import com.l.erp.common.exception.handlers.GlobalExceptionHandler;
import com.l.erp.common.util.Constants;
import com.l.erp.fiscalservice.api.dto.OperacaoFiscalDTO;
import com.l.erp.fiscalservice.exception.FiscalException;
import com.l.erp.fiscalservice.services.MotorFiscalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fatia B — expõe {@link MotorFiscalController} sobre HTTP. Verifica o caminho feliz,
 * validação de entrada (400) e o mapeamento de {@link FiscalException} para 400 pelo
 * {@link GlobalExceptionHandler} do common.
 */
@WebMvcTest(controllers = MotorFiscalController.class)
// addFilters=false: o gateway é a barreira e o slice não monta o filter chain corretamente (devolve 500).
// Efeito colateral conhecido: o CorrelationIdFilter do common não roda aqui, então o campo
// correlationId do StandardError não é coberto por este teste — cobri-lo exige teste de integração.
@AutoConfigureMockMvc(addFilters = false)
// @Import explícito: o advice também viria pelo component scan do slice (scanBasePackages=com.l.erp),
// mas declarar aqui mantém o teste independente da configuração de scan da aplicação.
@Import(GlobalExceptionHandler.class)
class MotorFiscalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MotorFiscalService motorFiscal;

    private static final String REQ_JSON = """
            {"cfop":"5101","ncm":"85171231","ibgeDestino":"3550308",
             "valorOperacao":10000.00,"dataCompetencia":"2027-03-15","regimeEmpresa":"LUCRO_REAL"}
            """;

    @Test
    void calcular_operacaoValida_retorna200ComResultado() throws Exception {
        OperacaoFiscalDTO resultado = OperacaoFiscalDTO.builder()
                .baseCalculo(new BigDecimal("10000.00"))
                .valorIbs(new BigDecimal("1762.00"))
                .valorCbs(new BigDecimal("880.00"))
                .regimeAplicado("PADRAO")
                .memoriaCalculo(List.of("CBS: 880.00"))
                .build();
        when(motorFiscal.calcular(any(), any())).thenReturn(resultado);

        mockMvc.perform(post("/fiscal/calcular")
                        .header(Constants.HEADER_TENANT_ID, "tenant-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQ_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regimeAplicado").value("PADRAO"))
                .andExpect(jsonPath("$.memoriaCalculo[0]").value("CBS: 880.00"))
                .andExpect(jsonPath("$.valorSplitIbs").doesNotExist()); // split desligado: campo ausente

        // o tenant do header decide o split payment por tenant
        verify(motorFiscal).calcular(any(), eq("tenant-x"));
    }

    @Test
    void calcular_cfopEmBranco_retorna400() throws Exception {
        String semCfop = REQ_JSON.replace("\"5101\"", "\"\"");
        mockMvc.perform(post("/fiscal/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semCfop))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calcular_corpoMalFormado_retorna400() throws Exception {
        // data em dd/MM/yyyy (formato que um chamador BR manda) → HttpMessageNotReadableException.
        // Sem handler no GlobalExceptionHandler isso caía no catch-all e virava 500.
        String dataBr = REQ_JSON.replace("\"2027-03-15\"", "\"15/03/2027\"");
        mockMvc.perform(post("/fiscal/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dataBr))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calcular_erroFiscal_retorna400ComCodigo() throws Exception {
        when(motorFiscal.calcular(any(), any()))
                .thenThrow(new FiscalException(Constants.FISCAL_CFOP_NAO_ENCONTRADO));

        mockMvc.perform(post("/fiscal/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQ_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Constants.FISCAL_CFOP_NAO_ENCONTRADO));
    }
}
