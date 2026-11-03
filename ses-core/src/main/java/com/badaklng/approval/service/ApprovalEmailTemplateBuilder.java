package com.badaklng.approval.service;

import com.badaklng.ses.text.SesText;
import com.badaklng.approval.entity.ApprovalStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders the approval notification email — the card layout from
 * {@code revision 1.jpeg}: Badak LNG logo, a status badge, a data card
 * carrying the submission's details, a "Lihat Pengajuan" button, and the
 * PT Badak NGL footer.
 *
 * <p>Plain Java string assembly rather than a Thymeleaf template, for one
 * hard reason: {@code app_mail.mail_body} is {@code VARCHAR2(4000)} and
 * {@code AppMailService.queue} truncates to it <em>silently</em> — a clipped
 * HTML mail is a broken HTML mail. Thymeleaf would carry the template file's
 * whitespace into the output, and that whitespace is unbudgeted. Here the
 * markup is emitted pre-minified and {@link #MAX_BODY} gives callers a hard
 * line to check against; {@link #buildText} is the fallback when a body
 * somehow still overruns.</p>
 *
 * <p>That ceiling is also why the detail rows ({@link Content#detail}) are
 * built by the form module with blank fields already dropped, and why
 * repeating child data (workers, vehicles) arrives as a count rather than a
 * table — see {@code ApprovalFormDescriptor.emailDetail}.</p>
 *
 * <p>Markup is deliberately old-fashioned — nested tables, inline styles, no
 * {@code <style>} block, no flexbox — because Outlook's Word rendering engine
 * ignores most of modern CSS. The status badge is a styled character rather
 * than an image so it survives the image-blocking that every mail client
 * applies by default; the logo is the one real image, and it sits above a
 * text wordmark that reads correctly when the image does not load (the app
 * is on an internal address, so an external client often cannot fetch it).</p>
 */
@Component
public class ApprovalEmailTemplateBuilder {

    /**
     * Safe ceiling for a rendered body. The column holds 4000; the margin
     * absorbs the {@code [DEV -> ...]} subject rewrite's cousin cases and any
     * multi-byte character that costs more than one byte on the way to Oracle.
     */
    public static final int MAX_BODY = 3900;

    /** Served from the platform jar; SES keeps no copy (see SesMailJob). */
    private static final String LOGO_PATH = "/image/ptb_logo.png";

    /**
     * Optional on purpose.
     *
     * <p>Every string below falls back to the literal it used to be, so the
     * builder keeps working with {@code new ApprovalEmailTemplateBuilder()} --
     * which is how {@code ApprovalEmailTemplateBuilderTest} constructs it, and
     * which is also what makes an un-seeded {@code APP_PARAMETER} render
     * byte-identical mail instead of blank clauses.
     */
    @Autowired(required = false)
    private SesText text;

    /** {@code SesText.get} when the bean is present, the literal otherwise. */
    private String txt(String key, String fallback) {
        return text == null ? fallback : text.get(key, fallback);
    }

    /**
     * Everything one mail needs. A parameter object rather than a dozen
     * positional arguments — the previous seven were already at the limit of
     * what a call site could be read against, and the detail card added more.
     * Plain mutable fields on purpose: this is a short-lived bag handed
     * straight to {@link #buildHtml}, not a domain type.
     */
    public static class Content {
        /** Drives colour, badge, headline and the status row's wording. */
        public ApprovalStatus status = ApprovalStatus.PENDING;
        /** Recipient's display name; blank falls back to a nameless greeting. */
        public String greetingName;
        /** Who submitted it — the card's "Diajukan Oleh" row. */
        public String requestorName;
        /** Step awaiting action; the PENDING card's "Tahap Saat Ini" row. */
        public String stepName;
        /** Who decided; the outcome card's "Disetujui Oleh"/"Ditolak Oleh" row. */
        public String actorName;
        public String nomorPengajuan;
        public String typePengajuan;
        /** Reviewer note; the CATATAN row is omitted when blank. */
        public String catatan;
        /** Ordered label -> value detail rows; blanks already dropped. */
        public Map<String, String> detail = Collections.emptyMap();
        /** Absolute app root, no trailing slash — used for logo and button. */
        public String baseUrl;
        /** Path appended to {@link #baseUrl} for the button. */
        public String actionPath;

        /*
         * Informational mails (a schedule was set, the whole process finished)
         * reuse this exact card, and everything about it except three strings
         * is already right. Overriding those three beats a second template
         * that would drift from this one the first time the layout changes.
         * {@link #status} still drives colour and badge glyph, so an override
         * picks its palette by picking a status.
         */

        /** Replaces the big coloured title. */
        public String headlineOverride;
        /** Replaces the one-sentence lead under the greeting. May contain <b>. */
        public String introOverride;
        /** Replaces the card's Status value. */
        public String statusLabelOverride;
    }

    /* Per-status palette + wording. PENDING covers the "you have something to
     * approve" mail; APPROVED/REJECTED cover the outcome mail back to the
     * requestor. CANCELLED is never mailed (ApprovalMailService returns early)
     * but would land on the PENDING branch if that guard ever came off. */
    private static String accent(ApprovalStatus s) {
        if (s == ApprovalStatus.APPROVED) return "#16a34a";
        if (s == ApprovalStatus.REJECTED) return "#dc2626";
        return "#d97706";
    }

    private static String cardBg(ApprovalStatus s) {
        if (s == ApprovalStatus.APPROVED) return "#f0fdf4";
        if (s == ApprovalStatus.REJECTED) return "#fef2f2";
        return "#fffbeb";
    }

    private static String cardBorder(ApprovalStatus s) {
        if (s == ApprovalStatus.APPROVED) return "#bbf7d0";
        if (s == ApprovalStatus.REJECTED) return "#fecaca";
        return "#fde68a";
    }

    /**
     * Glyph inside the status badge, shaped after fa-check / fa-hourglass-half
     * / fa-xmark but emitted as a text character, not markup.
     *
     * <p>It used to be the FontAwesome path inlined as {@code <svg>} — which
     * Gmail strips wholesale (Outlook's Word engine likewise), leaving an empty
     * coloured circle. An {@code <img>} fares no better: every mail client
     * blocks remote images by default. A character is the only badge that is
     * always drawn, which is what the class javadoc above already promised.</p>
     *
     * <p>PENDING carries VS15 ({@code &#65038;}) to ask for the hourglass's
     * text presentation rather than the colour-emoji one; clients that ignore
     * it still render a legible hourglass.</p>
     */
    private static String iconChar(ApprovalStatus s) {
        if (s == ApprovalStatus.APPROVED) return "&#10003;";          // check mark
        if (s == ApprovalStatus.REJECTED) return "&#10005;";          // multiplication x
        return "&#8987;&#65038;";                                     // hourglass, text presentation
    }

    private String headline(Content c) {
        return notBlank(c.headlineOverride) ? c.headlineOverride : headline(c.status);
    }

    private String intro(Content c) {
        return notBlank(c.introOverride) ? c.introOverride : intro(c.status);
    }

    private String statusLabel(Content c) {
        return notBlank(c.statusLabelOverride) ? c.statusLabelOverride : statusLabel(c.status);
    }

    private String headline(ApprovalStatus s) {
        if (s == ApprovalStatus.APPROVED) return txt("SES_TXT_MAIL_HEADLINE_APPROVED", "Pengajuan Disetujui");
        if (s == ApprovalStatus.REJECTED) return txt("SES_TXT_MAIL_HEADLINE_REJECTED", "Pengajuan Ditolak");
        return txt("SES_TXT_MAIL_HEADLINE_PENDING", "Persetujuan Diperlukan");
    }

    /**
     * One short sentence, and nothing else.
     *
     * <p>Facts belong in the card below, not inside the sentence. The previous
     * copy threaded the submitter, the form type and the step name through a
     * single clause — "<b>X</b> mengajukan Y dan <b>menunggu persetujuan
     * Anda</b> pada tahap Z." — and every one of those values is already a
     * labelled row two lines further down, so the sentence was reading the
     * table back aloud. It also changed shape whenever a value was blank,
     * which is why a subjectless "Ada pengajuan baru" fallback had to exist
     * at all.</p>
     *
     * <p>Now the sentence carries only what the reader has to <em>do</em> and
     * the card carries what it is about. Nothing here interpolates, so nothing
     * here can lose its subject.</p>
     */
    private String intro(ApprovalStatus s) {
        if (s == ApprovalStatus.APPROVED) {
            return txt("SES_TXT_MAIL_INTRO_APPROVED", "Pengajuan Anda telah <b>disetujui</b>.");
        }
        if (s == ApprovalStatus.REJECTED) {
            return txt("SES_TXT_MAIL_INTRO_REJECTED", "Pengajuan Anda <b>ditolak</b>.");
        }
        return txt("SES_TXT_MAIL_INTRO_PENDING", "Pengajuan berikut <b>menunggu persetujuan Anda</b>.");
    }

    private String statusLabel(ApprovalStatus s) {
        if (s == ApprovalStatus.APPROVED) return txt("SES_TXT_MAIL_STATUS_APPROVED", "DISETUJUI");
        if (s == ApprovalStatus.REJECTED) return txt("SES_TXT_MAIL_STATUS_REJECTED", "DITOLAK");
        return txt("SES_TXT_MAIL_STATUS_PENDING", "MENUNGGU PERSETUJUAN ANDA");
    }

    /* Wording on the button is status-independent on purpose. The target is
     * not — an assignment mail opens the approver screen, an outcome mail the
     * requestor's own page (see ApprovalMailService.requestorActionPath) — but
     * "Lihat Pengajuan" is true of both, and naming one product ("E-Sign",
     * "B-Sign") in a template that the platform will share across applications
     * dates it the moment it is reused. */
    private String buttonText() {
        return txt("SES_TXT_MAIL_BUTTON", "Lihat Pengajuan");
    }

    /**
     * Builds the HTML body.
     *
     * <p>The markup reads meaner than hand-written HTML would: three-digit
     * hex colours, {@code <b>} instead of {@code font-weight:bold}, the
     * column width declared on the first data row only, no style that a
     * parent element or an attribute already implies. That is not taste — it
     * is {@link #MAX_BODY}. A full request form is eleven detail rows, and
     * the bytes those rows need come out of the chrome around them.</p>
     */
    public String buildHtml(Content c) {
        String accent = accent(c.status);
        String base = stripTrailingSlash(c.baseUrl);

        StringBuilder b = new StringBuilder(3600);
        b.append("<html><body style=\"margin:0;padding:0;background:#f4f5f7\">")
         .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
         .append(" style=\"background:#f4f5f7;padding:24px 12px\"><tr><td align=\"center\">")
         .append("<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"560\"")
         .append(" style=\"max-width:100%;background:#fff;border-radius:8px;")
         .append("font-family:Arial,Helvetica,sans-serif\">");

        // header — logo, seamless with card (no border line)
        b.append("<tr><td align=\"center\" style=\"padding:36px 24px 16px\">")
         .append("<img src=\"").append(esc(base)).append(LOGO_PATH).append("\" width=\"150\"")
         .append(" alt=\"").append(esc(txt("SES_TXT_BRAND_WORDMARK", "Badak LNG")))
         .append("\" style=\"display:block;border:0\">")
         .append("<div style=\"margin-top:8px;font-size:12px;color:#888\">")
         .append(esc(txt("SES_TXT_BRAND_TAGLINE", "Center of Excellence")))
         .append("</div></td></tr>");

        // badge + headline
        b.append("<tr><td align=\"center\" style=\"padding:20px 24px 0\">")
         .append("<div style=\"width:52px;height:52px;line-height:52px;border-radius:26px;")
         .append("font-size:26px;color:#fff;display:inline-block;background:").append(accent)
         .append("\"><b>").append(iconChar(c.status)).append("</b></div>")
         .append("<div style=\"margin-top:14px;font-size:20px;color:").append(accent)
         .append("\"><b>").append(headline(c)).append("</b></div></td></tr>");

        // greeting + narrative
        b.append("<tr><td style=\"padding:20px 36px 0;font-size:14px;color:#333;line-height:1.6\">")
         .append(txt("SES_TXT_MAIL_GREETING", "Halo"));
        if (notBlank(c.greetingName)) {
            b.append(" <b>").append(esc(c.greetingName.trim())).append("</b>");
        }
        b.append(",<div style=\"margin-top:10px\">").append(intro(c)).append("</div></td></tr>");

        /* Data card. One two-column table rather than a styled <div> pair per
         * field: the shared typography moves up to the <table>, cellpadding
         * replaces a per-cell padding rule, and what is left per row is a
         * label colour. That is what buys the room for the detail rows. */
        b.append("<tr><td style=\"padding:16px 36px 0\">")
         .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:")
         .append(cardBg(c.status)).append(";border:1px solid ").append(cardBorder(c.status))
         .append(";border-radius:6px\"><tr><td style=\"padding:12px 16px\">")
         .append("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"0\"")
         .append(" style=\"font-size:13px;color:#111\">");

        boolean first = true;
        for (Map.Entry<String, String> e : cardRows(c).entrySet()) {
            row(b, first, e.getKey(), esc(e.getValue()));
            first = false;
        }
        row(b, first, "Status", "<b style=\"color:" + accent + "\">" + esc(statusLabel(c)) + "</b>");
        if (notBlank(c.catatan)) {
            row(b, false, txt("SES_TXT_MAIL_ROW_NOTE", "Catatan"), esc(c.catatan.trim()));
        }
        b.append("</table></td></tr></table></td></tr>");

        // call to action — omitted when actionPath is blank (an informational
        // copy to a recipient with no access to the app, e.g. the ext/contractor
        // "Email Penerima Dokumen" notice, which must not link to an intranet page)
        if (notBlank(c.actionPath)) {
            b.append("<tr><td align=\"center\" style=\"padding:24px 36px 28px\"><a href=\"")
             .append(esc(base)).append(esc(c.actionPath))
             .append("\" style=\"display:inline-block;padding:12px 32px;border-radius:5px;font-size:14px;")
             .append("color:#fff;text-decoration:none;background:").append(accent)
             .append("\"><b>").append(buttonText()).append("</b></a></td></tr>");
        }

        // footer
        b.append("<tr><td align=\"center\" style=\"padding:16px;font-size:12px;background:#222;")
         .append("color:#aaa;border-radius:0 0 8px 8px\">")
         // Year from the clock, not a parameter: a hardcoded "2026" is what
         // this line was, and a parameter would only move the staleness to a
         // January chore for an administrator.
         .append("&copy; ").append(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
         .append(' ').append(esc(txt("SES_TXT_MAIL_FOOTER",
                 "PT Badak NGL. All rights reserved.")))
         .append("</td></tr>")
         .append("</table></td></tr></table></body></html>");

        return b.toString();
    }

    /**
     * Plain-text equivalent, used when {@link #buildHtml} overruns
     * {@link #MAX_BODY}. Truncated HTML renders as garbage, so the fallback
     * drops the design rather than the information.
     */
    public String buildText(Content c) {
        StringBuilder b = new StringBuilder(600);
        b.append(txt("SES_TXT_MAIL_GREETING", "Halo"));
        if (notBlank(c.greetingName)) b.append(' ').append(c.greetingName.trim());
        b.append(",\n\n").append(headline(c)).append(".\n\n");
        for (Map.Entry<String, String> e : cardRows(c).entrySet()) {
            b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        b.append(txt("SES_TXT_MAIL_ROW_STATUS", "Status")).append(": ").append(statusLabel(c)).append('\n');
        if (notBlank(c.catatan)) {
            b.append(txt("SES_TXT_MAIL_ROW_NOTE", "Catatan")).append(": ").append(c.catatan.trim()).append('\n');
        }
        if (notBlank(c.actionPath)) {
            b.append("\nLihat pengajuan: ").append(stripTrailingSlash(c.baseUrl)).append(c.actionPath);
        }
        return b.toString();
    }

    /* ------------------------------------------------------------ */

    /**
     * The card's rows above Status/Catatan, shared by both renderers so the
     * fallback cannot fall out of step with the HTML.
     *
     * <p>The order answers the reader's questions in the order they ask them:
     * whose submission this is, what it is waiting on or who decided it, which
     * submission, then what is in it.</p>
     *
     * <p>The second slot is ONE row whose label follows the status — "Tahap
     * Saat Ini" for an approver, who needs to know where in the chain they
     * are; "Disetujui Oleh"/"Ditolak Oleh" for a requestor, who needs to know
     * who decided. Three separate rows would cost bytes {@link #MAX_BODY} has
     * no room for, and only one of them could ever be filled anyway.</p>
     *
     * <p>Detail rows keep the labels of the page they come from
     * (request/fragments/request-detail.html,
     * sticker/fragments/sticker-detail.html) so a reader never has to
     * translate between the mail and the page its button opens — see
     * {@code ApprovalFormDescriptor.emailDetail}.</p>
     */
    private Map<String, String> cardRows(Content c) {
        Map<String, String> rows = new LinkedHashMap<>();
        putIfPresent(rows, txt("SES_TXT_MAIL_ROW_SUBMITTED_BY", "Diajukan Oleh"), c.requestorName);
        if (notBlank(c.statusLabelOverride)) {
            // An informational mail is not a decision: "Disetujui Oleh" on a
            // "schedule set" card would name the wrong person for the wrong act.
            putIfPresent(rows, txt("SES_TXT_MAIL_ROW_CURRENT_STEP", "Tahap Saat Ini"), c.stepName);
        } else if (c.status == ApprovalStatus.APPROVED) {
            putIfPresent(rows, txt("SES_TXT_MAIL_ROW_APPROVED_BY", "Disetujui Oleh"), c.actorName);
        } else if (c.status == ApprovalStatus.REJECTED) {
            putIfPresent(rows, txt("SES_TXT_MAIL_ROW_REJECTED_BY", "Ditolak Oleh"), c.actorName);
        } else {
            putIfPresent(rows, txt("SES_TXT_MAIL_ROW_CURRENT_STEP", "Tahap Saat Ini"), c.stepName);
        }
        rows.put(txt("SES_TXT_MAIL_ROW_REQUEST_NO", "Nomor Pengajuan"), orDash(c.nomorPengajuan));
        rows.put(txt("SES_TXT_MAIL_ROW_REQUEST_TYPE", "Jenis Pengajuan"), orDash(c.typePengajuan));
        if (c.detail != null) {
            rows.putAll(c.detail);
        }
        return rows;
    }

    /** Omits the row entirely rather than dashing it — a blank fact is not a fact. */
    private static void putIfPresent(Map<String, String> rows, String label, String value) {
        if (notBlank(value)) {
            rows.put(label, value.trim());
        }
    }

    /**
     * One label/value line of the data card. {@code value} is pre-escaped.
     *
     * <p>The 40% column width rides on the first row only — an HTML table
     * sizes its columns from the first row, so repeating it on the other
     * thirteen would cost bytes {@link #MAX_BODY} has no room for.</p>
     */
    private static void row(StringBuilder b, boolean first, String label, String value) {
        b.append(first ? "<tr><td width=\"40%\" style=\"color:#888\">" : "<tr><td style=\"color:#888\">")
         .append(esc(label)).append("</td><td>").append(value).append("</td></tr>");
    }

    /**
     * Every interpolated value is escaped. These strings come from employee
     * records and free-text notes, so they can legitimately contain
     * {@code &} or {@code <} — unescaped, one of those silently corrupts the
     * surrounding markup.
     */
    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private static String orDash(String s) {
        return notBlank(s) ? s.trim() : "-";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }
}
