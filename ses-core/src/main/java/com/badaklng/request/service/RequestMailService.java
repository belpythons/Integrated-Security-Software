package com.badaklng.request.service;

import com.badaklng.platform.system.service.AppMailService;
import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.ses.async.SesAsyncConfig;
import com.badaklng.ses.mail.SesMailDispatcherService;
import com.badaklng.ses.text.SesText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outgoing mail owned by the request module.
 *
 * <p>Today that is one email: the document-upload link. It lives here rather
 * than in {@code ApprovalMailService} because nothing about it is an approval
 * — there is no instance, no task, no decision, and at the moment it is sent
 * the approval chain has deliberately <b>not</b> been triggered.</p>
 *
 * <p>Queued and dispatched asynchronously via {@link SesMailDispatcherService}.
 * {@code AppMailService.queue} runs {@code REQUIRES_NEW}, so the row survives
 * even if the surrounding submit transaction rolls back — and conversely an
 * SMTP server that is down cannot roll back a submission that genuinely
 * happened. {@code SesMailJob} acts as the Quartz safety net.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestMailService {

    /** Where the token link points. Must match {@code RequestUploadController}. */
    static final String UPLOAD_PATH = "/request/upload-docs?token=";

    private final AppMailService appMailService;
    private final AppParameterService appParameterService;
    private final SesRequestTypeRepository requestTypeRepository;
    private final RequestEmailTemplateBuilder templateBuilder;
    private final SesMailDispatcherService mailDispatcherService;
    private final SesText text;

    /**
     * Same switch the approval mails honour, so one flag silences the whole
     * application in an environment where outgoing mail is not wanted.
     */
    @Value("${ses.mail.approval.enabled:true}")
    private boolean enabled;

    /** External base URL used when APP_LINK_EXT is unset; points to ses-ext gateway. */
    @Value("${ses.app.base-url-ext:http://localhost:8083/ses-ext}")
    private String fallbackBaseUrlExt;

    /** Only reached when APP_LINK is unset; see {@link #baseUrl()}. */
    @Value("${ses.app.base-url:http://localhost:8082}")
    private String fallbackBaseUrl;

    /**
     * Queues the "please upload the documents" mail for one request asynchronously.
     *
     * <p>Returns quietly when mail is disabled or the request carries no
     * address. A warning plus a request they can resend the link from is the better failure.</p>
     */
    @Async(SesAsyncConfig.TASK_EXECUTOR_NAME)
    @Transactional
    public void sendUploadDocumentsEmail(SesRequest request, String token) {
        if (!enabled) {
            log.info("Mail disabled; not sending upload link for {}", request.getRequestNo());
            return;
        }
        String to = request.getEmail() == null ? null : request.getEmail().trim();
        if (to == null || to.isEmpty()) {
            log.warn("Request {} has no recipient address; upload link not sent",
                    request.getRequestNo());
            return;
        }

        RequestEmailTemplateBuilder.Content c = new RequestEmailTemplateBuilder.Content();
        c.greetingName = request.getInstance();
        c.requestNo = request.getRequestNo();
        c.typeName = typeName(request.getTypeCode());
        c.contractNo = request.getContractNo();
        c.contractType = request.getContractType();
        c.contractTitle = request.getContractTitle();
        c.periodContract = periodLabel(request.getStartPeriod(), request.getFinishPeriod());
        c.baseUrl = baseUrl();
        c.uploadUrl = uploadUrl(token);
        c.validDays = UploadTokenService.VALID_DAYS;

        // Subject carries no "[TESTING]" prefix of its own: SesMailJob
        // prepends [DEV -> recipient] when TESTING_MODE is active at send time.
        String subject = text.format("SES_TXT_MAIL_SUBJECT_UPLOAD",
                "[SES] Badge Request - Documents Required - {0}", request.getRequestNo());

        appMailService.queue(to, null, null, subject, render(c),
                "REQUEST_UPLOAD_DOCS:" + request.getRequestId());

        if (mailDispatcherService != null) {
            mailDispatcherService.dispatchPendingAsync();
        }

        log.info("Queued and dispatched upload-link mail for {} to {}", request.getRequestNo(), to);
    }

    /**
     * HTML normally; plain text when the HTML would be truncated.
     *
     * <p>{@code app_mail.mail_body} is {@code VARCHAR2(4000)} and the queue
     * truncates silently, which for HTML means losing the closing tags. The
     * fallback drops the design rather than the information, and the downgrade
     * is logged so it is visible rather than merely survivable.</p>
     */
    private String render(RequestEmailTemplateBuilder.Content c) {
        String html = templateBuilder.buildHtml(c);
        if (html.length() <= RequestEmailTemplateBuilder.MAX_BODY) {
            return html;
        }
        log.warn("Upload-link mail body for {} is {} chars (> {}); falling back to plain text",
                c.requestNo, html.length(), RequestEmailTemplateBuilder.MAX_BODY);
        return templateBuilder.buildText(c);
    }

    /** The absolute link that goes in the mail — also useful on screen. */
    public String uploadUrl(String token) {
        return baseUrl() + UPLOAD_PATH + (token == null ? "" : token.trim());
    }

    /**
     * Reads {@code APP_LINK_EXT} first, then {@code APP_LINK}, and finally the
     * Spring property {@code ses.app.base-url} when both rows are blank.
     *
     * <p>{@code APP_LINK_EXT} is dedicated for external document uploads so that
     * external-facing URLs (via DMZ / internet / reverse proxy) can be configured
     * independently from intranet portal links.</p>
     */
    private String baseUrl() {
        String v = appParameterService.getValue("APP_LINK_EXT", "");
        if (v != null && !v.trim().isEmpty()) {
            return RequestEmailTemplateBuilder.stripTrailingSlash(v);
        }
        if (fallbackBaseUrlExt != null && !fallbackBaseUrlExt.trim().isEmpty()) {
            return RequestEmailTemplateBuilder.stripTrailingSlash(fallbackBaseUrlExt);
        }
        v = appParameterService.getValue("APP_LINK", "");
        String base = (v != null && !v.trim().isEmpty()) ? v : fallbackBaseUrl;
        return RequestEmailTemplateBuilder.stripTrailingSlash(base);
    }

    private String typeName(String typeCode) {
        if (typeCode == null) {
            return null;
        }
        return requestTypeRepository.findById(typeCode)
                .map(t -> t.getTypeName())
                .orElse(typeCode);
    }

    /** SimpleDateFormat is not thread-safe, so this is a pattern rather than a shared instance -- two concurrent requests sharing one formatter mutate its internal Calendar mid-format, and the result is a silently wrong date, not an exception. */
    private static final String PERIOD_DATE_PATTERN = "dd-MM-yyyy";

    /** "dd-MM-yyyy — dd-MM-yyyy", or null if neither end is set. */
    private String periodLabel(java.util.Date start, java.util.Date finish) {
        if (start == null && finish == null) {
            return null;
        }
        return (start == null ? "-" : new java.text.SimpleDateFormat(PERIOD_DATE_PATTERN).format(start))
                + " — " + (finish == null ? "-" : new java.text.SimpleDateFormat(PERIOD_DATE_PATTERN).format(finish));
    }
}
