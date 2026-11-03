package com.badaklng.request.service;

import com.badaklng.ses.text.SesText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * The "please upload the badge documents" email, in HTML content snippet and plain text.
 *
 * <p>Generates a lightweight, semantic HTML snippet (modeled after {@code body-mail.html})
 * without {@code <html>}, {@code <body>}, outer framing tables, or inline image tags.
 * The master layout and PT Badak NGL logo header are dynamically wrapped at SMTP dispatch
 * time by {@code EmailLayoutDecorator} in {@code SesMailJob}.</p>
 *
 * <p>This keeps {@code APP_MAIL.mail_body} storage small (~1 KB) and clean in the database,
 * while ensuring modern, bilingual, and responsive emails are delivered.</p>
 */
@Component
public class RequestEmailTemplateBuilder {

    /**
     * Ceiling for the HTML body, mirroring
     * {@code ApprovalEmailTemplateBuilder.MAX_BODY}.
     */
    public static final int MAX_BODY = 3900;

    /**
     * Everything the email says. Plain mutable fields.
     */
    public static class Content {
        /**
         * Who the mail greets — the vendor/company name ({@code SesRequest.instance}),
         * since the recipient is the contractor's own contact, not the requestor.
         */
        public String greetingName;
        public String requestNo;
        public String typeName;
        public String contractNo;
        public String contractType;
        public String contractTitle;
        /** Formatted "startPeriod — finishPeriod", already human-readable. */
        public String periodContract;
        /** Absolute, already carrying the token. See {@code RequestMailService}. */
        public String uploadUrl;
        /** Application root, kept for backwards compatibility. */
        public String baseUrl;
        /** How many days the link lives. Interpolated so the text cannot drift from the code. */
        public int validDays;
    }

    private static final String NAVY = "#003B73";

    /**
     * Optional, same as the approval builder: every string falls back to the
     * literal it used to be, so the class still renders correctly when
     * constructed directly and an un-seeded {@code APP_PARAMETER} produces
     * byte-identical mail.
     */
    @Autowired(required = false)
    private SesText text;

    private String txt(String key, String fallback) {
        return text == null ? fallback : text.get(key, fallback);
    }

    /**
     * The six detail-row labels, resolved once and shared by {@link #buildHtml}
     * and {@link #buildText}.
     *
     * <p>They used to be written out twice -- once with {@code <i>} markup for
     * the HTML card and once padded for the plain-text table -- so a change of
     * wording had to be made in two places in one file, and the plain-text
     * fallback was the copy that would quietly go stale. The HTML labels carry
     * the {@code <i>} emphasis; {@code buildText} strips tags from the same
     * source.
     */
    private String[] detailLabels() {
        return new String[]{
                txt("SES_TXT_MAIL_UPLOAD_LABEL_REQUEST_NO", "No. Pengajuan / <i>Request No.</i>"),
                txt("SES_TXT_MAIL_UPLOAD_LABEL_REQUEST_TYPE", "Jenis Permohonan / <i>Request Type</i>"),
                txt("SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_NO", "No. Kontrak / <i>Contract No.</i>"),
                txt("SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_TYPE", "Jenis Kontrak / <i>Contract Type</i>"),
                txt("SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_TITLE", "Judul Kontrak / <i>Contract Title</i>"),
                txt("SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_PERIOD", "Masa Berlaku / <i>Contract Period</i>"),
        };
    }

    /** Same label without markup, padded for the plain-text table. */
    private static String plainLabel(String htmlLabel) {
        String plain = htmlLabel.replaceAll("<[^>]*>", "");
        StringBuilder b = new StringBuilder(plain);
        while (b.length() < 35) {
            b.append(' ');
        }
        return b.toString();
    }

