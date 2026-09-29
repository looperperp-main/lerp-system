package com.l.erp.authservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alerta operacional do emissao-fiscal-service (documento em ERRO): o e-mail tem que chegar ao
 * destinatário do evento com prioridade máxima e sem HTML injetado pelo motivo/dados do documento.
 */
@ExtendWith(MockitoExtension.class)
class EmailConsumerServiceEmissaoAlertaTest {

    @Mock
    private JavaMailSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EmailConsumerService service;

    @BeforeEach
    void setUp() {
        service = new EmailConsumerService(mailSender, objectMapper);
        ReflectionTestUtils.setField(service, "fromEmail", "remetente@exemplo.com");
        ReflectionTestUtils.setField(service, "clientPortalUrl", "http://localhost:4200");
    }

    @Test
    void enviaParaODestinatarioDoEventoComPrioridadeMaxima() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(novaMensagem());

        service.consumeEmissaoAlertaOperacional(payload("Certificado vencido"));

        ArgumentCaptor<MimeMessage> enviada = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviada.capture());
        MimeMessage m = enviada.getValue();
        assertEquals("responsavel@exemplo.com", m.getAllRecipients()[0].toString());
        assertTrue(m.getSubject().contains("PRIORIDADE MÁXIMA"));
        assertTrue(m.getSubject().contains("NFE nº 42"));
        assertArrayEquals(new String[]{"High"}, m.getHeader("Importance"));
        assertArrayEquals(new String[]{"1 (Highest)"}, m.getHeader("X-Priority"));
        assertTrue(html(m).contains("Certificado vencido"));
    }

    @Test
    void escapaHtmlDoMotivoParaNaoInjetarMarcacaoNoEmail() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(novaMensagem());

        service.consumeEmissaoAlertaOperacional(payload("<script>alert(1)</script>"));

        ArgumentCaptor<MimeMessage> enviada = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviada.capture());
        String corpo = html(enviada.getValue());
        assertFalse(corpo.contains("<script>"), "o motivo não pode virar tag no e-mail");
        assertTrue(corpo.contains("&lt;script&gt;"));
    }

    @Test
    void tipoDeAlertaDesconhecidoNaoEnviaEmail() throws Exception {
        service.consumeEmissaoAlertaOperacional(objectMapper.writeValueAsString(Map.of("type", "OUTRO", "to", "x@y.com")));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void payloadInvalidoNaoQuebraOConsumidor() {
        service.consumeEmissaoAlertaOperacional("isto não é json");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ---- helpers ----

    private String payload(String motivo) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", Constants.EMISSAO_ALERTA_TIPO_DOCUMENTO_EM_ERRO,
                "to", "responsavel@exemplo.com",
                "documentoId", "11111111-1111-1111-1111-111111111111",
                "tenantId", 9,
                "emitenteId", "22222222-2222-2222-2222-222222222222",
                "documento", "NFE",
                "serie", "1",
                "numero", 42,
                "motivo", motivo,
                "tentativas", 1));
    }

    private static MimeMessage novaMensagem() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    /**
     * O {@code Content-Type} multipart só é gravado no cabeçalho por {@code saveChanges()}, que o
     * JavaMailSender real chama ao enviar; como o {@code send} aqui é mock, chamamos na mão.
     */
    private static String html(MimeMessage mensagem) throws Exception {
        mensagem.saveChanges();
        return html((Part) mensagem);
    }

    /** Percorre a árvore MIME (multipart aninhado do MimeMessageHelper) atrás do corpo text/html. */
    private static String html(Part parte) throws Exception {
        if (parte.isMimeType("text/html")) {
            return (String) parte.getContent();
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) parte.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart filho = multipart.getBodyPart(i);
                String achado = html(filho);
                if (achado != null) {
                    return achado;
                }
            }
        }
        return null;
    }
}
