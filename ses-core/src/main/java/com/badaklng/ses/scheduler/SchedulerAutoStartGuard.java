package com.badaklng.ses.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Keeps the platform's startup registration compatible with SES's safe default:
 * schedules exist in the monitor but do not run until an operator starts them.
 * Manual starts resume the paused Quartz jobs through the platform service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerAutoStartGuard {

    private static final String JOB_GROUP = "appjob";

    private final Scheduler scheduler;

    @Value("${app.scheduler.auto-start:false}")
    private boolean autoStart;

    /** Runs after the platform's ContextRefreshedEvent listener has registered jobs. */
    @EventListener(ContextRefreshedEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void pauseAutoStartedSchedules() {
        if (autoStart) {
            return;
        }

        try {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(JOB_GROUP))) {
                if ("MAIL".equalsIgnoreCase(jobKey.getName())) {
                    log.info("Quartz schedule 'MAIL' kept active as automatic retry sweeper");
                    continue;
                }
                scheduler.pauseJob(jobKey);
                log.info("Quartz schedule '{}' held paused (app.scheduler.auto-start=false); "
                        + "start it from the Scheduler Monitor when needed", jobKey.getName());
            }
        } catch (SchedulerException ex) {
            log.error("Could not apply scheduler auto-start guard", ex);
        }
    }
}
