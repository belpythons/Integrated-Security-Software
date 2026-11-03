package com.badaklng.ses.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DevSecOps constraint annotation for sanitizing text inputs and mitigating Cross-Site Scripting (XSS).
 * Rejects inputs containing executable scripts, inline event handlers (onerror, onload, etc.),
 * or dangerous embedded elements (iframe, object, embed).
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    String message() default "Teks mengandung tag atau skrip yang berisiko keamanan (XSS)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
