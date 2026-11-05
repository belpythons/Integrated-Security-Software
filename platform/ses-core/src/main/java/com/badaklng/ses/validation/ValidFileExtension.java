package com.badaklng.ses.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DevSecOps constraint annotation for restricting uploaded file extensions to trusted formats.
 * Defends against arbitrary file upload vulnerabilities (.exe, .sh, .jsp, etc.).
 */
@Documented
@Constraint(validatedBy = ValidFileExtensionValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFileExtension {

    String message() default "Format berkas tidak diizinkan. Hanya berkas PDF, JPG, atau PNG yang diperbolehkan";

    String[] allowed() default {"pdf", "jpg", "jpeg", "png"};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
