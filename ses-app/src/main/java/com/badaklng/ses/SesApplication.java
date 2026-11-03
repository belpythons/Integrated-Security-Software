package com.badaklng.ses;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.badaklng.platform", "com.badaklng.ses", "com.badaklng.approval", "com.badaklng.request", "com.badaklng.switchuser", "com.badaklng.payment", "com.badaklng.scheduling", "com.badaklng.sticker", "com.badaklng.history", "com.badaklng.dashboard", "com.badaklng.cash"})
@EntityScan(basePackages = {"com.badaklng.platform.system.entity", "com.badaklng.ses.entity", "com.badaklng.ses.file", "com.badaklng.history.entity", "com.badaklng.approval.entity", "com.badaklng.request.entity", "com.badaklng.payment.entity", "com.badaklng.scheduling.entity", "com.badaklng.sticker.entity", "com.badaklng.cash.entity"})
@EnableJpaRepositories(basePackages = {"com.badaklng.platform.system.repository", "com.badaklng.ses.repository", "com.badaklng.ses.file", "com.badaklng.history.repository", "com.badaklng.approval.repository", "com.badaklng.request.repository", "com.badaklng.payment.repository", "com.badaklng.scheduling.repository", "com.badaklng.sticker.repository", "com.badaklng.cash.repository"})
public class SesApplication extends SpringBootServletInitializer {

    /**
     * Entry point when the WAR is deployed into an external container
     * (WebLogic). Ignored when the artifact is launched with `java -jar`,
     * which goes through main() instead.
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SesApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SesApplication.class, args);
    }
}
