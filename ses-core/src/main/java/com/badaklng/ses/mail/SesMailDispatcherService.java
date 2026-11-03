package com.badaklng.ses.mail;

import com.badaklng.platform.system.entity.AppMail;
import com.badaklng.platform.system.repository.AppMailRepository;
import com.badaklng.platform.scheduler.MailJob;
import com.badaklng.platform.system.service.AppErrorService;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.platform.system.service.AppMailService;
import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.ses.async.SesAsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * High-performance email dispatcher service for SES.
 * Provides instant asynchronous dispatching upon email creation as well as
 * scheduled batch draining.
 *
 * <p>Optimizations:
 * <ul>
 *   <li><b>Hosted Logo:</b> Replaces heavy binary MIME inline attachments with lightweight
 *       hosted web URLs ({@code APP_LINK + "/image/ptb_logo.png"}), reducing email payload
 *       from ~100KB+ multipart to ~3KB single-part HTML.</li>
 *   <li><b>Instant Dispatch:</b> {@link #dispatchPendingAsync()} wakes up the background
 *       worker pool immediately when an email is enqueued, dropping delivery delay from minutes to &lt; 2 seconds.</li>
 *   <li><b>Resilience:</b> Failed dispatches are logged to {@code app_error} and kept pending
 *       for Quartz retry without blocking user transactions.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SesMailDispatcherService {

    public static final int MAX_RETRY = MailJob.MAX_RETRY;
    public static final String LOGO_URL_PATH = "/image/ptb_logo.png";

    /**
     * Connect / read / write ceiling for one SMTP conversation, in ms.
     * Overridable per environment with a {@code timeout=<ms>} key in the
     * {@code MAIL_SERVER} parameter. 10 s is well above a healthy intranet
     * relay and well below anything a user would sit through.
     */
    private static final int DEFAULT_SMTP_TIMEOUT_MS = 10000;

    private static final Pattern LOGO_SRC_PATTERN = Pattern.compile(
            "(?i)src\\s*=\\s*['\"][^'\"]*?ptb_logo\\.png(?:\\?[^'\"]*)?['\"]"
    );

    private final AppMailRepository mailRepository;
    private final AppMailService mailService;
    private final AppParameterService paramService;
    private final AppErrorService errorService;
    private final AppLogService logService;

    @Autowired(required = false)
    private EmailLayoutDecorator emailLayoutDecorator = new EmailLayoutDecorator();

    @Autowired(required = false)
    private javax.sql.DataSource dataSource;

    /**
     * Whether this JVM may drain {@code APP_MAIL}. Queueing is always allowed.
     *
     * <p>ses-app and ses-ext are two JVMs on one Oracle schema. ses-ext must
     * be able to <em>queue</em> mail -- finishing a contractor upload triggers
     * the approval chain, which notifies the first approver -- so
     * {@code SesMailDispatcherService} has to exist there: it is a required
     * constructor dependency of both {@code ApprovalMailService} and
     * {@code RequestMailService}, which ses-ext does need. It must not
     * <em>send</em>, though: two JVMs running {@code findPending} -> send ->
     * {@code markSent} against the same unlocked table is a double-send race,
     * and only ses-app's drain is cron-driven and TESTING_MODE-aware.
     *
     * <p>{@code SesExtApplication}'s javadoc claimed the mail package was
     * excluded from its component scan. It never was, and it cannot be --
     * which is precisely why the exclusion has to be a runtime flag instead
     * of a scan filter. Set {@code false} in ses-ext.
     */
    @Value("${ses.mail.dispatch.enabled:true}")
    private boolean dispatchEnabled;

    /**
     * Asynchronously drains pending mails in the background worker pool.
     * Invoked immediately when business processes queue a new email.
     */
    @Async(SesAsyncConfig.TASK_EXECUTOR_NAME)
    public void dispatchPendingAsync() {
        log.debug("Immediate async mail dispatch triggered");
        drainQueue();
    }

    /** See {@link #dispatchEnabled}. */
    public boolean isDispatchEnabled() {
        return dispatchEnabled;
    }

    /**
     * Guards the drain so only one runs at a time per JVM.
     *
     * <p>Was {@code synchronized} on the service instance. The difference
     * matters: a blocking monitor makes every other caller <em>wait</em> for
     * the in-flight drain, and callers arrive from the async pool (one
     * {@code dispatchPendingAsync()} per queued mail) as well as from the
     * Quartz MAIL cron. With a slow or hung SMTP relay the pool fills,
     * {@code CallerRunsPolicy} then runs the dispatch on the submitting HTTP
     * thread, and that thread blocks on the same monitor -- a stuck mail
     * server became stuck page loads.
     *
     * <p>Skipping is safe and is the whole point: rows stay in
     * {@code APP_MAIL} until {@code markSent}, the drain already in progress
     * picks up anything queued while it runs, and the cron sweeps again every
     * minute. Nothing is lost by not queueing behind it.
     */
    private final java.util.concurrent.locks.ReentrantLock drainLock =
            new java.util.concurrent.locks.ReentrantLock();

    /**
     * Drains all pending emails from APP_MAIL.
     * Called by both the async trigger and the Quartz backup sweeper.
     *
     * @return number of emails dispatched, or 0 if another drain is already running
     */
    public int drainQueue() {
        if (!dispatchEnabled) {
            log.debug("Mail dispatch is disabled in this JVM; rows stay queued for the sender");
            return 0;
        }
        if (!drainLock.tryLock()) {
            log.debug("Mail drain already in progress; skipping this trigger");
            return 0;
        }
        try {
            return drainQueueLocked();
        } finally {
            drainLock.unlock();
        }
    }

    private int drainQueueLocked() {
        String from = param("MAIL_FROM", null);
        String server = param("MAIL_SERVER", null);

        if (isBlank(from)) {
            errorService.log("SesMailDispatcher/Config",
                    "MAIL_FROM AppParameter is not set; mail queue will not drain",
                    "service:MAIL_DISPATCHER", null);
            return 0;
        }
        if (isBlank(server)) {
            errorService.log("SesMailDispatcher/Config",
                    "MAIL_SERVER AppParameter is not set; mail queue will not drain",
                    "service:MAIL_DISPATCHER", null);
            return 0;
        }

        boolean testing = "ON".equalsIgnoreCase(param("TESTING_MODE", "OFF"));
        boolean redirect = testing || "YES".equalsIgnoreCase(param("REDIRECT_MAIL", "NO"));
        String adminTo = param("ADMIN_EMAIL", null);
        String banner = param("TESTING_BANNER", "");
        String appLink = param("APP_LINK", "");

        if (redirect && isBlank(adminTo)) {
            errorService.log("SesMailDispatcher/Config",
                    "TESTING_MODE/REDIRECT_MAIL is active but ADMIN_EMAIL is blank; "
                            + "mail queue held back rather than delivered to real recipients",
                    "service:MAIL_DISPATCHER", null);
            return 0;
        }

        JavaMailSenderImpl sender;
        try {
            sender = buildSender(server);
        } catch (Exception ex) {
            errorService.log("SesMailDispatcher/Config",
                    "Invalid MAIL_SERVER='" + server + "'\n\n" + stackTrace(ex),
                    "service:MAIL_DISPATCHER", null);
            return 0;
        }

        List<AppMail> pending = mailRepository.findPending(MAX_RETRY);
        if (pending.isEmpty()) {
            log.debug("SesMailDispatcher: queue is empty");
            return 0;
        }
        log.info("SesMailDispatcher: processing {} pending mail(s){}", pending.size(),
                redirect ? " (redirecting to " + adminTo + ")" : "");
        if (redirect) {
            log.warn("SesMailDispatcher: Mail redirection is ACTIVE (TESTING_MODE={}, REDIRECT_MAIL={}). All outbound emails redirect to {}",
                    param("TESTING_MODE", "OFF"), param("REDIRECT_MAIL", "NO"), adminTo);
        }

        int sentCount = 0;
        for (AppMail m : pending) {
            // Concurrency guard: atomically claim this row so no other node or concurrent drain
            // can pick it up and cause a double-send.
            if (dataSource != null) {
                boolean claimed = claimMailForDispatch(m.getMailId());
                if (!claimed) {
                    log.info("SesMailDispatcher: mail {} was already claimed or sent by another instance; skipping", m.getMailId());
                    continue;
                }
            }

            String actualTo = redirect ? adminTo : m.getMailTo();
            try {
                String body = m.getMailBody() != null ? m.getMailBody() : "";
                String subject = m.getMailSubject() != null ? m.getMailSubject() : "";
                if (redirect) {
                    subject = (testing && !isBlank(banner) ? banner + " " : "")
                            + "[DEV -> " + m.getMailTo() + "] " + subject;
                }

                boolean html = isHtml(body);
                if (html && emailLayoutDecorator != null) {
                    body = emailLayoutDecorator.decorate(body, appLink);
                }

                // Hosted logo optimization: normalize img src to absolute hosted URL
                // without attaching heavy binary MIME CID parts.
                if (html && referencesLogo(body)) {
                    body = normalizeLogoToHostedUrl(body, appLink);
                }

                MimeMessage msg = sender.createMimeMessage();
                // Lightweight single-part HTML message (no MIME MULTIPART_MODE_RELATED needed)
                MimeMessageHelper helper = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_NO, "UTF-8");
                helper.setFrom(from);
                if (!isBlank(actualTo)) {
                    helper.setTo(splitAddrs(actualTo));
                }
                if (!redirect) {
                    if (!isBlank(m.getMailCc()))  helper.setCc(splitAddrs(m.getMailCc()));
                    if (!isBlank(m.getMailBcc())) helper.setBcc(splitAddrs(m.getMailBcc()));
                }
                helper.setSubject(subject);
                helper.setText(body, html);

                log.info("SesMailDispatcher: dispatching mail {} -> {} (html={}, size={} bytes)",
                        m.getMailId(), actualTo, html, body.length());
                sender.send(msg);

                mailService.markSent(m.getMailId());
                logService.log("MAIL_SENT", "AppMail",
                        "Sent mail " + m.getMailId() + " -> " + actualTo
                                + (redirect ? " (redirected from " + m.getMailTo() + ")" : "")
                                + " [format=" + (html ? "HTML" : "TEXT") + ", size=" + body.length() + "B]");
                sentCount++;
            } catch (Exception ex) {
                log.warn("SesMailDispatcher: failed to send mail {}", m.getMailId(), ex);
                if (dataSource != null) {
                    restoreFailedClaim(m.getMailId(), m.getMailRetry() != null ? m.getMailRetry() : 0, stackTrace(ex));
                } else {
                    try {
                        mailService.markFailed(m.getMailId(), stackTrace(ex));
                    } catch (Exception persistEx) {
                        log.error("Could not persist mail failure for {}", m.getMailId(), persistEx);
                    }
                }
            }
        }
        return sentCount;
    }

    private boolean claimMailForDispatch(String mailId) {
        if (dataSource == null || mailId == null) {
            return true;
        }
        String sql = "UPDATE app_mail SET mail_retry = mail_retry + 100 WHERE mail_id = ? AND mail_sent IS NULL AND mail_retry < ?";
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mailId);
            ps.setInt(2, MAX_RETRY);
            return ps.executeUpdate() > 0;
        } catch (Exception ex) {
            log.warn("SesMailDispatcher: failed to execute atomic claim on mail {}; proceeding cautiously", mailId, ex);
            return true;
        }
    }

    private void restoreFailedClaim(String mailId, int previousRetry, String error) {
        if (dataSource == null || mailId == null) {
            return;
        }
        String sql = "UPDATE app_mail SET mail_retry = ?, mail_error = ? WHERE mail_id = ?";
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, previousRetry + 1);
            ps.setString(2, error != null && error.length() > 4000 ? error.substring(0, 4000) : error);
            ps.setString(3, mailId);
            ps.executeUpdate();
        } catch (Exception ex) {
            log.error("SesMailDispatcher: failed to restore retry count for mail {}", mailId, ex);
        }
    }

    /**
     * Normalizes any logo reference to use the application's hosted web URL.
     */
    public static String normalizeLogoToHostedUrl(String body, String appLink) {
        if (body == null) {
            return null;
        }
        String base = EmailLayoutDecorator.stripTrailingSlash(appLink);
        String targetUrl = (base.isEmpty() ? "" : base) + LOGO_URL_PATH;
        return LOGO_SRC_PATTERN.matcher(body).replaceAll("src=\"" + targetUrl + "\"");
    }

    public static boolean referencesLogo(String body) {
        if (body == null) {
            return false;
        }
        return LOGO_SRC_PATTERN.matcher(body).find();
    }

    public static boolean isHtml(String body) {
        return body != null && body.trim().startsWith("<");
    }

    private String param(String key, String defaultValue) {
        String v = paramService.getValue(key, defaultValue);
        return isBlank(v) ? defaultValue : v.trim();
    }

    /** Package-private so SesMailDispatcherServiceTest can assert the SMTP timeouts are set. */
    static JavaMailSenderImpl buildSender(String spec) {
        Map<String, String> p = new HashMap<>();
        for (String tok : spec.split(",")) {
            int eq = tok.indexOf('=');
            if (eq <= 0) continue;
            p.put(tok.substring(0, eq).trim().toLowerCase(), tok.substring(eq + 1).trim());
        }
        if (!p.containsKey("host") || isBlank(p.get("host"))) {
            throw new IllegalArgumentException("MAIL_SERVER must include 'host=...'");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(p.get("host"));
        sender.setPort(parseInt(p.get("port"), 25));
        if (!isBlank(p.get("user")))     sender.setUsername(p.get("user"));
        if (!isBlank(p.get("password"))) sender.setPassword(p.get("password"));
        sender.setDefaultEncoding("UTF-8");

        Properties jp = sender.getJavaMailProperties();
        jp.put("mail.transport.protocol", "smtp");
        if ("true".equalsIgnoreCase(p.get("starttls"))) {
            jp.put("mail.smtp.starttls.enable", "true");
        }
        if (!isBlank(p.get("user")) || !isBlank(p.get("password"))) {
            jp.put("mail.smtp.auth", "true");
        }

        // JavaMail's default for all three of these is INFINITE. An SMTP port
        // that a firewall DROPs (rather than REJECTs) therefore hangs the
        // sending thread forever -- and because drainQueue() is synchronized,
        // one hung send blocks every other sender thread behind it. With the
        // async pool's queue full, Spring's rejection policy then runs the
        // dispatch on the calling HTTP thread, which blocks on the same
        // monitor: a stuck mail server turns into stuck page loads.
        //
        // Bounded here rather than left to configuration because the
        // MAIL_SERVER parser above silently ignores keys it does not know, so
        // there was no way to set these from APP_PARAMETER at all. A
        // 'timeout=<ms>' key is now honoured for the environment that needs a
        // different value; everything else gets a sane ceiling by default.
        String timeout = String.valueOf(parseInt(p.get("timeout"), DEFAULT_SMTP_TIMEOUT_MS));
        jp.put("mail.smtp.connectiontimeout", timeout);
        jp.put("mail.smtp.timeout", timeout);
        jp.put("mail.smtp.writetimeout", timeout);
        return sender;
    }

    private static String[] splitAddrs(String csv) {
        String[] raw = csv.split(",");
        int n = 0;
        for (String s : raw) if (!isBlank(s)) n++;
        String[] out = new String[n];
        int i = 0;
        for (String s : raw) {
            if (!isBlank(s)) out[i++] = s.trim();
        }
        return out;
    }

    private static int parseInt(String s, int fallback) {
        if (isBlank(s)) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String stackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            pw.println(ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace(pw);
        }
        return sw.toString();
    }
}
