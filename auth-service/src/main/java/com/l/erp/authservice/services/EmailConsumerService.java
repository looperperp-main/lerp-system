package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(EmailConsumerService.class);

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    // Coloque aqui o e-mail configurado no seu application.yml (spring.mail.username)
    @Value("${spring.mail.username}")
    private String FROM_EMAIL;

    public EmailConsumerService(JavaMailSender mailSender, ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = EmailNotificationService.EMAIL_TOPIC, groupId = "auth-service-group")
    public void consumeEmailEvent(String payload) {
        logger.debug("Recebido evento de email do Kafka: {}", payload);

        try {
            // Desserializa a string JSON recebida do Kafka
            Map<String, String> data = objectMapper.readValue(payload, new TypeReference<>() {});

            String toEmail = data.get("email");
            String name = data.get("name");
            String type = data.get("type");

            if ("WELCOME".equals(type)) {
                sendWelcomeEmail(toEmail, name);
            }

        } catch (Exception e) {
            // Em um cenário real, você poderia jogar para uma DLQ (Dead Letter Queue) do Kafka
            logger.error("Falha ao processar ou enviar o e-mail. Payload: {}", payload, e);
        }
    }

    private void sendWelcomeEmail(String to, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // O 'true' indica que é um e-mail multipart/html (suporta anexos e HTML)
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // Formata o remetente para "Nome Amigável <seu.email@dominio.com>"
            helper.setFrom(FROM_EMAIL, "Equipe ERP");
            helper.setTo(to);
            helper.setSubject("Bem-vindo ao nosso ERP!");

            // Corpo do e-mail em HTML simples (ajuda na reputação e estética)
            String htmlBody = String.format(
                    "<html>" +
                            "<body style='font-family: Arial, sans-serif; color: #333; line-height: 1.6;'>" +
                            "   <div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 8px;'>" +
                            "       <h2 style='color: #0056b3;'>Olá, %s!</h2>" +
                            "       <p>Sua conta foi criada com sucesso.</p>" +
                            "       <p>Seja muito bem-vindo ao sistema ERP. Estamos felizes em tê-lo conosco!</p>" +
                            "       <br/>" +
                            "       <p>Atenciosamente,<br/><strong>Equipe ERP</strong></p>" +
                            "   </div>" +
                            "</body>" +
                            "</html>", name);

            // O segundo parâmetro 'true' informa que o conteúdo é HTML
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            logger.info("E-mail HTML de boas-vindas enviado com sucesso para: {}", to);

        } catch (Exception e) {
            logger.error("Erro ao tentar enviar o e-mail HTML de boas-vindas para: {}", to, e);
        }
    }
}