    /**
     * Builds the clean, bilingual HTML content snippet (without outer html/body or img tags).
     */
    public String buildHtml(Content c) {
        String url = c.uploadUrl == null ? "" : c.uploadUrl.trim();
        String greeting = esc(blankToDash(c.greetingName));

        String[] labels = detailLabels();
        StringBuilder b = new StringBuilder(1800);

        // Header Title
        b.append("<h2 style=\"margin:0 0 14px 0;font-size:18px;color:").append(NAVY).append(";\">")
         .append(txt("SES_TXT_MAIL_UPLOAD_TITLE_ID", "Unggah Dokumen Permohonan Badge"))
         .append(" <span style=\"font-size:13px;color:#64748b;font-weight:normal;\">/ ")
         .append(txt("SES_TXT_MAIL_UPLOAD_TITLE_EN", "Badge Request - Document Upload Required"))
         .append("</span></h2>");

        // Greeting & Lead
        b.append("<p style=\"margin:0 0 10px 0;font-size:14px;color:#334155;\">")
         .append(txt("SES_TXT_MAIL_UPLOAD_GREETING", "Kepada / <i>Dear</i>"))
         .append(" <b>").append(greeting).append("</b>,</p>")
         .append("<p style=\"margin:0 0 16px 0;font-size:13px;color:#475569;line-height:1.5;\">")
         .append(txt("SES_TXT_MAIL_UPLOAD_LEAD_ID",
                 "Pengajuan badge Anda telah diterima dan memerlukan kelengkapan dokumen "
                         + "pendukung pekerja sebelum proses persetujuan dapat dilanjutkan.")).append("<br/>")
         .append("<i>").append(txt("SES_TXT_MAIL_UPLOAD_LEAD_EN",
                 "Your badge request has been submitted and requires supporting worker "
                         + "documents before the approval process can proceed.")).append("</i></p>");

        // Contract Detail Card Table (matching body-mail.html card pattern)
        b.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 18px 0;background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;\">")
         .append("<tr><td style=\"padding:12px 16px;\">")
         .append("<table width=\"100%\" cellpadding=\"4\" cellspacing=\"0\" style=\"font-size:13px;color:#1e293b;\">")
         .append(detailRow(true, labels[0], c.requestNo, true))
         .append(detailRow(false, labels[1], c.typeName, true))
         .append(detailRow(false, labels[2], c.contractNo, false))
         .append(detailRow(false, labels[3], c.contractType, false))
         .append(detailRow(false, labels[4], c.contractTitle, false))
         .append(detailRow(false, labels[5], c.periodContract, false))
         .append("</table></td></tr></table>");

        // Action Prompt
        b.append("<p style=\"margin:0 0 14px 0;font-size:13px;color:#475569;line-height:1.5;\">")
         .append(txt("SES_TXT_MAIL_UPLOAD_CTA_ID",
                 "Silakan klik tombol di bawah ini untuk mengunggah berkas persyaratan:")).append("<br/>")
         .append("<i>").append(txt("SES_TXT_MAIL_UPLOAD_CTA_EN",
                 "Please click the button below to upload the required documents:")).append("</i></p>");

        // Action Button
        b.append("<table cellpadding=\"0\" cellspacing=\"0\" style=\"margin:4px 0 16px 0;\"><tr><td style=\"background-color:").append(NAVY).append(";border-radius:6px;\">")
         .append("<a href=\"").append(esc(url)).append("\" style=\"display:inline-block;padding:11px 28px;color:#ffffff;font-size:14px;font-weight:bold;text-decoration:none;\">")
         .append(txt("SES_TXT_MAIL_UPLOAD_BUTTON", "Unggah Dokumen / Upload Documents"))
         .append("</a></td></tr></table>");

        // Fallback Link & Session Disclaimer (matching maints / body-mail.html pattern)
        b.append("<p style=\"margin:0 0 12px 0;font-size:12px;color:#64748b;line-height:1.5;\">")
         .append(txt("SES_TXT_MAIL_UPLOAD_FALLBACK_ID",
                 "Jika tombol di atas tidak dapat diklik, buka tautan berikut:")).append("<br/>")
         .append("<i>").append(txt("SES_TXT_MAIL_UPLOAD_FALLBACK_EN",
                 "If the button does not work, open:")).append("</i><br/>")
         .append("<a href=\"").append(esc(url)).append("\" style=\"color:#0d6efd;word-break:break-all;\">").append(esc(url)).append("</a></p>")
         .append("<p style=\"margin:0;font-size:12px;color:#94a3b8;line-height:1.4;\">")
         // {0} is the TTL in days -- interpolated rather than written out so
         // the promise in the text cannot drift from UploadTokenService.
         .append(textFormat("SES_TXT_MAIL_UPLOAD_TTL_ID",
                 "* Tautan berlaku selama {0} hari dan hanya berlaku untuk 1 sesi pengunggahan. "
                         + "Harap pastikan seluruh dokumen wajib telah diunggah sebelum menutup halaman.",
                 c.validDays)).append("<br/>")
         .append("<i>").append(textFormat("SES_TXT_MAIL_UPLOAD_TTL_EN",
                 "* The link is valid for {0} days and only valid for 1 upload session. "
                         + "Please ensure all mandatory documents are uploaded before closing the page.",
                 c.validDays)).append("</i></p>");

