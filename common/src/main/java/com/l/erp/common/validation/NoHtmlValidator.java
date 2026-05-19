package com.l.erp.common.validation;

import com.l.erp.common.util.HtmlSanitizerUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return HtmlSanitizerUtil.sanitizePlainText(value).equals(value);
    }
}