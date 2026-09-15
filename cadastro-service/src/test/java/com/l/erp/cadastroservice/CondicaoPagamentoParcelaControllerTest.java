package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.CondicaoPagamentoParcelaController;
import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaRequestDTO;
import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaResponseDTO;
import com.l.erp.cadastroservice.api.mappers.CondicaoPagamentoParcelaAssembler;
import com.l.erp.cadastroservice.domain.CondicaoPagamentoParcela;
import com.l.erp.cadastroservice.domain.enumerators.FormaPagamento;
import com.l.erp.cadastroservice.services.CondicaoPagamentoParcelaService;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CondicaoPagamentoParcelaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CondicaoPagamentoParcelaControllerTest {

    private static final String BASE_URL = "/api/v1/cond-pagamentos/{condicaoPagamentoId}/parcelas";
    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CondicaoPagamentoParcelaService service;

    @MockitoBean
    private CondicaoPagamentoParcelaAssembler assembler;

    private CondicaoPagamentoParcelaRequestDTO buildDto(UUID condicaoPagamentoId) {
        return new CondicaoPagamentoParcelaRequestDTO(null, condicaoPagamentoId, 1, 30, BigDecimal.valueOf(100), FormaPagamento.BOLETO, null, null, null, null);
    }

    private CondicaoPagamentoParcelaResponseDTO buildResponseDto(UUID condicaoPagamentoId) {
        CondicaoPagamentoParcelaResponseDTO dto = new CondicaoPagamentoParcelaResponseDTO();
        dto.setCondicaoPagamentoId(condicaoPagamentoId);
        dto.setNumeroParcela(1);
        dto.setDiasPrazo(30);
        dto.setPercentual(BigDecimal.valueOf(100));
        dto.setFormaPagamento(FormaPagamento.BOLETO);
        return dto;
    }

    @Test
    void shouldListarParcelas() throws Exception {
        UUID condicaoPagamentoId = UUID.randomUUID();

        when(service.findByCondicaoPagamentoId(condicaoPagamentoId, TENANT_ID)).thenReturn(List.of(new CondicaoPagamentoParcela()));
        when(assembler.toCollectionModel(any())).thenReturn(CollectionModel.of(List.of(buildResponseDto(condicaoPagamentoId))));

        mockMvc.perform(get(BASE_URL, condicaoPagamentoId).header(Constants.HEADER_TENANT_ID, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BOLETO")));
    }

    @Test
    void shouldReturn401WhenListarSemTenantHeader() throws Exception {
        mockMvc.perform(get(BASE_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldSalvarParcelas() throws Exception {
        UUID condicaoPagamentoId = UUID.randomUUID();
        List<CondicaoPagamentoParcelaRequestDTO> dtos = List.of(buildDto(condicaoPagamentoId));

        when(service.saveAll(any(), any(), any(), any())).thenReturn(List.of(new CondicaoPagamentoParcela()));
        when(assembler.toCollectionModel(any())).thenReturn(CollectionModel.of(List.of(buildResponseDto(condicaoPagamentoId))));

        mockMvc.perform(put(BASE_URL, condicaoPagamentoId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtos)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BOLETO")));
    }

    @Test
    void shouldRejectSalvarSemUserIdHeader() throws Exception {
        UUID condicaoPagamentoId = UUID.randomUUID();
        List<CondicaoPagamentoParcelaRequestDTO> dtos = List.of(buildDto(condicaoPagamentoId));

        mockMvc.perform(put(BASE_URL, condicaoPagamentoId)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtos)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSalvarSemTenantHeader() throws Exception {
        UUID condicaoPagamentoId = UUID.randomUUID();
        List<CondicaoPagamentoParcelaRequestDTO> dtos = List.of(buildDto(condicaoPagamentoId));

        mockMvc.perform(put(BASE_URL, condicaoPagamentoId)
                        .header(Constants.HEADER_USER_ID, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtos)))
                .andExpect(status().isUnauthorized());
    }
}
