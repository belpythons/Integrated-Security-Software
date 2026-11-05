package com.badaklng.ses.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards {@link AppFileService#canonicalFileName} — the one piece of logic
 * behind "attachments are named after the document, not after the phone that
 * took the picture". The value it returns lands in {@code app_file.file_name},
 * which is both the attachment card's title and the browser's save-as name, so
 * a regression here is visible in two places and fixable in neither.
 *
 * <p>No Spring context: the method is pure.</p>
 */
class AppFileNameTest {

    @Test
    void usesTheDetectedTypesExtensionNotTheUploadsOwn() {
        assertEquals("Kartu Keluarga - Budi.jpg",
                AppFileService.canonicalFileName("Kartu Keluarga - Budi", "image/jpeg"));
        assertEquals("Bukti Bayar - REQ-2026-0016.pdf",
                AppFileService.canonicalFileName("Bukti Bayar - REQ-2026-0016", "application/pdf"));
        assertEquals("Lampiran Surat - KT 1234 AB.png",
                AppFileService.canonicalFileName("Lampiran Surat - KT 1234 AB", "image/png"));
    }

    @Test
    void stripsWhatAFilesystemWouldChokeOn() {
        assertEquals("KTP - Budi Santoso.pdf",
                AppFileService.canonicalFileName("KTP/\\: - Budi\tSantoso", "application/pdf"));
    }

    @Test
    void dropsTheDanglingSeparatorWhenTheNameHalfIsMissing() {
        // "<doc> - <worker>" with a blank worker must not read "Kartu Keluarga -.pdf".
        assertEquals("Kartu Keluarga.pdf",
                AppFileService.canonicalFileName("Kartu Keluarga - ", "application/pdf"));
    }

    @Test
    void fallsBackToTheBrowsersNameWhenThereIsNothingUsable() {
        assertNull(AppFileService.canonicalFileName(null, "application/pdf"));
        assertNull(AppFileService.canonicalFileName("   ", "application/pdf"));
        assertNull(AppFileService.canonicalFileName(" - ", "application/pdf"));
    }

    @Test
    void keepsTheNameInsideTheColumnWidth() {
        String longName = AppFileService.canonicalFileName(repeat("A", 400), "application/pdf");
        assertEquals(204, longName.length()); // 200 + ".pdf", column is 255
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
