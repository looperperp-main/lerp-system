package com.l.erp.cadastroservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.api.controllers.EngagementController;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EngagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class EngagementControllerTest {

    private static final String BASE_URL = "/api/v1/engagement/feature";
    private static final Long TENANT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldRegistrarFeatureEPublicarNoKafka() throws Exception {
        EngagementController.FeatureRequest req = new EngagementController.FeatureRequest("dashboard-view");

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        verify(kafkaTemplate).send(any(), any(), any());
    }

    @Test
    void shouldAceitarSemPublicarQuandoSemTenantHeader() throws Exception {
        EngagementController.FeatureRequest req = new EngagementController.FeatureRequest("dashboard-view");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldRejectFeatureKeyEmBranco() throws Exception {
        EngagementController.FeatureRequest req = new EngagementController.FeatureRequest("");

        mockMvc.perform(post(BASE_URL)
                        .header(Constants.HEADER_TENANT_ID, TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
