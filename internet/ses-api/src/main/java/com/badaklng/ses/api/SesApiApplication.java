package com.badaklng.ses.api;

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
 * SES RESTful API Gateway Application.
 * Dedicated for Mobile Flutter (ses-mobo) and Web SPA consumption.
 * Fully stateless (JWT) and compliant with Badak LNG Platform.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.badaklng.platform",
        "com.badaklng.ses.api",
        "com.badaklng.request.service",
        "com.badaklng.approval.service",
        "com.badaklng.payment.service",
        "com.badaklng.scheduling.service",
        "com.badaklng.sticker.service",
        "com.badaklng.ses.file",
        "com.badaklng.ses.mail",
        "com.badaklng.ses.async",
        "com.badaklng.ses.user",
        "com.badaklng.ses.text",
        "com.badaklng.ses.config"
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
public class SesApiApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SesApiApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SesApiApplication.class, args);
    }
}

