package com.badaklng.ses.validation;

import org.springframework.web.multipart.MultipartFile;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validator implementation for {@link ValidFileExtension}.
 * Checks the original file name extension of a {@link MultipartFile}.
 */
public class ValidFileExtensionValidator implements ConstraintValidator<ValidFileExtension, MultipartFile> {

    private final Set<String> allowedExtensions = new HashSet<>();

    @Override
    public void initialize(ValidFileExtension constraintAnnotation) {
        allowedExtensions.clear();
        for (String ext : constraintAnnotation.allowed()) {
            if (ext != null && !ext.trim().isEmpty()) {
                String normalized = ext.trim().toLowerCase(Locale.ROOT);
                if (normalized.startsWith(".")) {
                    normalized = normalized.substring(1);
                }
                allowedExtensions.add(normalized);
            }
        }
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            return true; // Use @NotNull on the field to disallow empty files
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return false;
        }

        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == originalFilename.length() - 1) {
            return false; // No extension
        }

        String extension = originalFilename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
        return allowedExtensions.contains(extension);
    }
}
