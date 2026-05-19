package com.l.erp.common.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public class HtmlSanitizerUtil {

    private HtmlSanitizerUtil() {}

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING.and(Sanitizers.LINKS);

    // Strips all HTML — use for plain-text fields (names, addresses, etc.)
    private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

    public static String sanitize(String htmlContent) {
        return POLICY.sanitize(htmlContent);
    }

    public static String sanitizePlainText(String value) {
        return PLAIN_TEXT_POLICY.sanitize(value);
    }
}