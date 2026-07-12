package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private AuditProducerService auditProducerService;

    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void shouldSerializeAndSendEventToAuditTopic() {
        auditProducerService = new AuditProducerService(kafkaTemplate, objectMapper);

        AuditEventDTO event = new AuditEventDTO(
                "CLIENTE_CREATION", ACTOR_ID, "CLIENTE", UUID.randomUUID(),
                "SUCCESS", null, UUID.randomUUID(), Instant.now()
        );

        when(objectMapper.writeValueAsString(event)).thenReturn("{\"action\":\"CLIENTE_CREATION\"}");

        auditProducerService.sendAuditEvent(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(Constants.AUDIT_TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo(String.valueOf(ACTOR_ID));
        assertThat(payloadCaptor.getValue()).isEqualTo("{\"action\":\"CLIENTE_CREATION\"}");
    }

    @Test
    void shouldUseActorIdAsKafkaMessageKey() {
        auditProducerService = new AuditProducerService(kafkaTemplate, objectMapper);

        UUID differentActor = UUID.randomUUID();
        AuditEventDTO event = new AuditEventDTO(
                "PRODUTO_UPDATE", differentActor, "PRODUTO", null,
                "ERROR", "{\"error\":\"x\"}", UUID.randomUUID(), Instant.now()
        );

        when(objectMapper.writeValueAsString(any(AuditEventDTO.class))).thenReturn("{}");

        auditProducerService.sendAuditEvent(event);

        verify(kafkaTemplate).send(Constants.AUDIT_TOPIC, String.valueOf(differentActor), "{}");
    }
}
