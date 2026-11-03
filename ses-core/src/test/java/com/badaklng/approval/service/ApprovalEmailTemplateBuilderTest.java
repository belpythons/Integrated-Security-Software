package com.badaklng.approval.service;

import com.badaklng.approval.entity.ApprovalStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the things that silently break this template in production.
 *
 * <p>No Spring context — the builder is a pure function, and a context would
 * drag in the Oracle connection this check does not need.</p>
 *
 * <p>Side effect on purpose: every rendered variant is also written to
 * {@code target/mail-preview/} so the same {@code mvn test} run that proves
 * the guards produces files you can open in a browser to eyeball the design.</p>
 */
class ApprovalEmailTemplateBuilderTest {

    private final ApprovalEmailTemplateBuilder builder = new ApprovalEmailTemplateBuilder();

    private static final String BASE = "http://apps.badaklng.home:8005/ses";

    /* Realistic worst case: the longest values the real data can produce. */
    private static final String LONG_NAME = "FATCHURRAMADHANI DWI PRASETYO WIBOWO";
    private static final String LONG_NO   = "SES/REQ/2026/09/00001234-REVISI-02";
    private static final String LONG_TYPE = "Permohonan Izin Masuk Kawasan Terbatas & Stiker Kendaraan";
    private static final String LONG_STEP = "Persetujuan Manager Security & Loss Prevention";
    private static final String LONG_NOTE = "Dokumen kelengkapan pekerja belum sesuai, mohon dilengkapi "
            + "surat keterangan sehat dan sertifikat safety induction yang masih berlaku sebelum "
            + "pengajuan diajukan kembali ke tahap berikutnya.";

    private static ApprovalEmailTemplateBuilder.Content content(ApprovalStatus status) {
        ApprovalEmailTemplateBuilder.Content c = new ApprovalEmailTemplateBuilder.Content();
        c.status = status;
        c.greetingName = "BUDI";
        c.requestorName = "SITI AMINAH";
        c.nomorPengajuan = "SES/REQ/0001";
        c.typePengajuan = "New Request";
        c.baseUrl = BASE;
        c.actionPath = "/approval/detail/9";
        return c;
    }

