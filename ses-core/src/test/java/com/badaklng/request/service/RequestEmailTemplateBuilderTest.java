package com.badaklng.request.service;

import com.badaklng.ses.mail.EmailLayoutDecorator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "please upload the documents" email content snippet.
 *
 * <p>Validates that the template builder generates a clean, bilingual HTML content snippet
 * without outer {@code <html>}/{@code <body>} boilerplate or {@code <img>} tags, keeping
 * database storage minimal and letting {@link EmailLayoutDecorator} decorate the master layout
 * during SMTP dispatch.</p>
 */
class RequestEmailTemplateBuilderTest {

    private final RequestEmailTemplateBuilder builder = new RequestEmailTemplateBuilder();
    private final EmailLayoutDecorator decorator = new EmailLayoutDecorator();

    private static final String BASE = "http://apps.badaklng.home:8005/ses";
    private static final String TOKEN = "1702e53fcf0b8c034f55b4bbd472d46bcebc254469a801a221d1f21fce21da91";

    /** Realistic worst case: the longest values the columns actually allow. */
    private static final String LONG_NAME = "MUHAMMAD RIZKI PRATAMA WIBOWO KUSUMANINGRAT";
    private static final String LONG_TYPE =
            "Tanda Pengenal Pekerja (new badge) — Kontraktor Pemeliharaan Kilang Jangka Panjang";

    private RequestEmailTemplateBuilder.Content content() {
        RequestEmailTemplateBuilder.Content c = new RequestEmailTemplateBuilder.Content();
        c.greetingName = LONG_NAME;
        c.requestNo = "REQ-2026-0001";
        c.typeName = LONG_TYPE;
        c.contractNo = "CTR-2026-00123";
        c.contractType = "LONG TERM";
        c.contractTitle = "Pemeliharaan Rutin Kilang LNG Train 1-8";
        c.periodContract = "01-01-2026 — 31-12-2026";
        c.baseUrl = BASE;
        c.uploadUrl = BASE + "/request/upload-docs?token=" + TOKEN;
        c.validDays = 7;
        return c;
    }

    // ---------------------------------------------------------------
    // The guard that matters most
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The body fits app_mail.mail_body, in characters AND in bytes")
    void bodyFitsTheMailBodyColumn() {
        String html = builder.buildHtml(content());

        assertTrue(html.length() <= RequestEmailTemplateBuilder.MAX_BODY,
                "HTML snippet is " + html.length() + " chars, limit " + RequestEmailTemplateBuilder.MAX_BODY);
        assertTrue(html.getBytes(UTF_8).length <= RequestEmailTemplateBuilder.MAX_BODY,
                "HTML snippet is " + html.getBytes(UTF_8).length + " bytes, limit is " + RequestEmailTemplateBuilder.MAX_BODY);
        // Clean snippet is compact and fits within legacy 4000-char column even with worst-case data
        assertTrue(html.length() <= 4000, "Clean snippet fits even in legacy 4000-char column, was: " + html.length());
    }

    // ---------------------------------------------------------------
    // Database Storage Minimization: No full HTML or img tags
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Snippet does not contain <html>, <body>, or <img> tags")
    void snippetDoesNotContainFullHtmlOrImageTags() {
        String html = builder.buildHtml(content());

        assertTrue(html.startsWith("<h2"), "Snippet should start directly with content heading");
        assertFalse(html.contains("<html"), "Snippet must not contain <html tag");
        assertFalse(html.contains("<body"), "Snippet must not contain <body tag");
        assertFalse(html.contains("<img"), "Snippet must not contain <img> tag (prevents bloated DB storage)");
    }

    // ---------------------------------------------------------------
    // Bilingual Content
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The mail carries bilingual instructions, contract identity, and the full link")
    void carriesBilingualIdentityAndLink() {
        String html = builder.buildHtml(content());

        assertTrue(html.contains("Kepada / <i>Dear</i>"), "the greeting is bilingual");
        assertTrue(html.contains("CTR-2026-00123"));
        assertTrue(html.contains("LONG TERM"));
        assertTrue(html.contains("Pemeliharaan Rutin Kilang LNG Train 1-8"));
        assertTrue(html.contains("01-01-2026"));
        assertTrue(html.contains("31-12-2026"));
        assertTrue(html.contains("Unggah Dokumen Permohonan Badge"));
        assertTrue(html.contains("Badge Request - Document Upload Required"));
        assertTrue(html.contains("Your badge request has been submitted"));
        assertTrue(html.contains("Unggah Dokumen / Upload Documents"));
        assertTrue(html.contains(TOKEN), "the token must survive into the href");
        assertTrue(html.contains("7 hari"));
        assertTrue(html.contains("7 days"));
    }

