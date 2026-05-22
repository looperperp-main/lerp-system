package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.common.util.Constants;
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
    private String fromEmail;

    @Value("${app.client-portal-url}")
    private String clientPortalUrl;

    public EmailConsumerService(JavaMailSender mailSender, ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = EmailNotificationService.EMAIL_TOPIC, groupId = "auth-service-group")
    public void consumeEmailEvent(String payload) {
        logger.debug("Recebido evento de email do Kafka: {}", payload);
        try {
            Map<String, String> data = objectMapper.readValue(payload, new TypeReference<>() {});
            String toEmail = data.get("email");
            String name = data.get("name");
            String type = data.get("type");
            if ("WELCOME".equals(type)) {
                sendWelcomeEmail(toEmail, name);
            } else if ("BOAS_VINDAS_TRIAL".equals(type)) {
                String tenantName = data.get("tenantName");
                String trialExpiresAt = data.get("trialExpiresAt");
                sendBoasVindasTrialEmail(toEmail, name, tenantName, trialExpiresAt);
            } else if ("JA_EXISTE_CONTA".equals(type)) {
                sendJaExisteContaEmail(toEmail);
            }
        } catch (Exception e) {
            logger.error("Falha ao processar ou enviar o e-mail. Payload: {}", payload, e);
        }
    }

    @KafkaListener(topics = "partner.email.notification", groupId = "auth-service-group")
    public void consumePartnerEmailEvent(String payload) {
        logger.debug("Recebido partner.email.notification: {}", payload);
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            String type        = (String) data.get("type");
            String to          = (String) data.get("to");
            String partnerName = (String) data.get("partnerName");
            String clientName  = (String) data.get("clientName");
            String clientCnpj  = (String) data.get("clientCnpj");

            @SuppressWarnings("unchecked")
            Map<String, Object> extra = data.get("extraData") instanceof Map<?,?>
                    ? (Map<String, Object>) data.get("extraData") : java.util.Collections.emptyMap();

            switch (type) {
                case "CLIENTE_ATIVOU"        -> sendClienteAtivouEmail(to, partnerName, clientName, clientCnpj);
                case "TRIAL_EXPIROU"         -> sendTrialExpirouParceiroEmail(to, partnerName, clientName, clientCnpj);
                case "TRIAL_EXPIROU_CLIENTE" -> sendTrialExpirouClienteEmail(to, clientName);
                case "FOLLOWUP_MENSAGEM"     -> sendFollowupMensagemEmail(to, clientName, partnerName, (String) extra.get("message"));
                case "PERDIDO"               -> sendPerdidoEmail(to, partnerName, clientName, clientCnpj);
                case "RELATORIO_D10"         -> sendRelatorioD10Email(to, partnerName, clientName, clientCnpj, extra);
                case "CLIENTE_CONVERTEU"     -> sendClienteConverteuEmail(to, partnerName, clientName, clientCnpj, extra);
                case "TICKET_INTERNO_SYAX"   -> sendTicketInternoSyaxEmail(to, clientName, clientCnpj, extra);
                default -> logger.warn("Tipo de e-mail desconhecido: {}", type);
            }
        } catch (Exception e) {
            logger.error("Falha ao processar partner.email.notification. Payload: {}", payload, e);
        }
    }

    public void sendPartnerWelcomeEmail(String name, String email, String referralCode, String tempPassword) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, Constants.UTF8);

            helper.setFrom(fromEmail, Constants.EQ_ERP);
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
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, Constants.UTF8);

            helper.setFrom(fromEmail, Constants.EQ_ERP);
            helper.setTo(clientEmail);
            helper.setSubject(partnerName + " te convidou para o ERP — Ative sua conta");

            String prazo = tokenExpiresAt.toLocalDate().toString();
            String activationLink = clientPortalUrl + "/ativar?token=" + activationToken;

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

    private void sendClienteAtivouEmail(String to, String partnerName, String clientName, String clientCnpj) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, Constants.EQ_ERP);
            h.setTo(to);
            h.setSubject("Cliente " + clientName + " ativou a conta — acompanhe o engajamento");
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:20px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#0056b3'>Olá, %s!</h2>
                    <p>Boa notícia! O cliente <strong>%s</strong> (CNPJ: %s) ativou a conta e iniciou o período de trial de 15 dias.</p>
                    <p>Acesse o portal do parceiro para acompanhar o engajamento e garantir a conversão.</p>
                    <p>Atenciosamente,<br><strong>Equipe ERP</strong></p>
                    </div></body></html>
                    """, partnerName, clientName, clientCnpj), true);
            mailSender.send(msg);
        } catch (Exception e) {
            logger.error("Erro ao enviar CLIENTE_ATIVOU para {}", to, e);
        }
    }

    private void sendTrialExpirouParceiroEmail(String to, String partnerName, String clientName, String clientCnpj) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, Constants.EQ_ERP);
            h.setTo(to);
            h.setSubject("Trial de " + clientName + " expirou — inicie o follow-up");
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:20px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#0056b3'>Olá, %s!</h2>
                    <p>O período de trial do cliente <strong>%s</strong> (CNPJ: %s) encerrou.</p>
                    <p>Este é o momento de entrar em contato e apresentar as vantagens de continuar com o plano. Você tem até <strong>3 tentativas de follow-up</strong> registradas no sistema.</p>
                    <p>Acesse o portal e inicie o follow-up agora.</p>
                    <p>Atenciosamente,<br><strong>Equipe ERP</strong></p>
                    </div></body></html>
                    """, partnerName, clientName, clientCnpj), true);
            mailSender.send(msg);
        } catch (Exception e) {
            logger.error("Erro ao enviar TRIAL_EXPIROU ao parceiro {}", to, e);
        }
    }

    private void sendTrialExpirouClienteEmail(String to, String clientName) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, Constants.EQ_ERP);
            h.setTo(to);
            h.setSubject("Seu período gratuito terminou — escolha um plano");
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:20px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#0056b3'>Olá, %s!</h2>
                    <p>Seu período gratuito de 15 dias chegou ao fim. Esperamos que tenha aproveitado o sistema!</p>
                    <p>Para continuar acessando o ERP, escolha o plano ideal para sua empresa:</p>
                    <div style='text-align:center;margin:30px 0'>
                      <a href='%s/planos' style='background:#f97316;color:#fff;padding:14px 28px;border-radius:6px;text-decoration:none;font-size:16px;font-weight:bold'>
                        Escolher meu plano
                      </a>
                    </div>
                    <p>Seu contador também pode te ajudar nessa escolha.</p>
                    <p>Atenciosamente,<br><strong>Equipe ERP</strong></p>
                    </div></body></html>
                    """, clientName), true);
            mailSender.send(msg);
        } catch (Exception e) {
            logger.error("Erro ao enviar TRIAL_EXPIROU ao cliente {}", to, e);
        }
    }

    private void sendFollowupMensagemEmail(String to, String clientName, String partnerName, String message) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, Constants.EQ_ERP);
            h.setTo(to);
            h.setSubject("Mensagem do seu contador — " + partnerName);
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:20px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#0056b3'>Olá, %s!</h2>
                    <p>Seu contador <strong>%s</strong> enviou uma mensagem:</p>
                    <blockquote style='border-left:4px solid #0056b3;padding:10px 20px;margin:20px 0;background:#f9f9f9'>%s</blockquote>
                    <p>Atenciosamente,<br><strong>Equipe ERP</strong></p>
                    </div></body></html>
                    """, clientName, partnerName, message), true);
            mailSender.send(msg);
        } catch (Exception e) {
            logger.error("Erro ao enviar FOLLOWUP_MENSAGEM para {}", to, e);
        }
    }

    private void sendPerdidoEmail(String to, String partnerName, String clientName, String clientCnpj) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, Constants.EQ_ERP);
            h.setTo(to);
            h.setSubject(clientName + " marcado como PERDIDO após 3 tentativas");
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:20px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#c00'>Olá, %s!</h2>
                    <p>Após 3 tentativas de follow-up sem conversão, o cliente <strong>%s</strong> (CNPJ: %s) foi marcado como <strong>PERDIDO</strong> no sistema.</p>
                    <p>Acesse o portal para ver o histórico completo.</p>
                    <p>Atenciosamente,<br><strong>Equipe ERP</strong></p>
                    </div></body></html>
                    """, partnerName, clientName, clientCnpj), true);
            mailSender.send(msg);
        } catch (Exception e) {
            logger.error("Erro ao enviar PERDIDO para {}", to, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendRelatorioD10Email(String to, String partnerName, String clientName,
                                       String clientCnpj, Map<String, Object> extra) {
        try {
            int loginCount = extra.get("loginCount") instanceof Number n ? n.intValue() : 0;
            java.util.List<String> features = extra.get("features") instanceof java.util.List<?> f
                    ? (java.util.List<String>) f : java.util.Collections.emptyList();
            java.util.List<String> gaps = extra.get("gaps") instanceof java.util.List<?> g
                    ? (java.util.List<String>) g : java.util.Collections.emptyList();

            StringBuilder featuresHtml = new StringBuilder();
            features.forEach(f -> featuresHtml.append("<li>").append(f).append("</li>"));

            StringBuilder gapsHtml = new StringBuilder();
            gaps.forEach(g -> gapsHtml.append("<li style='color:#c00'>").append(g).append(" — nunca acessado</li>"));

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, Constants.EQ_ERP);
            h.setTo(to);
            h.setSubject("Relatório D+10 — Engajamento de " + clientName);
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:20px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#0056b3'>Relatório D+10 — %s</h2>
                    <p>Olá, %s! Aqui está o relatório de engajamento de <strong>%s</strong> (CNPJ: %s) após 10 dias de trial.</p>
                    <p><strong>Logins realizados:</strong> %d</p>
                    <h3>Features acessadas:</h3><ul>%s</ul>
                    <h3 style='color:#c00'>GAPs de adoção:</h3><ul>%s</ul>
                    <p>Use essas informações para guiar o follow-up e aumentar a chance de conversão.</p>
                    <p>Atenciosamente,<br><strong>Equipe ERP</strong></p>
                    </div></body></html>
                    """, clientName, partnerName, clientName, clientCnpj, loginCount,
                    featuresHtml.toString(), gapsHtml.toString()), true);
            mailSender.send(msg);
        } catch (Exception e) {
            logger.error("Erro ao enviar RELATORIO_D10 para {}", to, e);
        }
    }

    private void sendClienteConverteuEmail(String to, String partnerName, String clientName,
                                            String clientCnpj, Map<String, Object> extra) {
        try {
            String planType = extra.getOrDefault("planType", "").toString();
            String planValue = extra.getOrDefault("planValue", "").toString();
            String commission = extra.getOrDefault("commissionPreview", "").toString();

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, Constants.EQ_ERP);
            h.setTo(to);
            h.setSubject("🎉 " + clientName + " assinou um plano!");
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:20px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#16a34a'>Parabéns, %s!</h2>
                    <p>O cliente <strong>%s</strong> (CNPJ: %s) acabou de assinar o plano <strong>%s</strong>.</p>
                    <table style='border-collapse:collapse;width:100%%'>
                      <tr><td style='padding:8px 0;color:#555'><strong>Valor do plano:</strong></td>
                          <td style='padding:8px 0'>R$ %s/mês</td></tr>
                      <tr><td style='padding:8px 0;color:#555'><strong>Comissão estimada:</strong></td>
                          <td style='padding:8px 0;font-size:18px;font-weight:bold;color:#16a34a'>R$ %s</td></tr>
                    </table>
                    <p style='font-size:12px;color:#6b7280'>A comissão será processada após a confirmação do pagamento pelo Asaas.</p>
                    <p>Atenciosamente,<br><strong>Equipe ERP</strong></p>
                    </div></body></html>
                    """, partnerName, clientName, clientCnpj, planType, planValue, commission), true);
            mailSender.send(msg);
            logger.info("E-mail CLIENTE_CONVERTEU enviado para {}", to);
        } catch (Exception e) {
            logger.error("Erro ao enviar CLIENTE_CONVERTEU para {}", to, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendTicketInternoSyaxEmail(String to, String clientName, String clientCnpj, Map<String, Object> extra) {
        try {
            String ticketId   = (String) extra.getOrDefault("ticketId", "?");
            String ticketTipo = (String) extra.getOrDefault("ticketTipo", "?");
            String adminUrl   = (String) extra.getOrDefault("adminUrl", "");
            int loginCount    = extra.get("loginCount") instanceof Number n ? n.intValue() : 0;

            java.util.List<String> features = extra.get("features") instanceof java.util.List<?> f
                    ? (java.util.List<String>) f : java.util.Collections.emptyList();
            java.util.List<String> gaps = extra.get("gaps") instanceof java.util.List<?> g
                    ? (java.util.List<String>) g : java.util.Collections.emptyList();

            String tipoLabel = "RELATORIO_D10".equals(ticketTipo) ? "Relatório D+10" : "Trial Expirado";

            StringBuilder featHtml = new StringBuilder();
            features.forEach(f -> featHtml.append("<li>").append(f).append("</li>"));

            StringBuilder gapsHtml = new StringBuilder();
            gaps.forEach(g -> gapsHtml.append("<li style='color:#c00'>").append(g).append(" — nunca acessado</li>"));

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(fromEmail, "Syax — Fila Interna");
            h.setTo(to);
            h.setSubject("[Fila Interna] " + tipoLabel + " — " + clientName);
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6'>
                    <div style='max-width:600px;margin:0 auto;padding:24px;border:1px solid #ddd;border-radius:8px'>
                    <h2 style='color:#0056b3'>%s — Ticket #%s</h2>
                    <table style='border-collapse:collapse;width:100%%'>
                      <tr><td style='padding:6px 0;color:#555'><strong>Empresa:</strong></td><td>%s</td></tr>
                      <tr><td style='padding:6px 0;color:#555'><strong>CNPJ:</strong></td><td>%s</td></tr>
                      <tr><td style='padding:6px 0;color:#555'><strong>Logins:</strong></td><td>%d</td></tr>
                    </table>
                    %s
                    %s
                    <div style='text-align:center;margin:24px 0'>
                      <a href='%s' style='background:#0056b3;color:#fff;padding:12px 28px;border-radius:6px;text-decoration:none;font-size:15px'>
                        Abrir Fila Interna
                      </a>
                    </div>
                    </div></body></html>
                    """,
                    tipoLabel, ticketId, clientName, clientCnpj, loginCount,
                    features.isEmpty() ? "" : "<h3>Features acessadas:</h3><ul>" + featHtml + "</ul>",
                    gaps.isEmpty() ? "" : "<h3 style='color:#c00'>GAPs de adoção:</h3><ul>" + gapsHtml + "</ul>",
                    adminUrl), true);
            mailSender.send(msg);
            logger.info("TICKET_INTERNO_SYAX enviado para {} — ticket #{}", to, ticketId);
        } catch (Exception e) {
            logger.error("Erro ao enviar TICKET_INTERNO_SYAX para {}", to, e);
        }
    }

    private void sendBoasVindasTrialEmail(String to, String name, String tenantName, String trialExpiresAt) {
        try {
            String expiry = trialExpiresAt != null
                    ? java.time.Instant.parse(trialExpiresAt)
                            .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                            .toLocalDate().toString()
                    : "15 dias";

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, Constants.UTF8);
            h.setFrom(fromEmail, "Equipe Syax");
            h.setTo(to);
            h.setSubject("Bem-vindo ao Syax! Seu trial de 15 dias começou");
            h.setText(String.format("""
                    <html><body style='font-family:Arial,sans-serif;color:#333;line-height:1.6;margin:0;padding:0'>
                    <div style='max-width:600px;margin:0 auto;padding:32px 20px'>
                      <h2 style='color:#0056b3;margin-bottom:4px'>Olá, %s!</h2>
                      <p style='color:#555;margin-top:0'>Conta da empresa <strong>%s</strong> criada com sucesso.</p>
                      <div style='background:#f0f7ff;border-left:4px solid #0056b3;padding:14px 18px;border-radius:0 6px 6px 0;margin:24px 0'>
                        <strong style='color:#0056b3'>Trial gratuito ativo até %s</strong><br/>
                        <span style='font-size:13px;color:#555'>Sem cartão de crédito. Cancele a qualquer momento.</span>
                      </div>
                      <h3 style='color:#0056b3'>Primeiros passos</h3>
                      <ol style='padding-left:20px;color:#444'>
                        <li style='margin-bottom:8px'>Acesse o sistema com seu e-mail e CNPJ da empresa</li>
                        <li style='margin-bottom:8px'>Cadastre seus primeiros <strong>clientes e fornecedores</strong></li>
                        <li style='margin-bottom:8px'>Adicione seus <strong>produtos</strong> e configure o estoque</li>
                        <li style='margin-bottom:8px'>Emita sua primeira <strong>NF-e</strong></li>
                        <li style='margin-bottom:8px'>Convide sua equipe com <strong>controle de permissões</strong></li>
                      </ol>
                      <h3 style='color:#0056b3'>O que está incluso no trial</h3>
                      <ul style='padding-left:20px;color:#444'>
                        <li style='margin-bottom:6px'>Clientes, fornecedores e contatos</li>
                        <li style='margin-bottom:6px'>Produtos, categorias e controle de estoque</li>
                        <li style='margin-bottom:6px'>Emissão de NF-e</li>
                        <li style='margin-bottom:6px'>Multi-usuário com controle de acesso</li>
                        <li style='margin-bottom:6px'>Suporte por e-mail durante todo o período</li>
                      </ul>
                      <div style='text-align:center;margin:32px 0'>
                        <a href='%s' style='background:#0056b3;color:#fff;padding:14px 32px;border-radius:6px;text-decoration:none;font-size:16px;font-weight:bold'>
                          Acessar o sistema
                        </a>
                      </div>
                      <hr style='border:none;border-top:1px solid #eee;margin:24px 0'/>
                      <p style='font-size:13px;color:#888'>Precisa de ajuda? Responda este e-mail ou acesse nossa central de suporte.<br/>
                      Atenciosamente,<br/><strong>Equipe Syax</strong></p>
                    </div></body></html>
                    """, name, tenantName, expiry, clientPortalUrl), true);
            mailSender.send(msg);
            logger.info("E-mail BOAS_VINDAS_TRIAL enviado para {}", to);
        } catch (Exception e) {
            logger.error("Erro ao enviar BOAS_VINDAS_TRIAL para {}", to, e);
        }
    }

    private void sendWelcomeEmail(String to, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // O 'true' indica que é um e-mail multipart/html (suporta anexos e HTML)
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, Constants.UTF8);

            // Formata o remetente para "Nome Amigável <seu.email@dominio.com>"
            helper.setFrom(fromEmail, Constants.EQ_ERP);
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

    private void sendJaExisteContaEmail(String to) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, Constants.UTF8);
            helper.setFrom(fromEmail, Constants.EQ_ERP);
            helper.setTo(to);
            helper.setSubject("Tentativa de cadastro detectada");
            String loginUrl = clientPortalUrl + "/login";
            String htmlBody = String.format("""
                    <html><body style='font-family: Arial, sans-serif; color: #333; line-height: 1.6;'>
                      <div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 8px;'>
                        <h2 style='color: #0056b3;'>Tentativa de cadastro</h2>
                        <p>Identificamos uma tentativa de cadastro com este e-mail em nossa plataforma.</p>
                        <p>Você já possui uma conta. Para acessar, clique no botão abaixo:</p>
                        <p style='text-align: center; margin: 30px 0;'>
                          <a href='%s' style='background:#0056b3;color:#fff;padding:12px 24px;border-radius:4px;text-decoration:none;font-weight:bold;'>
                            Fazer login
                          </a>
                        </p>
                        <p>Se não foi você quem tentou se cadastrar, ignore este e-mail.</p>
                        <p>Atenciosamente,<br/><strong>Equipe Syax</strong></p>
                      </div>
                    </body></html>
                    """, loginUrl);
            helper.setText(htmlBody, true);
            mailSender.send(mimeMessage);
            logger.info("E-mail JA_EXISTE_CONTA enviado para {}", to);
        } catch (Exception e) {
            logger.error("Erro ao enviar JA_EXISTE_CONTA para {}", to, e);
        }
    }
}
