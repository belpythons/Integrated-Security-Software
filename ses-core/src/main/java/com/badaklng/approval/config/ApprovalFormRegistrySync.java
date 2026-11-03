package com.badaklng.approval.config;

import com.badaklng.approval.service.ApprovalFormService;
import com.badaklng.approval.spi.ApprovalFormDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Upserts a SES_FORM row per {@link ApprovalFormDescriptor} bean found on
 * the classpath, and deactivates orphans (rows whose descriptor bean has
 * disappeared). The approval module has ZERO compile-time dependency on any
 * form module — only on this interface.
 *
 * MUST inject {@code ObjectProvider<ApprovalFormDescriptor>}, NOT
 * {@code List<ApprovalFormDescriptor>}: with zero descriptor beans on the
 * classpath (the state until the request module lands), constructor
 * injecting a List throws NoSuchBeanDefinitionException and the app fails
 * to boot.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalFormRegistrySync implements ApplicationRunner {

    private final ObjectProvider<ApprovalFormDescriptor> descriptors;
    private final ApprovalFormService formService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> seen = new LinkedHashSet<>();
        descriptors.orderedStream().forEach(d -> {
            formService.upsertFromDescriptor(d);
            if (!seen.add(d.formCode())) {
                throw new IllegalStateException("Duplicate ApprovalFormDescriptor.formCode(): " + d.formCode());
            }
        });
        int orphaned = formService.deactivateOrphans(seen);
        log.info("Approval form registry synced: {} descriptor(s), {} orphaned", seen.size(), orphaned);
    }
}
