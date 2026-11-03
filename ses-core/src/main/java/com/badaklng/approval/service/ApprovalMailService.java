package com.badaklng.approval.service;

import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.SesApprovalTaskAssignee;
import com.badaklng.approval.spi.ApprovalFormDescriptor;
import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.platform.system.service.AppMailService;
import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.ses.user.UserDisplayNameService;
import com.badaklng.ses.async.SesAsyncConfig;
import com.badaklng.ses.mail.SesMailDispatcherService;
import com.badaklng.ses.text.SesText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and queues approval notification emails via the platform's shared
 * mail queue ({@code AppMailService.queue} -> {@code APP_MAIL}, drained
 * immediately via {@link SesMailDispatcherService#dispatchPendingAsync} and
 * backed up by {@code SesMailJob}). Two notification points:
 * <ul>
 *   <li>{@link #sendTaskAssignmentEmail} — a new step activates; notify its
 *       resolved assignees they have something to approve. Called directly
 *       from {@link ApprovalService#activateTask} — there is no existing SPI
 *       for "task activated", unlike the outcome case below.</li>
 *   <li>{@link #sendOutcomeEmail} — an instance finishes (APPROVED/REJECTED);
 *       notify the requestor. Called from {@code ApprovalRequestorNotifier}
 *       (an {@link com.badaklng.approval.spi.ApprovalOutcomeListener}), which
 *       reuses the SAME {@code notifyOutcomeListeners(...)} call sites
 *       {@code ApprovalService} already has for every business module — no
 *       edit to {@code ApprovalService} was needed for this half.</li>
 * </ul>
 * Both bodies carry the submission's own details, pulled per-module through
 * {@link ApprovalFormDescriptor#emailDetail} — the two forms share almost no
 * fields (a sticker has no contract or zone), so the detail block cannot be
 * a fixed list here.
 * <p>
 * The dev/test safety valve that keeps real employee inboxes out of test
 * traffic is NOT here — it lives in {@code SesMailJob}, which applies the
 * {@code TESTING_MODE}/{@code REDIRECT_MAIL}/{@code ADMIN_EMAIL}
 * {@code APP_PARAMETER}s at send time and therefore covers every outbound
 * mail, not just this module's.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalMailService {

    private final AppMailService appMailService;
    private final AppUserRepository appUserRepository;
    private final AppParameterService appParameterService;
    private final ApprovalFormService formService;
    private final ApprovalEmailTemplateBuilder templateBuilder;
    private final UserDisplayNameService userDisplayNameService;
    private final SesMailDispatcherService mailDispatcherService;
    private final SesText text;

    @Value("${ses.mail.approval.enabled:true}")
    private boolean enabled;

    @Value("${ses.app.base-url:http://localhost:8082}")
    private String fallbackBaseUrl;

    /** Notifies every resolved assignee of a newly-activated task asynchronously. */
    @Async(SesAsyncConfig.TASK_EXECUTOR_NAME)
    public void sendTaskAssignmentEmail(SesApprovalInstance instance, SesApprovalTask task,
                                         List<SesApprovalTaskAssignee> assignees) {
        if (!enabled || assignees == null || assignees.isEmpty()) {
            return;
        }
        if (assignees.stream().noneMatch(a -> notBlank(a.getEmail()))) {
            log.warn("Task {} activated but no assignee has an email address; no notification sent", task.getTaskId());
            return;
        }

        String requestorName = displayName(instance.getRequestorUserId());
        String transactionId = transactionId(instance);
        // The form title is deliberately NOT in the subject: it is already a
        // card row, and an inbox list shows roughly 60 characters before it
        // truncates — spending them on a value the reader sees anyway costs
        // the transaction number, which is the one thing they scan for.
        // {0} transaction number, {1} the " dari <name>" suffix (empty when the
        // requestor name is unknown -- the suffix is built here rather than
        // stored so an un-named submission does not leave a dangling "dari").
        String subject = text.format("SES_TXT_MAIL_SUBJECT_ASSIGNED",
                "[SES] Persetujuan diperlukan - {0}{1}",
                transactionId,
                notBlank(requestorName) ? " dari " + requestorName : "");

        // content() is hoisted out of the loop: everything it builds is the
        // same for every assignee, but it reads the form descriptor three
        // times (transactionId, formTitle, emailDetail) and emailDetail in
        // particular walks the whole business record. Inside the loop that was
        // the same set of reads repeated per recipient.
        ApprovalEmailTemplateBuilder.Content shared = content(instance, ApprovalStatus.PENDING);
        shared.requestorName = requestorName;
        shared.stepName = task.getStepName();

        boolean queued = false;
        for (SesApprovalTaskAssignee a : assignees) {
            if (!notBlank(a.getEmail())) {
                continue;
            }
            // Only the greeting differs per recipient, and render() reads the
            // Content without mutating it, so one instance is reused.
            shared.greetingName = a.getDisplayName();
            enqueue(a.getEmail().trim(), subject, render(shared),
                    "APPROVAL_TASK_ASSIGNED:" + task.getTaskId());
            queued = true;
        }
        // One drain trigger for the batch, not one per recipient -- every
        // trigger used to submit its own task to the async pool, and they all
        // contended for the same drain.
        if (queued) {
            triggerDispatch();
        }
    }

    /**
     * Notifies the requestor once an instance finishes asynchronously.
     *
     * <p>Only APPROVED and REJECTED are mailed. {@code ApprovalStatus} has a
     * fourth value, CANCELLED, and without this guard it would render on the
     * template's default branch — a cancelled submission announcing itself as
     * "menunggu persetujuan Anda".</p>
     */
    @Async(SesAsyncConfig.TASK_EXECUTOR_NAME)
    public void sendOutcomeEmail(SesApprovalInstance instance, ApprovalStatus finalStatus,
                                  String note, String actorUsername) {
        if (!enabled) {
            return;
        }
        if (finalStatus != ApprovalStatus.APPROVED && finalStatus != ApprovalStatus.REJECTED) {
            return;
        }
        String to = requestorEmail(instance.getRequestorUserId());
        if (to == null) {
            log.warn("Instance {} finished {} but requestor '{}' has no email on file; no notification sent",
                    instance.getInstanceId(), finalStatus, instance.getRequestorUserId());
            return;
        }

        ApprovalEmailTemplateBuilder.Content c = content(instance, finalStatus);
        c.greetingName = displayName(instance.getRequestorUserId());
        c.requestorName = c.greetingName;
        c.actorName = notBlank(actorUsername) ? displayName(actorUsername) : null;
        c.catatan = note;
        c.actionPath = requestorActionPath(instance);

        String subject = text.format("SES_TXT_MAIL_SUBJECT_OUTCOME",
                "[SES] Pengajuan {0} {1}",
                c.nomorPengajuan,
                finalStatus == ApprovalStatus.APPROVED
                        ? text.get("SES_TXT_MAIL_SUBJECT_WORD_APPROVED", "disetujui")
                        : text.get("SES_TXT_MAIL_SUBJECT_WORD_REJECTED", "ditolak"));
        queue(to, subject, render(c), "APPROVAL_OUTCOME:" + instance.getInstanceId());

        sendOutcomeEmailToExt(instance, finalStatus, note, subject);
    }

    /**
     * Same outcome mail, also to the "Email Penerima Dokumen" captured at
     * submission — if the module has one and it is filled in. No "Lihat
     * Pengajuan" button: that page sits on the intranet, unreachable from
     * outside, so {@code actionPath} is left null and
     * {@link ApprovalEmailTemplateBuilder} drops the button entirely.
     */
    private void sendOutcomeEmailToExt(SesApprovalInstance instance, ApprovalStatus finalStatus,
                                       String note, String subject) {
        String extEmail = formService.findDescriptor(instance.getFormCode())
                .map(d -> d.extNotifyEmail(instance.getBusinessRefId()))
                .filter(ApprovalMailService::notBlank)
                .orElse(null);
        if (extEmail == null) {
            return;
        }
        ApprovalEmailTemplateBuilder.Content c = content(instance, finalStatus);
        c.catatan = note;
        c.actionPath = null;
        queue(extEmail.trim(), subject, render(c), "APPROVAL_OUTCOME_EXT:" + instance.getInstanceId());
    }

    /**
     * Notifies the requestor about something that is not a decision — a
     * screening or badge-retrieval date being set, moved or cancelled, or the
     * whole process finishing.
     *
     * <p>Same card as every other approval mail, with the headline, lead
     * sentence and Status value overridden; {@code palette} only picks the
     * colour and badge glyph (APPROVED = green tick, PENDING = amber
     * hourglass). Extra rows are merged into the form's own detail rows, so
     * keep them few — {@link ApprovalEmailTemplateBuilder#MAX_BODY} is the
     * ceiling and {@link #render} silently drops to plain text past it.</p>
     *
     * <p>Queued like everything else, never sent inline: callers run inside a
     * business transaction and an SMTP timeout must not roll back a schedule
     * that was genuinely created.</p>
     *
     * <p>{@code @Async} like its two siblings above. It was the only one of
     * the three without it, so while the SMTP send did escape the request
     * thread (through the nested {@code dispatchPendingAsync()}), everything
     * before it did not: the requestor-email lookup, the descriptor reads
     * inside {@link #content}, the body render and the REQUIRES_NEW insert
     * all ran inline on the HTTP thread. The javadoc above was true of the
     * send and not of the work around it.</p>
     */
    @Async(SesAsyncConfig.TASK_EXECUTOR_NAME)
    public void sendRequestorInfoEmail(SesApprovalInstance instance, ApprovalStatus palette,
                                       String headline, String intro, String statusLabel,
                                       Map<String, String> extraDetail, String note, String mailKeySuffix) {
        if (!enabled) {
            return;
        }
        String to = requestorEmail(instance.getRequestorUserId());
        if (to == null) {
            log.warn("Instance {} info mail '{}' not sent: requestor '{}' has no email on file",
                    instance.getInstanceId(), headline, instance.getRequestorUserId());
            return;
        }

        ApprovalEmailTemplateBuilder.Content c = content(instance, palette);
        c.greetingName = displayName(instance.getRequestorUserId());
        c.requestorName = c.greetingName;
        c.catatan = note;
        c.actionPath = requestorActionPath(instance);
        c.headlineOverride = headline;
        c.introOverride = intro;
        c.statusLabelOverride = statusLabel;
        if (extraDetail != null && !extraDetail.isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>(c.detail);
            merged.putAll(extraDetail);
            c.detail = merged;
        }

        String subject = text.format("SES_TXT_MAIL_SUBJECT_INFO",
                "[SES] {0} - {1}", headline, c.nomorPengajuan);
        queue(to, subject, render(c), mailKeySuffix + ":" + instance.getInstanceId());
    }

    /** The parts of the mail that are the same for every status. */
    private ApprovalEmailTemplateBuilder.Content content(SesApprovalInstance instance, ApprovalStatus status) {
        ApprovalEmailTemplateBuilder.Content c = new ApprovalEmailTemplateBuilder.Content();
        c.status = status;
        c.nomorPengajuan = transactionId(instance);
        c.typePengajuan = formTitle(instance);
        c.detail = emailDetail(instance);
        c.baseUrl = baseUrl();
        c.actionPath = "/approval/detail/" + instance.getInstanceId();
        return c;
    }

    /**
     * HTML unless it would overrun the body column, in which case the plain
     * text variant keeps the information and drops the design. Logged, because
     * a silent downgrade to text is exactly the kind of thing that goes
     * unnoticed until someone asks why one form's mail looks different.
     */
    private String render(ApprovalEmailTemplateBuilder.Content c) {
        String html = templateBuilder.buildHtml(c);
        if (html.length() <= ApprovalEmailTemplateBuilder.MAX_BODY) {
            return html;
        }
        log.warn("Mail body for {} is {} chars (> {}); falling back to plain text",
                c.nomorPengajuan, html.length(), ApprovalEmailTemplateBuilder.MAX_BODY);
        return templateBuilder.buildText(c);
    }

    /**
     * Absolute app root for the button and the logo.
     *
     * <p>{@code APP_LINK} is the operator-facing knob (editable live at
     * {@code /admin/parameter}, so a move between environments needs no
     * redeploy); the property is only a fallback for a schema where that row
     * was never seeded. Read through the blank-safe dance because
     * {@code AppParameterService.getValue} maps a NULL {@code param_value} to
     * {@code ""} and returns that empty string rather than the default it was
     * given — the same reason {@code SesMailJob.param} exists.</p>
     */
    private String baseUrl() {
        String v = appParameterService.getValue("APP_LINK", "");
        return notBlank(v) ? v.trim() : fallbackBaseUrl;
    }

    /**
     * Where the outcome mail's button sends the requestor: their own
     * read-only page when the module exposes one, else the approver screen
     * as before.
     *
     * <p>{@code /approval/detail/{id}} is the approver's workspace — an
     * Approve/Reject panel, a checkpoint list, a step timeline. The requestor
     * can open it (REQUESTOR holds READ on /approval, see
     * sql/30_menu_reorg_rbac_sync_seed.sql), which is why this was never a
     * visible error; it was just the wrong page to land a requestor on.
     * {@link #sendTaskAssignmentEmail} keeps pointing there, because that
     * screen is precisely what its reader was asked to use.</p>
     */
    private String requestorActionPath(SesApprovalInstance instance) {
        return formService.findDescriptor(instance.getFormCode())
                .map(d -> d.requestorViewPath(instance.getBusinessRefId()))
                .filter(ApprovalMailService::notBlank)
                .orElse("/approval/detail/" + instance.getInstanceId());
    }

    private Map<String, String> emailDetail(SesApprovalInstance instance) {
        try {
            return formService.findDescriptor(instance.getFormCode())
                    .map(d -> d.emailDetail(instance.getBusinessRefId()))
                    .orElse(Collections.emptyMap());
        } catch (RuntimeException e) {
            // A missing business row must not stop the notification: the mail
            // still carries number, type, requestor and a link.
            log.warn("Could not load email detail for {} #{}: {}",
                    instance.getFormCode(), instance.getBusinessRefId(), e.toString());
            return Collections.emptyMap();
        }
    }

    private String formTitle(SesApprovalInstance instance) {
        return formService.findDescriptor(instance.getFormCode())
                .map(ApprovalFormDescriptor::formName)
                .orElse(instance.getBusinessRefType());
    }

    /** Queues one mail and asks the dispatcher to drain -- the single-recipient path. */
    private void queue(String to, String subject, String body, String key) {
        enqueue(to, subject, body, key);
        triggerDispatch();
    }

    /** INSERT into APP_MAIL only. Use with {@link #triggerDispatch} when queueing several. */
    private void enqueue(String to, String subject, String body, String key) {
        appMailService.queue(to, null, null, subject, body, key);
    }

    /**
     * Asks the dispatcher to drain now. Safe to call once for a whole batch:
     * the drain reads every pending row, and a second concurrent trigger is
     * skipped rather than queued (see {@code SesMailDispatcherService}).
     */
    private void triggerDispatch() {
        if (mailDispatcherService != null) {
            mailDispatcherService.dispatchPendingAsync();
        }
    }

    private String transactionId(SesApprovalInstance instance) {
        return formService.findDescriptor(instance.getFormCode())
                .map(d -> {
                    String id = d.transactionId(instance.getBusinessRefId());
                    return id != null ? id : instance.getBusinessRefType() + " #" + instance.getBusinessRefId();
                })
                .orElse(instance.getBusinessRefType() + " #" + instance.getBusinessRefId());
    }

    /**
     * Real name for a user reference, falling back to the reference itself.
     *
     * <p>Now {@link UserDisplayNameService}'s job: this used to be the only
     * correct implementation in the app, which is exactly why it had to stop
     * living here — the pages this mail links to were rendering raw usernames
     * for the same people.</p>
     */
    private String displayName(String userRef) {
        return userDisplayNameService.displayName(userRef);
    }

    private String requestorEmail(String requestorUserId) {
        Optional<AppUser> user = appUserRepository.findByUsername(requestorUserId);
        return user.map(AppUser::getEmail).filter(ApprovalMailService::notBlank).orElse(null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
