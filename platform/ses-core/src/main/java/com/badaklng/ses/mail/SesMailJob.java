package com.badaklng.ses.mail;

import com.badaklng.platform.scheduler.MailJob;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.regex.Pattern;

/**
 * The Quartz job registered under the platform's {@code MAIL} schedule key by
 * {@link SesMailJobRegistration}, so {@link MailJob} never runs here.
 *
 * <p>It does nothing but drain {@link SesMailDispatcherService}. All the
 * sending behaviour that used to live in this class — HTML sniffing, the
 * {@code TESTING_MODE}/{@code REDIRECT_MAIL} safety valve, SMTP sender
 * construction, address splitting, retry accounting — moved into that service
 * so the cron path and the instant {@code dispatchPendingAsync()} path share
 * one implementation instead of two that drift.</p>
 *
 * <p>What was left behind after that move was a second full copy of
 * {@code buildSender} and its helpers, unreachable from {@link #execute} and
 * from anywhere else. It has been deleted: a dead copy of the SMTP setup is
 * exactly the kind of thing that gets "fixed" in one place and silently not
 * the other — the missing connect/read/write timeouts had to be added to
 * {@code SesMailDispatcherService} and would have looked equally at home
 * here, where they would have had no effect at all.</p>
 *
 * <p>The three static helpers below stay because they are the inline-logo
 * contract the mail tests assert against, and {@code SesMailJobCidRewriteTest}
 * addresses them through this class.</p>
 */
@Slf4j
@DisallowConcurrentExecution
public class SesMailJob implements Job {

    /* The logo lives in the platform jar. SES deliberately keeps no copy of its
     * own: a second file on the same classpath path silently shadows platform's
     * and drifts out of sync -- it did, at 318x308 against platform's 196x165,
     * which is what blew up the logo on the login page. */
    private static final Pattern LOGO_SRC_PATTERN = Pattern.compile(
            "(?i)src\\s*=\\s*['\"][^'\"]*?ptb_logo\\.png(?:\\?[^'\"]*)?['\"]"
    );

    @Autowired private SesMailDispatcherService dispatcherService;

    @Override
    public void execute(JobExecutionContext context) {
        if (dispatcherService != null) {
            dispatcherService.drainQueue();
        }
    }

    /** A body opening with a tag is markup; anything else is plain text. */
    static boolean isHtml(String body) {
        return body != null && body.trim().startsWith("<");
    }

    /** Returns true if the body contains an img src pointing to ptb_logo.png. */
    public static boolean referencesLogo(String body) {
        if (body == null) {
            return false;
        }
        return LOGO_SRC_PATTERN.matcher(body).find();
    }

    /** Rewrites any ptb_logo.png img src attribute to use cid:badakLogo. */
    public static String rewriteLogoToCid(String body) {
        if (body == null) {
            return null;
        }
        return LOGO_SRC_PATTERN.matcher(body).replaceAll("src=\"cid:badakLogo\"");
    }
}
