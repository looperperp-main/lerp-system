package com.l.erp.authservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E-mails do export (migracao-service): destino sempre o e-mail cadastrado do usuário, link de posse só no cancelamento. */
class EmailConsumerServiceExportTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Long TENANT = 7L;

    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private EmailConsumerService service;
    private MimeMessage mensagem;

    @BeforeEach
    void setUp() {
        service = new EmailConsumerService(mail, new ObjectMapper(), users);
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@syax.com.br");
        ReflectionTestUtils.setField(service, "clientPortalUrl", "https://app.syax.com.br");
        mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(mensagem);
    }

    private UserAccount usuario(String email, String nome) {
        UserAccount u = new UserAccount();
        u.setId(USER);
        u.setEmail(email);
        u.setDisplayName(nome);
        return u;
    }

    private static String evento(String tipo, String extra) {
        return "{\"type\":\"" + tipo + "\",\"userId\":\"" + USER + "\",\"tenantId\":7,\"batchId\":\"" + UUID.randomUUID()
                + "\",\"expiresAt\":\"2026-10-19T13:00:00Z\"" + extra + "}";
    }

    private String corpo() throws Exception {
        mensagem.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mensagem.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void pronto_enviaAoEmailCadastradoComLinkParaATelaLogada() throws Exception {
        when(users.findByIdAndTenantId(USER, TENANT)).thenReturn(Optional.of(usuario("maria@acme.com.br", "Maria")));

        service.consumeMigracaoEmailEvent(evento("EXPORT_PRONTO", ""));

        verify(mail).send(mensagem);
        assertThat(mensagem.getAllRecipients()[0].toString()).isEqualTo("maria@acme.com.br");
        assertThat(mensagem.getSubject()).isEqualTo("Sua exportação de dados está pronta");
        assertThat(corpo()).contains("https://app.syax.com.br/dados/exportar").doesNotContain("/migracao/exports/posse/");
    }

    @Test
    void cancelamento_levaOLinkDePosseEAValidadeNoFusoDeSaoPaulo() throws Exception {
        when(users.findByIdAndTenantId(USER, TENANT)).thenReturn(Optional.of(usuario("maria@acme.com.br", "Maria")));

        service.consumeMigracaoEmailEvent(evento("EXPORT_PRONTO",
                ",\"downloadUrl\":\"https://api.syax.com.br/migracao/exports/posse/TOKEN43\""));

        String texto = corpo();
        assertThat(texto).contains("https://api.syax.com.br/migracao/exports/posse/TOKEN43");
        assertThat(texto).contains("19/10/2026");                      // 13:00Z = 10:00 em São Paulo
    }

    @Test
    void expiraEmBreve_usaAssuntoProprio() throws Exception {
        when(users.findByIdAndTenantId(USER, TENANT)).thenReturn(Optional.of(usuario("maria@acme.com.br", "Maria")));

        service.consumeMigracaoEmailEvent(evento("EXPORT_EXPIRA_EM_BREVE", ""));

        assertThat(mensagem.getSubject()).isEqualTo("Sua exportação de dados expira em breve");
    }

    @Test
    void nomeComHtmlEhEscapadoNoCorpo() throws Exception {
        when(users.findByIdAndTenantId(USER, TENANT)).thenReturn(Optional.of(usuario("x@acme.com.br", "<script>alert(1)</script>")));

        service.consumeMigracaoEmailEvent(evento("EXPORT_PRONTO", ""));

        assertThat(corpo()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void usuarioInexistenteNoTenantDescartaSemEnviar() {
        when(users.findByIdAndTenantId(USER, TENANT)).thenReturn(Optional.empty());

        service.consumeMigracaoEmailEvent(evento("EXPORT_PRONTO", ""));

        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    void tipoDesconhecidoOuPayloadInvalidoNaoLancamNemEnviam() {
        when(users.findByIdAndTenantId(USER, TENANT)).thenReturn(Optional.of(usuario("maria@acme.com.br", "Maria")));

        service.consumeMigracaoEmailEvent(evento("OUTRO_TIPO", ""));
        service.consumeMigracaoEmailEvent("isto nao e json");

        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    void falhaDoSmtpNaoPropagaParaOConsumidorKafka() {
        when(users.findByIdAndTenantId(USER, TENANT)).thenReturn(Optional.of(usuario("maria@acme.com.br", "Maria")));
        doThrow(new MailSendException("smtp fora")).when(mail).send(any(MimeMessage.class));

        service.consumeMigracaoEmailEvent(evento("EXPORT_PRONTO", ""));      // não lança: evita reentrega em loop

        verify(mail).send(mensagem);
    }
}
