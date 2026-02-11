package com.l.erp.authservice.util;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public class HtmlSanitizerUtil {
    // Define uma política que remove tags maliciosas, permitindo formatação básica
    private static final PolicyFactory POLICY = Sanitizers.FORMATTING.and(Sanitizers.LINKS);

    public static String sanitize(String htmlContent) {
        return POLICY.sanitize(htmlContent);
    }
}