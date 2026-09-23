package com.l.erp.emissaofiscalservice.services.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Envelope encryption (AES-256-GCM) para material sensível guardado em banco: certificado A1
 * ({@code emissao.certificado_digital}) e, futuramente, CSRT ({@code emissao.csrt_config}) —
 * spec/modulos/emissao-fiscal/emissao-fiscal.md §3 item 1 / §11.
 *
 * <p>A KEK (Key Encryption Key) nunca fica no banco — vem de {@code EMISSAO_KEK} (env var,
 * Base64 de 32 bytes/AES-256), mesmo padrão de validação no startup do {@code JWT_SECRET} em
 * {@code auth-service/TokenService}. Formato do blob cifrado: {@code nonce(12) || ciphertext || tag}
 * — o nonce viaja junto porque o GCM exige um nonce novo por cifragem, não é segredo.</p>
 *
 * <p>{@code kekVersion} é fixo em 1 nesta etapa — rotação de KEK (job de re-cifragem) é operação
 * rara, fora de escopo da Etapa 1 (spec §3 item 1); a coluna já existe nas tabelas para não exigir
 * migração de schema quando a rotação virar necessidade real.</p>
 */
@Service
public class EnvelopeEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_NONCE_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEK_LENGTH_BYTES = 32; // AES-256

    public static final short KEK_VERSION_ATUAL = 1;

    @Value("${emissao.kek}")
    private String kekBase64;

    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void validarKek() {
        byte[] kek = decodeKek();
        if (kek.length != KEK_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "EMISSAO_KEK deve decodificar (Base64) para exatamente 32 bytes (AES-256). Verifique a variável de ambiente.");
        }
    }

    public byte[] cifrar(byte[] dadosClaros) {
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, chaveAtual(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] cifrado = cipher.doFinal(dadosClaros);

            byte[] blob = new byte[nonce.length + cifrado.length];
            System.arraycopy(nonce, 0, blob, 0, nonce.length);
            System.arraycopy(cifrado, 0, blob, nonce.length, cifrado.length);
            return blob;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar material sensível (envelope encryption).", e);
        }
    }

    public byte[] decifrar(byte[] blob) {
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            System.arraycopy(blob, 0, nonce, 0, GCM_NONCE_LENGTH_BYTES);
            int tamanhoCifrado = blob.length - GCM_NONCE_LENGTH_BYTES;
            byte[] cifrado = new byte[tamanhoCifrado];
            System.arraycopy(blob, GCM_NONCE_LENGTH_BYTES, cifrado, 0, tamanhoCifrado);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, chaveAtual(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(cifrado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar material sensível (envelope encryption) — KEK errada ou dado corrompido.", e);
        }
    }

    private SecretKeySpec chaveAtual() {
        return new SecretKeySpec(decodeKek(), "AES");
    }

    private byte[] decodeKek() {
        return Base64.getDecoder().decode(kekBase64);
    }
}
