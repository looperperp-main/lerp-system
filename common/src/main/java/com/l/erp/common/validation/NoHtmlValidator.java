package com.l.erp.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    // Detecta tags HTML de verdade (<tag>, </tag>, <!--comment-->) — NÃO usar round-trip por um
    // sanitizer e comparar igualdade: o OWASP HTML-escapa aspas/& na serialização mesmo sem
    // nenhuma tag presente, rejeitando falsamente nomes como "BRINK'S..." ou "Johnson & Johnson".
    private static final Pattern HTML_TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z!][^>]*>");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return !HTML_TAG.matcher(value).find();
    }
}