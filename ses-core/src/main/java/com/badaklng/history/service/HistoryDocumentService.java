package com.badaklng.history.service;

import com.badaklng.history.model.HistoryRow;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

/**
 * The printable Riwayat list sheet: every row in one PDF, behind the
 * "Cetak Semua" button shown to whichever role has org-wide (SUBMIT) access
 * to /history. Same rendering recipe as
 * {@code RequestDocumentService} (Thymeleaf -> HTML -> OpenHTMLtoPDF,
 * no base URI — the document is fully self-contained).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoryDocumentService {

    private final TemplateEngine templateEngine;

    public byte[] renderListPdf(List<HistoryRow> rows) {
        Context ctx = new Context(new Locale("id", "ID"));
        ctx.setVariable("rows", rows);

        String html = templateEngine.process("history/print_list", ctx);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render history list PDF", e);
            throw new IllegalStateException("Gagal membuat dokumen PDF riwayat.", e);
        }
    }
}
