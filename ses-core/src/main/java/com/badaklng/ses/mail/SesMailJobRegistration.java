package com.badaklng.ses.mail;

import com.badaklng.platform.scheduler.AppSchedulerService;
import com.badaklng.platform.scheduler.MailJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Points the {@code MAIL} schedule at {@link SesMailJob} instead of the
 * platform's {@code MailJob}.
 *
 * <p>{@code AppSchedulerService.registerJobClass} is a plain map {@code put},
 * so this is last-writer-wins against the platform's own
 * {@code AppNativeJobRegistration}. Ordering between two {@code @PostConstruct}
 * methods is not guaranteed, hence the explicit {@code @DependsOn} — it forces
 * the platform bean to be constructed (and to register {@code MailJob}) before
 * this one overwrites the entry. Both still run well before
 * {@code AppJobSchedulerConfig} fires {@code startAll()} on
 * {@code ContextRefreshedEvent}, so the override is live from the first tick.</p>
 *
 * <p>Lives in application code because platform classes cannot be edited from
 * outside their jar — same shape as KPI's {@code KpiNativeJobRegistration}.
 * No new {@code app_cat[JOB_SCHEDULE]} row is needed: the existing {@code MAIL}
 * schedule and its cron are reused as-is.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("appNativeJobRegistration")
public class SesMailJobRegistration {

    private final AppSchedulerService schedulerService;

    @PostConstruct
    public void register() {
        schedulerService.registerJobClass(MailJob.SCHEDULE_KEY, SesMailJob.class);
        log.info("Schedule '{}' overridden by {} (HTML bodies + APP_PARAMETER dev redirect)",
                MailJob.SCHEDULE_KEY, SesMailJob.class.getSimpleName());
    }
}
