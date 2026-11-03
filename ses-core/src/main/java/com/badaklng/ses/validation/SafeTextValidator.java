package com.badaklng.ses.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator implementation for {@link SafeText}.
 * Scans string inputs for patterns typically associated with XSS payloads and HTML injection.
 */
public class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
            "(?i)(" +
                    "<\\s*script[^>]*>" +
                    "|<\\s*/\\s*script\\s*>" +
                    "|javascript\\s*:" +
                    "|vbscript\\s*:" +
                    "|data\\s*:\\s*text/html" +
                    "|\\bon[a-zA-Z]+\\s*=" +
                    "|<\\s*(iframe|object|embed|applet|meta|link)[^>]*>" +
                    "|<\\s*svg[^>]*on[a-zA-Z]+\\s*=" +
                    "|<\\s*img[^>]*on[a-zA-Z]+\\s*=" +
                    "|<\\s*body[^>]*on[a-zA-Z]+\\s*=" +
                    ")",
            Pattern.DOTALL
    );

    @Override
    public void initialize(SafeText constraintAnnotation) {
        // No specific configuration needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        // Return false if any dangerous pattern is found
        return !DANGEROUS_PATTERNS.matcher(value).find();
    }
}
