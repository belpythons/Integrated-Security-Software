package com.badaklng.ses.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailLayoutDecoratorTest {

    private final EmailLayoutDecorator decorator = new EmailLayoutDecorator();
    private static final String BASE = "http://apps.badaklng.home:8005/ses";

    @Test
    @DisplayName("Decorates HTML snippet with master PT Badak NGL layout and logo header")
    void decoratesHtmlSnippet() {
        String snippet = "<h2>Unggah Dokumen</h2><p>Silakan klik tautan.</p>";
        String result = decorator.decorate(snippet, BASE);

        assertTrue(result.startsWith("<html>"), "must wrap with html tag");
        assertTrue(result.contains("<!DOCTYPE") || result.contains("<body"), "must contain body");
        assertFalse(result.contains("PT BADAK NGL"), "logo image already carries the org name; no redundant text line");
        assertTrue(result.contains("Center of Excellence"), "must carry subtitle");
        assertTrue(result.contains(BASE + "/image/ptb_logo.png"), "must carry logo img src");
        assertTrue(result.contains(snippet), "must carry the inner content snippet");
        assertTrue(result.contains("This is an automated notification from Security Entry System (SES)"), "must carry disclaimer");
        assertTrue(result.endsWith("</html>"), "must end with closing html tag");
    }

    @Test
    @DisplayName("Idempotent: Does not double-wrap body that already has full HTML tags")
    void doesNotDoubleWrapFullHtml() {
        String fullHtml = "<html><head></head><body><h1>Already Full</h1></body></html>";
        String result = decorator.decorate(fullHtml, BASE);
        assertEquals(fullHtml, result, "full HTML must be returned untouched");

        String doctypeHtml = "<!DOCTYPE html><html><body><h1>Doctype</h1></body></html>";
        String resultDoctype = decorator.decorate(doctypeHtml, BASE);
        assertEquals(doctypeHtml, resultDoctype, "doctype HTML must be returned untouched");
    }

    @Test
    @DisplayName("Does not wrap plain text bodies")
    void doesNotWrapPlainText() {
        String plainText = "Dear Bapak/Ibu,\n\nPengajuan badge Anda telah diterima.";
        String result = decorator.decorate(plainText, BASE);
        assertEquals(plainText, result, "plain text must not be wrapped in HTML");
    }

    @Test
    @DisplayName("Handles null and empty gracefully")
    void handlesNullAndEmpty() {
        assertEquals("", decorator.decorate(null, BASE));
        assertEquals("", decorator.decorate("", BASE));
        assertEquals("", decorator.decorate("   \n\t  ", BASE));
    }

    @Test
    @DisplayName("Handles base URL with or without trailing slash without duplicating slashes")
    void handlesTrailingSlash() {
        String snippet = "<p>Test</p>";
        String withSlash = decorator.decorate(snippet, BASE + "/");
        String withoutSlash = decorator.decorate(snippet, BASE);

        assertTrue(withSlash.contains("src=\"" + BASE + "/image/ptb_logo.png\""));
        assertFalse(withSlash.contains(BASE + "//"));
        assertEquals(withSlash, withoutSlash);

        String nullBase = decorator.decorate(snippet, null);
        assertTrue(nullBase.contains("src=\"/image/ptb_logo.png\""));
    }
}