    /** Every "Request Details" row the request form can produce, all filled. */
    private static Map<String, String> fullRequestDetail() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Type of Request", "TPP_ WIDYAKARSA_ PENELITIAN SKRIPSI");
        d.put("Contract Number", "4500123456/PO/2026-REV02");
        d.put("Contract Type", "Unit Rate Contract - Multi Year");
        d.put("Contract Title", "Jasa Penunjang Operasi Kilang dan Pemeliharaan Fasilitas");
        d.put("Zone Access", "Zone 01 - Process Area Restricted");
        d.put("Instansi", "PT Widyakarsa Nusantara Sejahtera");
        d.put("Address", "Jl. Yos Sudarso No. 1, Bontang Utara, Kalimantan Timur 75311");
        d.put("Start Period", "21-09-2026");
        d.put("Finish Period", "30-09-2026");
        d.put("Payment Amount", "Rp 12.500.000");
        d.put("Jumlah Pekerja", "25");
        return d;
    }

    /**
     * The body column is VARCHAR2(4000) and AppMailService.queue truncates to
     * it without a word of warning — a clipped HTML mail renders as garbage.
     * This is the guard that lets detail rows be added at all: every status,
     * every row filled, longest realistic values, plus a reviewer note.
     */
    @Test
    void everyVariantFitsTheMailBodyColumn() {
        for (ApprovalStatus status : ApprovalStatus.values()) {
            ApprovalEmailTemplateBuilder.Content c = content(status);
            c.greetingName = LONG_NAME;
            c.requestorName = LONG_NAME;
            c.stepName = LONG_STEP;
            c.actorName = LONG_NAME;
            c.nomorPengajuan = LONG_NO;
            c.typePengajuan = LONG_TYPE;
            c.catatan = LONG_NOTE;
            c.detail = fullRequestDetail();
            c.actionPath = "/approval/detail/123456";

            String html = builder.buildHtml(c);
            assertTrue(html.length() <= ApprovalEmailTemplateBuilder.MAX_BODY,
                    status + " body is " + html.length() + " chars, over the "
                            + ApprovalEmailTemplateBuilder.MAX_BODY + " guard");
            assertTrue(html.getBytes(StandardCharsets.UTF_8).length <= 4000,
                    status + " body exceeds 4000 bytes once encoded");
        }
    }

    @Test
    void cardCarriesTheSubmissionIdentity() {
        String html = builder.buildHtml(content(ApprovalStatus.PENDING));

        assertTrue(html.contains("Diajukan Oleh"));
        assertTrue(html.contains("SITI AMINAH"));
        assertTrue(html.contains("Nomor Pengajuan"));
        assertTrue(html.contains("Jenis Pengajuan"));
        assertTrue(html.contains("Status"));
        assertTrue(html.contains("SES/REQ/0001"));
        assertTrue(html.contains("New Request"));
        assertTrue(html.contains("Lihat Pengajuan"));
        assertTrue(html.contains("&copy; 2026 PT Badak NGL. All rights reserved."));
        // note block appears only when there is a note
        assertFalse(html.contains("Catatan"));
    }

    /**
     * The point of the redesign: the mail now carries the same Request Details
     * the approver would see on the page, and carries ONLY the rows the form
     * actually filled in — omitting blanks is what keeps the body inside the
     * column, so a blank field leaking back in as a dash is a real regression.
     */
    @Test
    void detailRowsAreRenderedAndBlanksAreNeverInvented() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.PENDING);
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("Type of Request", "TPP_ WIDYAKARSA_ PENELITIAN SKRIPSI");
        detail.put("Zone Access", "Zone 01");
        detail.put("Start Period", "21-09-2026");
        c.detail = detail;

        String html = builder.buildHtml(c);
        assertTrue(html.contains("Type of Request"));
        assertTrue(html.contains("TPP_ WIDYAKARSA_ PENELITIAN SKRIPSI"));
        assertTrue(html.contains("Zone Access"));
        assertTrue(html.contains("21-09-2026"));
        // rows the form left empty must not appear at all
        assertFalse(html.contains("Contract Number"));
        assertFalse(html.contains("Payment Amount"));
    }

    /**
     * Whose submission and which step are still answered — as labelled card
     * rows, not as clauses threaded through the sentence. The submitter's name
     * comes from the data; it was never allowed to be a fixed phrase and still
     * is not.
     */
    @Test
    void pendingCardNamesTheSubmitterAndTheStep() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.PENDING);
        c.stepName = "Requestor's Manager Approval";

        String html = builder.buildHtml(c);
        assertTrue(html.contains("Diajukan Oleh"));
        assertTrue(html.contains("SITI AMINAH"));
        assertTrue(html.contains("Tahap Saat Ini"));
        assertTrue(html.contains("Requestor&#39;s Manager Approval"));
        assertTrue(html.contains("menunggu persetujuan Anda"));
        assertTrue(html.contains("MENUNGGU PERSETUJUAN ANDA"));
        // the step is a row, not part of the sentence
        assertFalse(html.contains("pada tahap"));
    }

    /**
     * The point of this pass: the narrative is one short sentence per status
     * and interpolates nothing. Values belong to the card below it — a
     * sentence that re-reads the table is what made the old copy unreadable,
     * and a sentence built from optional values is what forced a subjectless
     * "Ada pengajuan baru" fallback to exist.
     */
    @Test
    void narrativeIsOneSentenceAndCarriesNoFacts() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.PENDING);
        c.stepName = "Requestor's Manager Approval";
        c.actorName = "AHMAD FAUZI";
        String pending = narrative(builder.buildHtml(c));
        assertEquals("Pengajuan berikut <b>menunggu persetujuan Anda</b>.", pending);

        c = content(ApprovalStatus.APPROVED);
        c.actorName = "AHMAD FAUZI";
        assertEquals("Pengajuan Anda telah <b>disetujui</b>.", narrative(builder.buildHtml(c)));

        c = content(ApprovalStatus.REJECTED);
        c.actorName = "AHMAD FAUZI";
        assertEquals("Pengajuan Anda <b>ditolak</b>.", narrative(builder.buildHtml(c)));
    }

    /** The sentence between the greeting and the data card. */
    private static String narrative(String html) {
        String open = "<div style=\"margin-top:10px\">";
        int from = html.indexOf(open);
        assertTrue(from >= 0, "narrative block not found");
        from += open.length();
        return html.substring(from, html.indexOf("</div>", from));
    }

    /** A rejection is useless without who rejected it and why. */
    @Test
    void rejectedCardNamesTheApproverAndCarriesTheNote() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.REJECTED);
        c.actorName = "AHMAD FAUZI";
        c.catatan = "Dokumen KTP pekerja belum dilampirkan.";

        String html = builder.buildHtml(c);
        assertTrue(html.contains("Ditolak Oleh"));
        assertTrue(html.contains("AHMAD FAUZI"));
        assertTrue(html.contains("Catatan"));
        assertTrue(html.contains("Dokumen KTP pekerja belum dilampirkan."));
        assertTrue(html.contains("DITOLAK"));
        assertFalse(html.contains("Tahap Saat Ini"), "the step row belongs to PENDING only");
    }

    /** An approval should say who signed it off, not just that it happened. */
    @Test
    void approvedCardNamesTheApprover() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.APPROVED);
        c.actorName = "AHMAD FAUZI";

        String html = builder.buildHtml(c);
        assertTrue(html.contains("Disetujui Oleh"));
        assertTrue(html.contains("AHMAD FAUZI"));
        assertFalse(html.contains("Ditolak Oleh"));
    }

    /**
     * The design came from a B-Sign screenshot, so the first cut of this
     * template spoke B-Sign: "Dokumen Selesai Ditandatangani", "JUDUL DOKUMEN",
     * "Unduh Dokumen Selesai", "aplikasi E-Sign". SES approves access stickers
     * and permits — nothing gets signed and nothing gets downloaded. This is
     * the guard that stops that wording creeping back in.
     */
    @Test
    void approvedMailStaysProductNeutral() {
        String html = builder.buildHtml(content(ApprovalStatus.APPROVED));

        assertTrue(html.contains("Jenis Pengajuan"));
        assertTrue(html.contains("Lihat Pengajuan"));
        assertTrue(html.contains("Pengajuan Disetujui"));
        assertTrue(html.contains("DISETUJUI"));

        assertFalse(html.contains("JUDUL DOKUMEN"));
        assertFalse(html.contains("Unduh Dokumen Selesai"));
        assertFalse(html.contains("Ditandatangani"));
        assertFalse(html.contains("E-Sign"));
    }

    /**
     * The link used to be built from ses.app.base-url, whose committed value
     * is localhost — unreachable for every recipient. It now comes from the
     * APP_LINK parameter, context path and all.
     */
    @Test
    void buttonAndLogoUseTheConfiguredBaseUrl() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.APPROVED);
        c.baseUrl = BASE + "/";
        c.actionPath = "/approval/detail/9";

        String html = builder.buildHtml(c);
        // trailing slash on the base must not produce a doubled slash
        assertTrue(html.contains("href=\"" + BASE + "/approval/detail/9\""));
        assertTrue(html.contains("src=\"" + BASE + "/image/ptb_logo.png\""));
    }

    /**
     * Names, notes and detail values come from employee records, reviewer
     * input and free-text form fields, so they can contain characters that
     * would otherwise break the markup.
     */
    @Test
    void interpolatedValuesAreEscaped() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.REJECTED);
        c.greetingName = "<script>alert(1)</script>";
        c.requestorName = "<img src=x onerror=alert(1)>";
        c.actorName = "<b>BOS</b>";
        c.nomorPengajuan = "A&B";
        c.typePengajuan = "C<D";
        c.catatan = "note \"quoted\" & <b>bold</b>";
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("Contract Title", "PT A & B <Tbk>");
        c.detail = detail;

        String html = builder.buildHtml(c);
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img src=x"), "an injected tag must arrive as text, not markup");
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("A&amp;B"));
        assertTrue(html.contains("PT A &amp; B &lt;Tbk&gt;"));
        assertTrue(html.contains("Catatan"));
    }

    @Test
    void blankValuesDegradeToDashesNotNulls() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.PENDING);
        c.greetingName = "  ";
        c.requestorName = null;
        c.nomorPengajuan = null;
        c.typePengajuan = "";
        c.stepName = null;

        String html = builder.buildHtml(c);
        assertFalse(html.contains("null"));
        assertTrue(html.contains(">-<"));
        assertTrue(html.contains("Halo,"), "a blank name must not leave a dangling space");
        assertTrue(html.contains("menunggu persetujuan Anda"),
                "the narrative interpolates nothing, so it cannot be affected by a blank value");
        assertFalse(html.contains("Diajukan Oleh"), "no submitter, no row");
        assertFalse(html.contains("Tahap Saat Ini"), "no step, no row");
    }

    @Test
    void textFallbackKeepsTheInformation() {
        ApprovalEmailTemplateBuilder.Content c = content(ApprovalStatus.APPROVED);
        c.catatan = "oke";
        c.detail = fullRequestDetail();

        String text = builder.buildText(c);
        assertTrue(text.contains("Diajukan Oleh: SITI AMINAH"));
        assertTrue(text.contains("Nomor Pengajuan: SES/REQ/0001"));
        assertTrue(text.contains("Jenis Pengajuan: New Request"));
        assertTrue(text.contains("Contract Number: 4500123456/PO/2026-REV02"));
        assertTrue(text.contains("Status: DISETUJUI"));
        assertTrue(text.contains("Catatan: oke"));
        assertTrue(text.contains(BASE + "/approval/detail/9"));
        assertFalse(text.startsWith("<"), "fallback must not be sniffed as HTML by SesMailJob");
    }

    /** Writes the three variants to target/mail-preview/ for a visual check. */
    @Test
    void writePreviewFiles() throws IOException {
        Path dir = Paths.get("target", "mail-preview");
        Files.createDirectories(dir);

        ApprovalEmailTemplateBuilder.Content pending = content(ApprovalStatus.PENDING);
        pending.greetingName = "UMAR HAKIM";
        pending.requestorName = "SITI AMINAH";
        pending.stepName = "Requestor's Manager Approval";
        pending.nomorPengajuan = "REQ-2026-0004";
        pending.detail = fullRequestDetail();
        write(dir, "01-pending.html", builder.buildHtml(pending));

        ApprovalEmailTemplateBuilder.Content approved = content(ApprovalStatus.APPROVED);
        approved.greetingName = "SITI AMINAH";
        approved.actorName = "AHMAD FAUZI";
        approved.nomorPengajuan = "REQ-2026-0004";
        approved.detail = fullRequestDetail();
        write(dir, "02-approved.html", builder.buildHtml(approved));

        ApprovalEmailTemplateBuilder.Content rejected = content(ApprovalStatus.REJECTED);
        rejected.greetingName = "SITI AMINAH";
        rejected.actorName = "AHMAD FAUZI";
        rejected.nomorPengajuan = "REQ-2026-0004";
        rejected.catatan = "Dokumen kelengkapan pekerja belum sesuai, mohon dilengkapi.";
        rejected.detail = fullRequestDetail();
        write(dir, "03-rejected.html", builder.buildHtml(rejected));

        // Sticker shares the template; only the detail rows differ.
        ApprovalEmailTemplateBuilder.Content sticker = content(ApprovalStatus.PENDING);
        sticker.greetingName = "UMAR HAKIM";
        sticker.typePengajuan = "Stiker Kendaraan";
        sticker.stepName = "Security Section Head";
        sticker.nomorPengajuan = "STK-2026-0002";
        Map<String, String> stickerDetail = new LinkedHashMap<>();
        stickerDetail.put("Badge", "123456");
        stickerDetail.put("Departemen", "Information Technology");
        stickerDetail.put("Seksi", "Application Support");
        stickerDetail.put("Instansi", "PT Badak NGL");
        stickerDetail.put("No. Telepon", "081234567890");
        stickerDetail.put("Alamat", "Jl. Yos Sudarso No. 1, Bontang");
        stickerDetail.put("Jumlah Dibayar", "Rp 150.000");
        stickerDetail.put("Jumlah Kendaraan", "2");
        sticker.detail = stickerDetail;
        write(dir, "04-pending-sticker.html", builder.buildHtml(sticker));

        System.out.println("Mail previews written to " + dir.toAbsolutePath());
    }

    private static void write(Path dir, String name, String html) throws IOException {
        Files.write(dir.resolve(name), html.getBytes(StandardCharsets.UTF_8));
        System.out.println("  " + name + " — " + html.length() + " chars");
    }
}
