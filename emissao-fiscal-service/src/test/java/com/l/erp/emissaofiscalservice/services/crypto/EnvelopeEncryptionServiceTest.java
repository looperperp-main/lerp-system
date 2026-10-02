package com.l.erp.emissaofiscalservice.services.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cobre o round-trip do envelope encryption (spec §3 item 1) e a validação da KEK no startup. */
class EnvelopeEncryptionServiceTest {

    private EnvelopeEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new EnvelopeEncryptionService();
        byte[] kek = new byte[32];
        new SecureRandom().nextBytes(kek);
        ReflectionTestUtils.setField(service, "kekBase64", Base64.getEncoder().encodeToString(kek));
        service.validarKek();
    }

    @Test
    void cifraEDecifraDevolveOTextoOriginal() {
        byte[] original = "certificado-de-teste-.pfx-bytes".getBytes(StandardCharsets.UTF_8);

        byte[] cifrado = service.cifrar(original);
        byte[] decifrado = service.decifrar(cifrado);

        assertThat(decifrado).isEqualTo(original);
        assertThat(cifrado).isNotEqualTo(original);
    }

    @Test
    void duasCifragensDoMesmoTextoGeramBlobsDiferentes() {
        byte[] original = "mesmo-conteudo".getBytes(StandardCharsets.UTF_8);

        byte[] cifrado1 = service.cifrar(original);
        byte[] cifrado2 = service.cifrar(original);

        assertThat(cifrado1).isNotEqualTo(cifrado2); // nonce novo a cada cifragem (GCM)
    }

    @Test
    void kekComTamanhoErradoFalhaNoStartup() {
        EnvelopeEncryptionService servicoComKekInvalida = new EnvelopeEncryptionService();
        ReflectionTestUtils.setField(servicoComKekInvalida, "kekBase64",
                Base64.getEncoder().encodeToString("chave-curta-demais".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(servicoComKekInvalida::validarKek).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void v2DecifraParaOMesmoTenantEEmitente() {
        UUID emitente = UUID.randomUUID();
        byte[] original = "pfx".getBytes(StandardCharsets.UTF_8);

        byte[] cifrado = service.cifrar(original, 5L, emitente);

        assertThat(service.decifrar(cifrado, EnvelopeEncryptionService.KEK_VERSION_COM_AAD, 5L, emitente))
                .isEqualTo(original);
    }

    @Test
    void v2NaoDecifraParaOutroTenantMesmoComAMesmaKek() {
        UUID emitente = UUID.randomUUID();
        byte[] cifrado = service.cifrar("pfx".getBytes(StandardCharsets.UTF_8), 5L, emitente);

        assertThatThrownBy(() -> service.decifrar(cifrado, EnvelopeEncryptionService.KEK_VERSION_COM_AAD, 99L, emitente))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void v2NaoDecifraParaOutroEmitenteDoMesmoTenant() {
        byte[] cifrado = service.cifrar("pfx".getBytes(StandardCharsets.UTF_8), 5L, UUID.randomUUID());

        assertThatThrownBy(() -> service.decifrar(cifrado, EnvelopeEncryptionService.KEK_VERSION_COM_AAD, 5L, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void v1LegadoSemAadContinuaDecifrandoPelaVersaoGravada() {
        byte[] original = "pfx-antigo".getBytes(StandardCharsets.UTF_8);
        byte[] cifradoV1 = service.cifrar(original);

        assertThat(service.decifrar(cifradoV1, (short) 1, 5L, UUID.randomUUID())).isEqualTo(original);
    }

    @Test
    void decifrarComKekErradaFalha() {
        byte[] cifrado = service.cifrar("dado-sensivel".getBytes(StandardCharsets.UTF_8));

        EnvelopeEncryptionService outroServico = new EnvelopeEncryptionService();
        byte[] outraKek = new byte[32];
        new SecureRandom().nextBytes(outraKek);
        ReflectionTestUtils.setField(outroServico, "kekBase64", Base64.getEncoder().encodeToString(outraKek));

        assertThatThrownBy(() -> outroServico.decifrar(cifrado)).isInstanceOf(IllegalStateException.class);
    }
}
