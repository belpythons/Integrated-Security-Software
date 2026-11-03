package com.badaklng.ses.ext;

import com.badaklng.platform.scheduler.AppJobSchedulerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the fix for the duplicate-mail bug: ses-ext is a second JVM
 * sharing ses-app's Oracle schema, and {@code AppJobSchedulerConfig} is what
 * fires every active {@code app_cat[JOB_SCHEDULE]} row ({@code startAll()}
 * on {@code ContextRefreshedEvent}). Left unexcluded, ses-ext would run its
 * own MAIL schedule against the same unlocked {@code app_mail} queue ses-app
 * already drains, sending every outbound mail twice. No Spring context
 * needed — this just checks the annotation ses-ext actually ships with.
 */
class SesExtApplicationSchedulerExclusionTest {

    @Test
    @DisplayName("SesExtApplication excludes AppJobSchedulerConfig from its component scan")
    void excludesJobSchedulerAutostart() {
        ComponentScan scan = SesExtApplication.class.getAnnotation(ComponentScan.class);
        assertTrue(scan != null, "SesExtApplication must declare an explicit @ComponentScan "
                + "(scanBasePackages() on @SpringBootApplication cannot carry excludeFilters)");

        boolean excluded = false;
        for (ComponentScan.Filter f : scan.excludeFilters()) {
            for (Class<?> c : f.classes()) {
                if (c == AppJobSchedulerConfig.class) {
                    excluded = true;
                }
            }
        }
        assertTrue(excluded, "AppJobSchedulerConfig must be excluded so ses-ext never "
                + "auto-starts the shared MAIL schedule");
    }
}
