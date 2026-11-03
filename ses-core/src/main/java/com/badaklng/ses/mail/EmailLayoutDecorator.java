package com.badaklng.ses.mail;

import com.badaklng.ses.text.SesText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Decorates HTML email content snippets with the official PT Badak NGL
 * branded master layout (outer framing table, header logo, Center of Excellence,
 * and automated disclaimer footer) before SMTP dispatch.
 *
 * <p>By separating the outer master layout from the database-stored email body,
 * the database queue ({@code APP_MAIL.mail_body}) only stores lightweight,
 * semantic content snippets (modeled after {@code body-mail.html}), keeping
 * database storage minimal and avoiding redundant storage of full HTML boilerplate
 * and image tags.</p>
 *
 * <p><b>Idempotent:</b> If a body already contains {@code <html} or {@code <!DOCTYPE}
 * (e.g. legacy emails), or is plain text (does not start with {@code <}), it is
 * returned as-is without double-wrapping.</p>
 */
@Component
public class EmailLayoutDecorator {

    public static final String LOGO_SRC = "/image/ptb_logo.png";
    public static final String NAVY = "#003B73";

    /**
     * Optional on purpose: {@code SesMailDispatcherService} keeps a
     * {@code new EmailLayoutDecorator()} as its fallback when no bean is
     * injected, and the decorator tests construct it the same way. Every
     * string below falls back to the literal it used to be, so an un-seeded
     * {@code APP_PARAMETER} produces byte-identical mail.
     */
    @Autowired(required = false)
    private SesText text;

    private String txt(String key, String fallback) {
        return text == null ? fallback : text.get(key, fallback);
    }

    /**
     * Wraps an HTML content snippet in the master layout.
     *
     * @param body    the raw HTML content snippet or plain text
     * @param baseUrl application root URL (used for fallback logo link if inline cid is blocked)
     * @return the decorated full HTML document, or the original body if already full HTML or plain text
     */
    public String decorate(String body, String baseUrl) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        String trimmed = body.trim();
        // Do not wrap if it is already a full document or plain text
        if (isFullHtml(trimmed) || !trimmed.startsWith("<")) {
            return body;
        }

        String base = stripTrailingSlash(baseUrl);

        StringBuilder b = new StringBuilder(trimmed.length() + 1400);
        b.append("<html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>")
         .append("<body style=\"margin:0;padding:0;background:#f4f5f7;\">")
         .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#f4f5f7;padding:24px 12px;\">")
         .append("<tr><td align=\"center\">")
         .append("<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"580\" style=\"max-width:100%;background:#ffffff;border-radius:8px;font-family:Arial,Helvetica,sans-serif;box-shadow:0 2px 8px rgba(0,0,0,0.06);\">")
         // Header Logo PT Badak NGL
         .append("<tr><td align=\"center\" style=\"padding:28px 24px 16px;border-bottom:1px solid #edf2f7;\">")
         .append("<img src=\"").append(base).append(LOGO_SRC).append("\" width=\"140\" height=\"118\" alt=\"PT Badak NGL\" style=\"display:block;border:0;outline:none;font-family:Arial,Helvetica,sans-serif;font-size:16px;font-weight:bold;color:").append(NAVY).append(";\">")
         .append("<div style=\"margin-top:6px;font-size:11px;color:#718096;\">")
         .append(txt("SES_TXT_BRAND_TAGLINE", "Center of Excellence")).append("</div>")
         .append("</td></tr>")
         // Slot Konten
         .append("<tr><td style=\"padding:24px 32px;\">")
         .append(trimmed)
         .append("</td></tr>")
         // Footer Disclaimer
         .append("<tr><td style=\"padding:16px 32px 24px;border-top:1px solid #edf2f7;font-size:11px;color:#a0aec0;text-align:center;line-height:1.5;\">")
         .append(txt("SES_TXT_MAIL_NO_REPLY",
                 "This is an automated notification from Security Entry System (SES). "
                         + "Please do not reply directly to this email.")).append("<br/>")
         // Year from the clock; the previous line carried no year at all while
         // three other footers carried a hardcoded 2026. One source now.
         .append("&copy; ").append(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
         .append(' ').append(txt("SES_TXT_MAIL_FOOTER", "PT Badak NGL. All rights reserved."))
         .append("</td></tr>")
         .append("</table></td></tr></table></body></html>");

        return b.toString();
    }

    /**
     * Checks if the body already contains full document tags.
     */
    public boolean isFullHtml(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("<html") || lower.contains("<!doctype");
    }

    /**
     * Strips a trailing slash from a base URL.
     */
    public static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }
}