        return b.toString();
    }

    /**
     * Plain-text equivalent, used when {@link #buildHtml} overruns
     * {@link #MAX_BODY}.
     *
     * <p>Must never start with {@code <} — {@code SesMailJob} decides HTML vs
     * plain text by sniffing the first character, so a fallback that opened
     * with a tag would be sent as markup and arrive as source code.</p>
     */
    public String buildText(Content c) {
        String[] labels = detailLabels();
        String[] values = {blankToDash(c.requestNo), blankToDash(c.typeName), blankToDash(c.contractNo),
                blankToDash(c.contractType), blankToDash(c.contractTitle), blankToDash(c.periodContract)};

        StringBuilder b = new StringBuilder(600);
        b.append(txt("SES_TXT_MAIL_UPLOAD_GREETING_TEXT", "Dear Bapak/Ibu")).append(' ')
         .append(blankToDash(c.greetingName)).append(",\n\n")
         .append(strip(txt("SES_TXT_MAIL_UPLOAD_LEAD_ID",
                 "Pengajuan badge Anda telah diterima dan memerlukan kelengkapan dokumen "
                         + "pendukung pekerja sebelum proses persetujuan dapat dilanjutkan."))).append('\n')
         .append(strip(txt("SES_TXT_MAIL_UPLOAD_LEAD_EN",
                 "Your badge request has been submitted and requires supporting worker "
                         + "documents before the approval process can proceed."))).append("\n\n");
        for (int i = 0; i < labels.length; i++) {
            b.append(plainLabel(labels[i])).append(": ").append(values[i]).append('\n');
        }
        b.append('\n')
         .append(strip(txt("SES_TXT_MAIL_UPLOAD_CTA_ID",
                 "Silakan klik tombol di bawah ini untuk mengunggah berkas persyaratan:"))).append('\n')
         .append(strip(txt("SES_TXT_MAIL_UPLOAD_CTA_EN",
                 "Please click the button below to upload the required documents:"))).append("\n\n")
         .append(c.uploadUrl == null ? "" : c.uploadUrl.trim()).append("\n\n")
         .append(strip(textFormat("SES_TXT_MAIL_UPLOAD_TTL_ID",
                 "* Tautan berlaku selama {0} hari dan hanya berlaku untuk 1 sesi pengunggahan. "
                         + "Harap pastikan seluruh dokumen wajib telah diunggah sebelum menutup halaman.",
                 c.validDays))).append('\n')
         .append(strip(textFormat("SES_TXT_MAIL_UPLOAD_TTL_EN",
                 "* The link is valid for {0} days and only valid for 1 upload session. "
                         + "Please ensure all mandatory documents are uploaded before closing the page.",
                 c.validDays))).append('\n');
        return b.toString();
    }

    /**
     * {@link #txt} with {@code {0}} substituted.
     *
     * <p>Not {@code MessageFormat}: the stored text is prose an administrator
     * types, and {@code MessageFormat} gives a lone apostrophe a meaning that
     * would silently swallow the rest of the sentence.
     */
    private String textFormat(String key, String fallback, Object arg) {
        return txt(key, fallback).replace("{0}", String.valueOf(arg));
    }

    /** Drops any markup the stored wording carries, for the plain-text variant. */
    private static String strip(String html) {
        return html == null ? "" : html.replaceAll("<[^>]*>", "");
    }

    /**
     * Trims one trailing slash so {@code base + "/path"} never produces a
     * double slash.
     */
    public static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    /** One label/value row of the contract detail table. */
    private String detailRow(boolean first, String label, String value, boolean boldValue) {
        String val = esc(blankToDash(value));
        return (first ? "<tr><td width=\"38%\" style=\"color:#64748b;\">" : "<tr><td style=\"color:#64748b;\">")
                + label + "</td><td>" + (boldValue ? "<b>" + val + "</b>" : val) + "</td></tr>";
    }

    /** Never render the four characters "null" at a reader. */
    private static String blankToDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    /** Every interpolated value goes through here. */
    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
