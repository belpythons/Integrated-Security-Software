package com.badaklng.request.job;

import com.badaklng.platform.scheduler.AppSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Binds the {@code REQ_EXPIRY} schedule to {@link RequestExpiryJob}.
 *
 * <p>Same shape as {@code SesMailJobRegistration}, with one difference worth
 * naming: that class <b>overrides</b> an existing platform schedule, whereas
 * this one registers a schedule the platform has never heard of. The
 * {@code app_cat['JOB_SCHEDULE','REQ_EXPIRY']} row it pairs with is seeded by
 * {@code sql/61}; without that row the scheduler has a job class and no cron
 * to run it on, and the registration is simply inert.</p>
 *
 * <p>{@code @DependsOn} is kept for the same reason as the mail registration —
 * {@code registerJobClass} is a plain map {@code put} and ordering between
 * {@code @PostConstruct} methods is otherwise unspecified. Both run well
 * before {@code AppJobSchedulerConfig} fires {@code startAll()}.</p>
 *
 * <p>⚠️ Registering is not starting. {@code app.scheduler.auto-start=false},
 * so the sweep only begins once an operator starts {@code REQ_EXPIRY} at
 * {@code /admin/scheduler}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("appNativeJobRegistration")
public class RequestExpiryJobRegistration {

    private final AppSchedulerService schedulerService;

    @PostConstruct
    public void register() {
        schedulerService.registerJobClass(RequestExpiryJob.SCHEDULE_KEY, RequestExpiryJob.class);
        log.info("Schedule '{}' bound to {} (cancels WAITING_FOR_UPLOAD requests with no documents)",
                RequestExpiryJob.SCHEDULE_KEY, RequestExpiryJob.class.getSimpleName());
    }
}
