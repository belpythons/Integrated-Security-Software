package com.badaklng.ses.ext;

import com.badaklng.platform.scheduler.AppJobSchedulerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * {@code AppJobSchedulerConfig} is excluded here because it is what fires
 * every active {@code app_cat[JOB_SCHEDULE]} row on startup ({@code
 * startAll()}). ses-ext is a second, independent JVM sharing the same Oracle
 * schema as ses-app — without this exclusion it would run its own copy of
 * every schedule (MAIL included) against the same unlocked {@code app_mail}
 * queue, double-sending every outbound mail: once via ses-app's {@code
 * SesMailJob} (decorated HTML, TESTING_MODE-aware), and once via the
 * platform's un-overridden {@code MailJob} (raw {@code mail_body}, literal
 * tags, oblivious to TESTING_MODE).
 *
 * <p><b>The scan below does include {@code com.badaklng.ses.mail}, and has
 * to.</b> An earlier version of this comment claimed the opposite. It cannot
 * be excluded: {@code SesMailDispatcherService} is a required constructor
 * dependency of {@code ApprovalMailService} and {@code RequestMailService},
 * and ses-ext reaches both — finishing a contractor upload calls
 * {@code RequestService.triggerApproval()}, which notifies the first
 * approver. So excluding {@code AppJobSchedulerConfig} stops the <em>cron</em>
 * but not the instant {@code dispatchPendingAsync()} path, and this module was
 * in fact draining the shared queue.</p>
 *
 * <p>What actually stops it is {@code ses.mail.dispatch.enabled=false} in this
 * module's {@code application.properties}: ses-ext still writes rows to
 * {@code APP_MAIL} (nothing is lost) and ses-app remains the only sender.</p>
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.badaklng.platform",
        "com.badaklng.ses.ext",
        "com.badaklng.request.controller",
        "com.badaklng.request.service",
        "com.badaklng.approval.service",
        "com.badaklng.payment.service",
        "com.badaklng.scheduling.service",
        "com.badaklng.ses.file",
        "com.badaklng.ses.mail",
        "com.badaklng.ses.async",
        "com.badaklng.ses.user",
        "com.badaklng.ses.text",
        "com.badaklng.ses.config",
        "com.badaklng.ses.web"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                AppJobSchedulerConfig.class,
                com.badaklng.ses.mail.SesMailJobRegistration.class
        })
})
@EntityScan(basePackages = {
        "com.badaklng.platform.system.entity",
        "com.badaklng.ses.entity",
        "com.badaklng.ses.file",
        "com.badaklng.request.entity",
        "com.badaklng.approval.entity",
        "com.badaklng.payment.entity",
        "com.badaklng.scheduling.entity",
        "com.badaklng.sticker.entity",
        "com.badaklng.cash.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.badaklng.platform.system.repository",
        "com.badaklng.ses.repository",
        "com.badaklng.ses.file",
        "com.badaklng.request.repository",
        "com.badaklng.approval.repository",
        "com.badaklng.payment.repository",
        "com.badaklng.scheduling.repository",
        "com.badaklng.sticker.repository",
        "com.badaklng.cash.repository"
})
public class SesExtApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SesExtApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SesExtApplication.class, args);
    }
}