    @Test
    @DisplayName("The link is a real anchor, not just text")
    void linkIsClickable() {
        String html = builder.buildHtml(content());
        String expected = "href=\"" + BASE + "/request/upload-docs?token=" + TOKEN + "\"";
        assertTrue(html.contains(expected),
                "the upload URL must appear as an href; a pasted-text-only link is a support call");
    }

    @Test
    @DisplayName("Carries fallback URL text link matching maints reference")
    void carriesFallbackUrlLinkLikeMaints() {
        String html = builder.buildHtml(content());
        assertTrue(html.contains("If the button does not work, open:"),
                "must carry fallback link text matching maints reference");
        assertTrue(html.contains("style=\"color:#0d6efd;word-break:break-all;\""),
                "fallback link must be formatted with break-all style");
    }

    @Test
    @DisplayName("The identity-document warning is NOT in the mail")
    void dropsTheIdentityDocumentWarning() {
        String html = builder.buildHtml(content());
        assertFalse(html.contains("KTP"), "the KTP warning belongs on the upload page, not in the mail");
        assertFalse(html.contains("passport"));
        assertFalse(html.contains("they will be rejected"));
    }

    @Test
    @DisplayName("Neither footer bar is rendered")
    void dropsTheFooterBars() {
        String html = builder.buildHtml(content());
        assertFalse(html.contains("Untuk mengunjungi aplikasi"));
        assertFalse(html.contains("Internet Explorer"));
        assertFalse(html.contains("please do not reply"));
        assertFalse(html.contains("#ffff99"), "the yellow strip's background must be gone too");
    }

    @Test
    @DisplayName("The subject prefix is NOT built into the body")
    void noTestingBannerInTheBody() {
        String html = builder.buildHtml(content());
        assertFalse(html.contains("TESTING MODE"));
    }

    // ---------------------------------------------------------------
    // Safety
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Interpolated values are HTML-escaped")
    void valuesAreEscaped() {
        RequestEmailTemplateBuilder.Content c = content();
        c.greetingName = "<script>alert(1)</script>";
        c.typeName = "Badge A&B";
        String html = builder.buildHtml(c);

        assertFalse(html.contains("<script>"), "a name must never reach the reader as markup");
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("A&amp;B"));
    }

    @Test
    @DisplayName("Blank values render as dashes, never the word null")
    void blanksDegradeToDashes() {
        RequestEmailTemplateBuilder.Content c = new RequestEmailTemplateBuilder.Content();
        c.baseUrl = BASE;
        c.validDays = 7;
        String html = builder.buildHtml(c);

        assertFalse(html.contains("null"));
        assertTrue(html.contains(">-<"));
    }

    // ---------------------------------------------------------------
    // Plain-text fallback
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The text fallback keeps the information and is not sniffed as HTML")
    void textFallback() {
        String text = builder.buildText(content());

        assertTrue(text.contains("CTR-2026-00123"));
        assertTrue(text.contains("01-01-2026 — 31-12-2026"));
        assertTrue(text.contains(TOKEN));
        assertTrue(text.contains("7 days"));
        assertTrue(text.contains("7 hari"));
        assertFalse(text.contains("KTP"), "the text fallback tracks the HTML; the warning left both");
        assertFalse(text.startsWith("<"),
                "SesMailJob decides HTML by sniffing the first character; this must not open with a tag");
    }

    // ---------------------------------------------------------------
    // Master Layout Preview
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Write a preview file so the mail can actually be looked at")
    void writePreviewFile() throws IOException {
        Path dir = Paths.get("target", "mail-preview");
        Files.createDirectories(dir);

        String snippet = builder.buildHtml(content());
        Path fSnippet = dir.resolve("05-upload-docs-snippet.html");
        Files.write(fSnippet, snippet.getBytes(UTF_8));

        String decorated = decorator.decorate(snippet, BASE);
        Path fFull = dir.resolve("05-upload-docs-full.html");
        Files.write(fFull, decorated.getBytes(UTF_8));

        System.out.println("  " + fSnippet.getFileName() + "  " + snippet.length() + " chars (stored in DB)");
        System.out.println("  " + fFull.getFileName() + "  " + decorated.length() + " chars (sent via SMTP)");
        System.out.println("Upload-mail preview written to " + dir.toAbsolutePath());

        assertEquals(snippet, new String(Files.readAllBytes(fSnippet), UTF_8));
        assertEquals(decorated, new String(Files.readAllBytes(fFull), UTF_8));
    }
}
