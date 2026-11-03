package com.badaklng.request.service;

import com.badaklng.platform.system.service.AppParameterService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the QR code printed on a badge permit sheet.
 *
 * <p><b>The QR carries a URL, not the request's data.</b> It encodes
 * {@code {APP_LINK}/verify/{requestNo}} and nothing else.</p>
 *
 * <p>An embedded payload was considered and rejected for two reasons:</p>
 * <ol>
 *   <li><b>An embedded payload is frozen.</b> A request cancelled yesterday
 *       would still scan as "approved" today from a sheet printed last week —
 *       precisely the case a guard at the gate needs to catch. A URL sends the
 *       scanner to a page that reads the current status from the database.</li>
 *   <li><b>Encryption needs key management</b> this application does not have:
 *       key storage, rotation, and a way to roll one without invalidating every
 *       printed sheet. All to hide data that is printed in the clear on the
 *       same piece of paper.</li>
 * </ol>
 *
 * <p>{@code APP_LINK} already exists in {@code app_parameter} (seeded by
 * {@code sql/39_app_link_production.sql}) and is already read the same way by
 * {@code ApprovalMailService#baseUrl}.</p>
 */
@Service
@RequiredArgsConstructor
public class RequestQrCodeService {

    private static final int SIZE_PX = 340;

    private final AppParameterService appParameterService;

    @Value("${ses.app.base-url:http://localhost:8082}")
    private String fallbackBaseUrl;

    /**
     * The URL a scan resolves to — also useful on screen, so callers can show it as text.
     *
     * <p>Reads {@code APP_LINK_EXT} first, then {@code APP_LINK}, and finally the
     * application properties fallback. {@code APP_LINK_EXT} is dedicated for external-facing
     * public pages like verification and contractor document uploads so that
     * links scanned by external devices point to the publicly accessible gateway/domain
     * instead of an internal-only intranet address.</p>
     */
    public String verifyUrl(String requestNo) {
        String base = appParameterService.getValue("APP_LINK_EXT", "");
        if (base == null || base.trim().isEmpty()) {
            base = appParameterService.getValue("APP_LINK", "");
        }
        if (base == null || base.trim().isEmpty()) {
            base = fallbackBaseUrl;
        }
        base = base.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/verify/" + requestNo;
    }

    /**
     * PNG bytes of the QR for one request.
     *
     * <p>Error correction is set to {@code M}: these sheets get folded, stamped
     * and handled at a gate, and the default {@code L} tolerates far less
     * smudging. {@code H} would be sturdier still but makes the pattern denser,
     * which hurts more than it helps at this print size.</p>
     */
    public byte[] pngFor(String requestNo) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(verifyUrl(requestNo), BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX, hints);

            // This loop is the whole reason com.google.zxing:javase is not a
            // dependency — MatrixToImageWriter does exactly this and nothing
            // else this application needs.
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Gagal membuat QR code untuk " + requestNo, e);
        }
    }

    /**
     * The same PNG as a {@code data:} URI.
     *
     * <p>The printed sheet is rendered by OpenHTMLtoPDF from an HTML string
     * with no servlet context behind it — it cannot fetch
     * {@code /request/{id}/qr.png} over HTTP, and giving it a URI resolver that
     * could would mean the PDF renderer making authenticated calls back into
     * the application mid-render. Inlining the bytes avoids the whole
     * question.</p>
     */
    public String pngDataUriFor(String requestNo) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngFor(requestNo));
    }
}
