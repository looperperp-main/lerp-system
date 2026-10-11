package com.l.erp.migracaoservice.util;

import java.security.SecureRandom;
import java.util.Base64;

/** Token opaco de download: 32 bytes de SecureRandom em Base64 URL-safe sem padding (43 chars). Spec export §5.3. */
public final class DownloadTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private DownloadTokenGenerator() {}

    public static String gerar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
