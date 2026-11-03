package com.badaklng.ses.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Global asynchronous execution configuration for SES.
 * Provides a dedicated thread pool for high-throughput background tasks
 * such as immediate email dispatching, approval chain evaluations,
 * and asynchronous document/audit handling.
 */
@Slf4j
@Configuration
@EnableAsync
public class SesAsyncConfig {

    public static final String TASK_EXECUTOR_NAME = "sesTaskExecutor";

    @Bean(name = TASK_EXECUTOR_NAME)
    public Executor sesTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("ses-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Initialized SES background worker pool (core=5, max=15, queue=1000)");
        return executor;
    }
}
