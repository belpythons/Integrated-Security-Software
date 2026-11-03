package com.badaklng.ses.file;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Template-callable helper backing the big attachment card
 * (templates/layout/fragments/attachment.html) — wraps {@link AppFileService#getMeta}
 * so a Thymeleaf page can show a file's real name/type/size without a
 * dedicated model attribute per attachment. Named {@code fileMeta}, same
 * {@code @Component("...")}-for-Thymeleaf convention as platform's {@code
 * iconUtils} (see IconUtils).
 */
@Component("fileMeta")
@RequiredArgsConstructor
public class FileMetaUtils {

    private final AppFileService fileService;

    /** Null (not thrown) for a blank/missing/deleted fileId — templates use this to fall back to a placeholder. */
    public SesAppFileRepository.FileMeta meta(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return null;
        }
        try {
            return fileService.getMeta(fileId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String iconClass(String fileType) {
        if (fileType == null) {
            return "bi-file-earmark";
        }
        String t = fileType.toLowerCase(Locale.ROOT);
        if (t.equals("application/pdf")) {
            return "bi-file-earmark-pdf";
        }
        if (t.startsWith("image/")) {
            return "bi-file-earmark-image";
        }
        return "bi-file-earmark";
    }

    public String typeLabel(String fileType) {
        if (fileType == null) {
            return "File";
        }
        switch (fileType.toLowerCase(Locale.ROOT)) {
            case "application/pdf":
                return "PDF";
            case "image/jpeg":
                return "JPEG Image";
            case "image/png":
                return "PNG Image";
            default:
                return fileType;
        }
    }

    public String humanSize(Long bytes) {
        if (bytes == null) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }
}
