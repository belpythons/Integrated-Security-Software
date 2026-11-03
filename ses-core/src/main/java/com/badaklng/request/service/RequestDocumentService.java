package com.badaklng.request.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesZone;
import com.badaklng.request.repository.SesZoneRepository;
import com.badaklng.ses.text.SesText;
import com.badaklng.ses.user.UserDisplayNameService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The printable permit sheet: "Permohonan Badge" as a real PDF.
 *
 * <p>Rendered in two steps — Thymeleaf produces the HTML, OpenHTMLtoPDF turns
 * that into PDF bytes. The alternative was a print-only HTML page driven by the
 * browser's print dialog, which cannot produce a file at all: "Download" would
 * have meant "open the print dialog and hope the user chooses Save as PDF".</p>
 *
 * <p><b>Everything the renderer needs is inlined.</b> OpenHTMLtoPDF is handed a
 * bare HTML string with no servlet context behind it, so it cannot fetch
 * {@code /file/{id}} or {@code /request/{id}/qr.png} over HTTP — and giving it
 * a resolver that could would mean the PDF renderer making authenticated calls
 * back into the application mid-render. The QR therefore travels as a
 * {@code data:} URI — and it is the only image on the sheet.</p>
 *
 * <p>The template must be <b>XHTML-strict</b>: every tag closed, every
 * attribute quoted. OpenHTMLtoPDF parses with an XML parser and will reject
 * markup a browser would happily guess at. That is why the print template is a
 * separate file rather than a variant of an existing screen.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestDocumentService {

    /**
     * {@code SimpleDateFormat} is NOT thread-safe, so these are patterns, not
     * shared instances. As static fields they were a real hazard: two users
     * downloading a permit at the same time share one formatter, and its
     * internal {@code Calendar} is mutated mid-format. The failure is silent
     * and wrong -- a garbled or swapped date on an official letter -- not an
     * exception. Instantiated per render below; a permit PDF is not issued
     * often enough for the allocation to matter.
     */
    private static final String LETTER_DATE_PATTERN = "d MMMM yyyy";
    private static final String SHORT_DATE_PATTERN = "dd-MM-yyyy";
    private static final Locale ID = new Locale("id", "ID");

    private final SesText text;
    private final TemplateEngine templateEngine;
    private final RequestService requestService;
    private final RequestQrCodeService qrCodeService;
    private final SesZoneRepository zoneRepository;
    private final ApprovalService approvalService;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * May this request be printed yet?
     *
     * <p>Only an APPROVED one. The sheet names the Procurement Manager and
     * Manager Pemohon signatories and carries a QR asserting the permit is
     * valid; printing it mid-flow would put a document into circulation that
     * claims approvals nobody has given. The button is hidden client-side
     * too, but this is the check that matters.</p>
     */
    @Transactional(readOnly = true)
    public boolean isPrintable(SesRequest request) {
        return request != null && "APPROVED".equals(request.getStatus());
    }

    /** PDF bytes of the permit sheet for one request. */
    @Transactional(readOnly = true)
    public byte[] renderPdf(Long requestId, String username) {
        SesRequest request = requestService.findById(requestId);
        if (!isPrintable(request)) {
            throw new IllegalStateException(text.get("SES_TXT_LETTER_NOT_PRINTABLE",
                    "Dokumen hanya dapat dicetak setelah seluruh tahap persetujuan selesai."));
        }

        SimpleDateFormat letterDate = new SimpleDateFormat(LETTER_DATE_PATTERN, ID);
        SimpleDateFormat shortDate = new SimpleDateFormat(SHORT_DATE_PATTERN);

        Context ctx = new Context(ID);
        ctx.setVariable("request", request);
        ctx.setVariable("letterDate", letterDate.format(new Date()));
        ctx.setVariable("startPeriod", request.getStartPeriod() == null
                ? "-" : shortDate.format(request.getStartPeriod()));
        ctx.setVariable("finishPeriod", request.getFinishPeriod() == null
                ? "-" : shortDate.format(request.getFinishPeriod()));
        ctx.setVariable("workers", requestService.findWorkers(requestId));
        ctx.setVariable("qrDataUri", qrCodeService.pngDataUriFor(request.getRequestNo()));
        ctx.setVariable("requestorDisplayName",
                userDisplayNameService.displayName(request.getRequestorUserId()));
        ctx.setVariable("contractorName", contractorNameOf(request));
        ctx.setVariable("contractLabel", buildContractLabel(request));
        ctx.setVariable("zoneName", request.getZoneCode() != null
                ? zoneRepository.findById(request.getZoneCode()).map(SesZone::getZoneName).orElse(request.getZoneCode())
                : "-");
        // One signatureSlots() call for both labels. Each resolveSignerByLabel
        // used to run the whole query itself, so the signature lookup ran twice
        // per document for two reads of the same list.
        List<ApprovalService.SignatureSlot> slots = request.getApprovalInstanceId() == null
                ? Collections.<ApprovalService.SignatureSlot>emptyList()
                : approvalService.signatureSlots(request.getApprovalInstanceId());
        ctx.setVariable("procurementSignerName", signerByLabel(slots, "procurement"));
        ctx.setVariable("managerPemohonSignerName", signerByLabel(slots, "manager pemohon"));
        ctx.setVariable("logoDataUri", logoDataUri());

        List<ApprovalService.ApprovedSigner> signers = null;
        if (request.getApprovalInstanceId() != null) {
            signers = approvalService.approvedSigners(request.getApprovalInstanceId());
        }
        if (signers == null) {
            signers = Collections.emptyList();
        }
        ctx.setVariable("approvedSigners", signers);

        String managerPos = null;
        if (request.getApprovalInstanceId() != null || request.getRequestorUserId() != null) {
            managerPos = approvalService.resolveRequestorManagerPosition(
                    request.getApprovalInstanceId(), request.getRequestorUserId());
        }
        if (managerPos == null || managerPos.trim().isEmpty()) {
            managerPos = "Manager Pemohon";
        }
        ctx.setVariable("managerUserPosition", managerPos);
        ctx.setVariable("txt", letterText(contractorNameOf(request), buildContractLabel(request)));

        String html = templateEngine.process("request/print_permohonan", ctx);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // No base URI: the document is fully self-contained by design, and
            // leaving it null makes any accidental external reference fail
            // loudly at render time instead of silently reaching out.
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            // Deliberately broad: OpenHTMLtoPDF throws a variety of unchecked
            // types for malformed markup, and a stack trace in the browser
            // helps nobody. The cause is kept for the log.
            log.error("PDF render failed for request {}", requestId, e);
            throw new IllegalStateException("Gagal membuat dokumen PDF untuk pengajuan "
                    + request.getRequestNo() + ".", e);
        }
    }

    /**
     * Every piece of wording the permit letter renders, resolved from
     * {@code APP_PARAMETER} with the literal that used to be compiled into the
     * template as the fallback.
     *
     * <p>Passed in explicitly because this template is rendered through a
     * plain {@code Context}, not a Spring web context: {@code
     * GlobalModelAttributes} does not run, so {@code appParams} — which every
     * ordinary page can read — is simply absent here. Getting that wrong would
     * have produced a letter of empty clauses rather than an error.
     *
     * <p>{@code BODY} and {@code SUBJECT} carry {@code {0}}/{@code {1}}
     * placeholders so an administrator can move the contractor name and
     * contract label around inside the sentence, which is the whole point of
     * making the sentence editable.
     */
    private Map<String, String> letterText(String contractorName, String contractLabel) {
        Map<String, String> t = new HashMap<>();
        t.put("qrCaption", text.get("SES_TXT_LETTER_QR_CAPTION", "Scan untuk Verifikasi Dokumen"));
        t.put("city", text.get("SES_TXT_BRAND_CITY", "Bontang"));
        t.put("labelTo", text.get("SES_TXT_LETTER_LABEL_TO", "Kepada"));
        t.put("labelFrom", text.get("SES_TXT_LETTER_LABEL_FROM", "Dari"));
        t.put("labelAttachment", text.get("SES_TXT_LETTER_LABEL_ATTACHMENT", "Lampiran"));
        t.put("labelSubject", text.get("SES_TXT_LETTER_LABEL_SUBJECT", "Perihal"));
        t.put("addressee", text.get("SES_TXT_LETTER_ADDRESSEE", "Manager Security"));
        t.put("subject", text.format("SES_TXT_LETTER_SUBJECT",
                "Permohonan Badge Kerja {0}", contractorName));
        t.put("body", text.format("SES_TXT_LETTER_BODY",
                "Sehubungan dengan pelaksanaan kontrak kerja sama dengan kami <span>{0}</span> "
                        + "terkait <strong>{1}</strong> bersama surat ini kami mengajukan permohonan "
                        + "penerbitan kartu identitas (badge) akses area kerja untuk personel pelaksana "
                        + "kami sebagaimana terinci di bawah ini ;",
                contractorName, contractLabel));
        t.put("closing", text.get("SES_TXT_LETTER_CLOSING",
                "Demikian surat permohonan ini kami sampaikan, atas bantuannya kami ucapkan terimakasih."));
        t.put("colNo", text.get("SES_TXT_LETTER_COL_NO", "NO"));
        t.put("colName", text.get("SES_TXT_LETTER_COL_NAME", "NAMA"));
        t.put("colId", text.get("SES_TXT_LETTER_COL_ID", "NO. IDENTITAS (KTP/PASSPORT)"));
        t.put("colRole", text.get("SES_TXT_LETTER_COL_ROLE", "JABATAN"));
        t.put("colPeriod", text.get("SES_TXT_LETTER_COL_PERIOD", "PERIODE"));
        t.put("colZone", text.get("SES_TXT_LETTER_COL_ZONE", "ZONE"));
        t.put("emptyWorkers", text.get("SES_TXT_LETTER_EMPTY_WORKERS", "Tidak ada data pekerja."));
        t.put("signCaption", text.get("SES_TXT_LETTER_SIGN_CAPTION", "Mengetahui,"));
        t.put("companyFormal", text.get("SES_TXT_BRAND_COMPANY_FORMAL", "PT. Badak NGL"));
        t.put("signPlaceholder", text.get("SES_TXT_LETTER_SIGN_PLACEHOLDER", "( ................... )"));
        t.put("signRoleFallback", text.get("SES_TXT_LETTER_SIGN_ROLE_FALLBACK", "Approver"));
        t.put("notPrintable", text.get("SES_TXT_LETTER_NOT_PRINTABLE",
                "Dokumen hanya dapat dicetak setelah seluruh tahap persetujuan selesai."));
        return t;
    }

    private static String contractorNameOf(SesRequest request) {
        return request.getInstance() != null && !request.getInstance().trim().isEmpty()
                ? request.getInstance().trim()
                : "PEMOHON";
    }

    /** Suggested filename for the download, e.g. {@code Permohonan-REQ-2026-0001.pdf}. */
    @Transactional(readOnly = true)
    public String fileNameFor(Long requestId) {
        return fileNameFor(requestService.findById(requestId));
    }

    /**
     * Same name, for a caller that already holds the request.
     *
     * <p>{@code RequestController.documentPdf} fetches the request to check
     * access, renders the PDF, and then asked for the filename by id -- a
     * third read of a row it was already holding, purely to build a string.
     */
    public String fileNameFor(SesRequest request) {
        return "Permohonan-" + request.getRequestNo() + ".pdf";
    }

    /**
     * The letterhead logo as a {@code data:} URI, read from the classpath once.
     *
     * <p>It is a fixed file in the platform jar, so re-reading and re-base64ing
     * it on every render was pure repetition. Cached in a volatile field rather
     * than a static initialiser so a missing file still logs per attempt
     * instead of failing class loading.</p>
     */
    private volatile String cachedLogoDataUri;

    private String logoDataUri() {
        String cached = cachedLogoDataUri;
        if (cached == null) {
            cached = resolveLogoDataUri();
            cachedLogoDataUri = cached;
        }
        return cached;
    }

    private String resolveLogoDataUri() {
        try {
            ClassPathResource res = new ClassPathResource("static/image/ptb_logo.png");
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    byte[] bytes = StreamUtils.copyToByteArray(is);
                    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load Badak logo from classpath for document generation", e);
        }
        return null;
    }

    /** The signer of the step whose {@code roleLabel} contains {@code labelPart}, or null if skipped/absent. */
    private static String signerByLabel(List<ApprovalService.SignatureSlot> slots, String labelPart) {
        for (ApprovalService.SignatureSlot slot : slots) {
            if (slot.getRoleLabel() != null && slot.getRoleLabel().toLowerCase(Locale.ROOT).contains(labelPart)) {
                return slot.isSkipped() ? null : slot.getSignerName();
            }
        }
        return null;
    }

    private static String buildContractLabel(SesRequest request) {
        String title = request.getContractTitle() != null && !request.getContractTitle().trim().isEmpty()
                ? request.getContractTitle().trim() : "-";
        String no = request.getContractNo() != null && !request.getContractNo().trim().isEmpty()
                ? request.getContractNo().trim() : "-";
        return title + " " + no;
    }
}
