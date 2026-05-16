package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
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

    public void sendPartnerWelcomeEmail(String name, String email, String referralCode, String tempPassword) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, "Equipe ERP");
            helper.setTo(email);
            helper.setSubject("Bem-vindo como Parceiro! Suas credenciais de acesso");

            String htmlBody = String.format(
                    "<html>" +
                    "<body style='font-family: Arial, sans-serif; color: #333; line-height: 1.6;'>" +
                    "  <div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 8px;'>" +
                    "    <h2 style='color: #0056b3;'>Olá, %s!</h2>" +
                    "    <p>Sua solicitação de parceria foi <strong>aprovada</strong>! Seja muito bem-vindo(a) à rede de parceiros do ERP.</p>" +
                    "    <hr style='border: none; border-top: 1px solid #eee;'/>" +
                    "    <h3 style='color: #0056b3;'>Suas credenciais de acesso</h3>" +
                    "    <table style='border-collapse: collapse; width: 100%%;'>" +
                    "      <tr><td style='padding: 8px 0; color: #555;'><strong>Login (e-mail):</strong></td>" +
                    "          <td style='padding: 8px 0;'>%s</td></tr>" +
                    "      <tr><td style='padding: 8px 0; color: #555;'><strong>Senha temporária:</strong></td>" +
                    "          <td style='padding: 8px 0;'><code style='background:#f4f4f4; padding: 3px 8px; border-radius: 4px; font-size: 15px;'>%s</code></td></tr>" +
                    "      <tr><td style='padding: 8px 0; color: #555;'><strong>Código de parceiro:</strong></td>" +
                    "          <td style='padding: 8px 0;'><span style='font-size: 18px; font-weight: bold; color: #0056b3;'>%s</span></td></tr>" +
                    "    </table>" +
                    "    <hr style='border: none; border-top: 1px solid #eee;'/>" +
                    "    <p style='color: #c00; font-size: 13px;'>&#128274; Por segurança, altere sua senha no primeiro acesso.</p>" +
                    "    <br/>" +
                    "    <p>Atenciosamente,<br/><strong>Equipe ERP</strong></p>" +
                    "  </div>" +
                    "</body>" +
                    "</html>", name, email, tempPassword, referralCode);

            helper.setText(htmlBody, true);
            mailSender.send(mimeMessage);
            logger.info("E-mail de boas-vindas de parceiro enviado para: {}", email);
        } catch (Exception e) {
            logger.error("Erro ao enviar e-mail de boas-vindas de parceiro para: {}", email, e);
        }
    }

    public void sendClientInviteEmail(String clientName, String clientEmail,
                                      String partnerName, String activationToken,
                                      java.time.OffsetDateTime tokenExpiresAt) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, "Equipe ERP");
            helper.setTo(clientEmail);
            helper.setSubject(partnerName + " te convidou para o ERP — Ative sua conta");

            String prazo = tokenExpiresAt.toLocalDate().toString();
            String activationLink = "https://app.erp.com/ativar?token=" + activationToken;

            String htmlBody = String.format(
                    "<html>" +
                    "<body style='font-family: Arial, sans-serif; color: #333; line-height: 1.6;'>" +
                    "  <div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 8px;'>" +
                    "    <h2 style='color: #0056b3;'>Olá, %s!</h2>" +
                    "    <p>O escritório <strong>%s</strong> te convidou para gerenciar sua empresa no ERP.</p>" +
                    "    <p>Clique no botão abaixo para criar sua senha e ativar sua conta:</p>" +
                    "    <div style='text-align: center; margin: 30px 0;'>" +
                    "      <a href='%s' style='background: #0056b3; color: #fff; padding: 14px 28px; border-radius: 6px; text-decoration: none; font-size: 16px;'>" +
                    "        Ativar minha conta" +
                    "      </a>" +
                    "    </div>" +
                    "    <p style='color: #c00; font-size: 13px;'>&#128274; Este link é válido até %s. Após essa data será necessário solicitar um novo convite.</p>" +
                    "    <hr style='border: none; border-top: 1px solid #eee;'/>" +
                    "    <p>Atenciosamente,<br/><strong>Equipe ERP</strong></p>" +
                    "  </div>" +
                    "</body>" +
                    "</html>", clientName, partnerName, activationLink, prazo);

            helper.setText(htmlBody, true);
            mailSender.send(mimeMessage);
            logger.info("E-mail de convite enviado para: {}", clientEmail);
        } catch (Exception e) {
            logger.error("Erro ao enviar e-mail de convite para: {}", clientEmail, e);
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
