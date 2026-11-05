package com.badaklng.ses.file;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Shared upload store backing the whole app. {@link #store} centralises validation
 * (size ceiling, magic-number content-type allowlist, filename sanitisation) so every
 * future module that uploads through {@code app_file} inherits it for free.
 */
@Service
public class AppFileService {
    public AppFileService(SesAppFileRepository fileRepository) { this.fileRepository = fileRepository; }

    private final SesAppFileRepository fileRepository;

    @Value("${ses.upload.max-document-size:20MB}")
    private DataSize maxDocumentSize;

    @Value("${ses.upload.allowed-types:application/pdf,image/jpeg,image/png}")
    private String allowedTypesCsv;

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47}; // .PNG

    @Transactional
    public String store(MultipartFile file, String refId) {
        return store(file, refId, null);
    }

    /**
     * Same as {@link #store(MultipartFile, String)} but files the upload under a
     * caller-supplied name ("Kartu Keluarga - Budi") plus the extension of the
     * DETECTED type, instead of whatever the browser happened to send
     * ("WhatsApp Image 2026-09-21 at 07.35.42.jpeg").
     *
     * <p>One value, two effects: {@code app_file.file_name} is both the title on
     * the attachment card and the {@code Content-Disposition} filename the
     * browser saves the file as — so naming it here is the only change needed
     * for the page and the download to agree.</p>
     *
     * <p>A blank/unusable {@code displayName} falls back to the browser's own
     * sanitised filename, i.e. exactly the old behaviour.</p>
     */
    @Transactional
    public String store(MultipartFile file, String refId, String displayName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > maxDocumentSize.toBytes()) {
            throw new IllegalArgumentException("File \"" + file.getOriginalFilename() + "\" exceeds the maximum "
                    + "allowed size of " + maxDocumentSize.toMegabytes() + " MB.");
        }

        byte[] fileData;
        try {
            fileData = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read uploaded file \"" + file.getOriginalFilename() + "\".", e);
        }

        String sniffedType = sniffContentType(fileData);
        List<String> allowedTypes = Arrays.asList(allowedTypesCsv.split(","));
        boolean allowed = false;
        for (String allowedType : allowedTypes) {
            if (allowedType.trim().equalsIgnoreCase(sniffedType)) {
                allowed = true;
                break;
            }
        }
        if (sniffedType == null || !allowed) {
            throw new IllegalArgumentException("Rejected file \"" + file.getOriginalFilename() + "\": "
                    + (sniffedType == null ? "unrecognised file type" : "detected type " + sniffedType + " is not allowed") + ".");
        }

        // Generated up front (rather than left to the @PrePersist UUID generator) so
        // the sanitised-filename fallback below can embed the same id the row is saved under.
        String fileId = UUID.randomUUID().toString();

        SesAppFile entity = new SesAppFile();
        entity.setFileId(fileId);
        entity.setFileData(fileData);
        entity.setFileSize((long) fileData.length);
        entity.setFileType(sniffedType);
        String canonical = canonicalFileName(displayName, sniffedType);
        entity.setFileName(canonical != null ? canonical
                : sanitiseFileName(file.getOriginalFilename(), sniffedType, fileId));
        entity.setRefId(refId);

        SesAppFile saved = fileRepository.save(entity);
        return saved.getFileId();
    }

    @Transactional(readOnly = true)
    public SesAppFile get(String fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
    }

    @Transactional(readOnly = true)
    public SesAppFileRepository.FileMeta getMeta(String fileId) {
        return fileRepository.findFileMetaByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
    }

    @Transactional
    public void reassign(String fileId, String refId) {
        SesAppFile file = get(fileId);
        file.setRefId(refId);
        fileRepository.save(file);
    }

    @Transactional
    public void delete(String fileId) {
        fileRepository.deleteById(fileId);
    }

    @Transactional(readOnly = true)
    public List<SesAppFile> findByRef(String refId) {
        return fileRepository.findByRefId(refId);
    }

    /**
     * Detects the actual content type from the file's leading bytes (magic number),
     * ignoring whatever the client-supplied Content-Type header claims. Returns null
     * when none of the supported signatures match.
     */
    private String sniffContentType(byte[] data) {
        if (startsWith(data, PDF_MAGIC)) {
            return "application/pdf";
        }
        if (startsWith(data, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(data, PNG_MAGIC)) {
            return "image/png";
        }
        return null;
    }

    private boolean startsWith(byte[] data, byte[] magic) {
        if (data == null || data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Strips path separators and control characters, keeping only the last path
     * segment (defends against a crafted filename like "../../etc/passwd"). Falls
     * back to a generic name when the sanitised result is empty.
     */
    private String sanitiseFileName(String originalFilename, String sniffedType, String fileId) {
        String name = originalFilename == null ? "" : originalFilename;
        // Keep only the last path segment, handling both separators regardless of OS.
        name = name.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        // Strip control characters.
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }
        name = sb.toString().trim();

        if (name.isEmpty()) {
            name = "upload_" + fileId + extensionForType(sniffedType);
        }
        return name;
    }

    /**
     * "Kartu Keluarga - Budi" + image/jpeg -> "Kartu Keluarga - Budi.jpg".
     * Returns null when the caller gave nothing usable, which tells {@link #store}
     * to keep the browser-supplied name.
     *
     * <p>Strips what a filesystem would choke on ({@code \ / : * ? " < > |} plus
     * control chars) rather than escaping it, collapses runs of whitespace, and
     * caps the base at 200 chars so the result always fits {@code
     * app_file.file_name} (255).</p>
     */
    static String canonicalFileName(String displayName, String sniffedType) {
        if (displayName == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(displayName.length());
        for (int i = 0; i < displayName.length(); i++) {
            char c = displayName.charAt(i);
            if (c < 0x20 || c == 0x7F || "\\/:*?\"<>|".indexOf(c) >= 0) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        String base = sb.toString().replaceAll("\\s+", " ").trim();
        // A trailing separator is what "<doc> - <name>" leaves behind when the
        // name half is missing; keep the document half rather than printing "KK -".
        while (base.endsWith("-") || base.endsWith(".")) {
            base = base.substring(0, base.length() - 1).trim();
        }
        if (base.isEmpty()) {
            return null;
        }
        if (base.length() > 200) {
            base = base.substring(0, 200).trim();
        }
        return base + extensionForType(sniffedType);
    }

    private static String extensionForType(String sniffedType) {
        if (sniffedType == null) {
            return "";
        }
        switch (sniffedType.toLowerCase(Locale.ROOT)) {
            case "application/pdf":
                return ".pdf";
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            default:
                return "";
        }
    }
}
