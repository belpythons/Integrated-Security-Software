package com.badaklng.ses.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rewrite that turns the logo's {@code src} into the inline {@code cid:}
 * reference, tested on its own because the decision and the edit used to be
 * two separate rules.
 *
 * <p>{@code SesMailJob} previously asked
 * {@code body.contains("/image/ptb_logo.png")} and then rewrote with
 * {@code src="[^"]*\/image\/ptb_logo\.png"}. Both happened to agree on the one
 * body in the repository, so nothing failed — but they are not the same test,
 * and the two ways they can disagree are not equally harmless. A body that
 * matches the contains-check and not the regex ships an inline part nobody
 * references: wasteful, invisible. The reverse ships
 * {@code src="cid:badakLogo"} with no part attached, which is a guaranteed
 * dead image in every mail client. Routing both through one
 * {@link java.util.regex.Pattern} is what makes that second case
 * unrepresentable, and these tests are what stop someone re-splitting them.</p>
 *
 * <p>No Spring context, no SMTP: the two methods under test are static and
 * take a String.</p>
 */
class SesMailJobCidRewriteTest {

    private static final String CID = "src=\"cid:badakLogo\"";

    // ---------------------------------------------------------------
    // What the builders actually emit today
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The real production shape is detected and rewritten")
    void productionShape() {
        // Byte-for-byte what RequestEmailTemplateBuilder emits, including the
        // context path that comes from APP_LINK.
        String body = "<html><img src=\"http://apps.badaklng.home:8005/ses/image/ptb_logo.png\""
                + " width=\"150\" height=\"126\" alt=\"PT Badak NGL\"></html>";

        assertTrue(SesMailJob.referencesLogo(body));
        assertTrue(SesMailJob.rewriteLogoToCid(body).contains(CID));
        assertFalse(SesMailJob.rewriteLogoToCid(body).contains("ptb_logo.png"));
    }

    @Test
    @DisplayName("Detection and rewrite never disagree")
    void detectionAgreesWithRewrite() {
        // The invariant the single Pattern exists to hold. Every one of these
        // must answer the same way to both questions; a cid: emitted without
        // a matching attachment is the failure mode that reaches an inbox.
        String[] bodies = {
                "<img src=\"/ses/image/ptb_logo.png\">",
                "<img src='/ses/image/ptb_logo.png'>",
                "<img src = \"/image/ptb_logo.png\">",
                "<img SRC=\"/IMAGE/PTB_LOGO.PNG\">",
                "<img src=\"/image/ptb_logo.png?v=2\">",
                "<img src=\"ptb_logo.png\">",
                "<p>no logo at all</p>",
                "<img src=\"/image/other.png\">",
                "",
        };
        for (String body : bodies) {
            boolean detected = SesMailJob.referencesLogo(body);
            boolean rewritten = !body.equals(SesMailJob.rewriteLogoToCid(body));
            assertEquals(detected, rewritten,
                    "detection and rewrite disagree on: " + body);
        }
    }

    // ---------------------------------------------------------------
    // The variations that must still match
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Single quotes, spaced equals, odd case and a query suffix all match")
    void tolerantWhereItShouldBe() {
        assertTrue(SesMailJob.referencesLogo("<img src='/image/ptb_logo.png'>"),
                "single quotes are legal HTML and were previously missed");
        assertTrue(SesMailJob.referencesLogo("<img src = \"/image/ptb_logo.png\">"),
                "whitespace around = is legal HTML");
        assertTrue(SesMailJob.referencesLogo("<img SRC=\"/Image/PTB_Logo.png\">"),
                "attribute and path case must not decide whether the logo renders");
        assertTrue(SesMailJob.referencesLogo("<img src=\"/image/ptb_logo.png?v=3\">"),
                "a cache-busting suffix must not break the rewrite");
        assertTrue(SesMailJob.referencesLogo("<img src=\"ptb_logo.png\">"),
                "a relative src with no directory at all still names the logo");
    }

    @Test
    @DisplayName("A rewritten src is always double-quoted, whatever the source used")
    void alwaysEmitsDoubleQuotes() {
        // MimeMessageHelper.addInline publishes Content-ID: <badakLogo>, and
        // the HTML has to reference it in a form every client parses.
        assertTrue(SesMailJob.rewriteLogoToCid("<img src='/image/ptb_logo.png'>").contains(CID));
    }

    @Test
    @DisplayName("Every occurrence is rewritten, not just the first")
    void rewritesAllOccurrences() {
        String body = "<img src=\"/image/ptb_logo.png\"><img src=\"/image/ptb_logo.png\">";
        String out = SesMailJob.rewriteLogoToCid(body);

        assertFalse(out.contains("ptb_logo.png"), "a surviving URL next to a cid: is a half-broken header");
        assertEquals(2, out.split("cid:badakLogo", -1).length - 1);
    }

    // ---------------------------------------------------------------
    // The variations that must NOT match
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Another image, or no image, is left alone")
    void strictWhereItMustBe() {
        String other = "<img src=\"/image/other_logo.png\">";
        assertFalse(SesMailJob.referencesLogo(other));
        assertEquals(other, SesMailJob.rewriteLogoToCid(other),
                "rewriting an unrelated image would attach the wrong file under this cid");

        assertFalse(SesMailJob.referencesLogo("Dear Budi, plain text only"));
        assertFalse(SesMailJob.referencesLogo(null));
        assertEquals(null, SesMailJob.rewriteLogoToCid(null));
    }

    @Test
    @DisplayName("A body already carrying cid: is not rewritten twice")
    void idempotent() {
        // The queue retries a failed mail up to MAX_RETRY times. The rewrite
        // runs on a local copy each tick rather than on the stored row, but an
        // already-rewritten body must still be a no-op if that ever changes.
        String once = SesMailJob.rewriteLogoToCid("<img src=\"/image/ptb_logo.png\">");
        assertEquals(once, SesMailJob.rewriteLogoToCid(once));
    }

    // ---------------------------------------------------------------
    // The guard that pairs with the rewrite
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Plain-text bodies are never treated as HTML")
    void plainTextIsNotHtml() {
        // inlineLogo is html && referencesLogo && asset exists. If the first
        // term were dropped, a text fallback that happened to mention the
        // filename would be sent as a multipart HTML mail.
        assertFalse(SesMailJob.isHtml("Dear Budi,\n\nPlease upload."));
        assertTrue(SesMailJob.isHtml("<html><body>x</body></html>"));
        assertTrue(SesMailJob.isHtml("  \n <html>"), "leading whitespace must not hide the tag");
    }
}
