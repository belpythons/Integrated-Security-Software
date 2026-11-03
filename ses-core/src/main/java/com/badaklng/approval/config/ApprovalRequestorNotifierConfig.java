package com.badaklng.approval.config;

import com.badaklng.approval.repository.SesApprovalHistoryRepository;
import com.badaklng.approval.repository.SesApprovalInstanceRepository;
import com.badaklng.approval.repository.SesApprovalTaskCheckpointRepository;
import com.badaklng.approval.repository.SesApprovalTaskRepository;
import com.badaklng.approval.service.ApprovalMailService;
import com.badaklng.approval.service.ApprovalRequestorNotifier;
import com.badaklng.approval.spi.CheckpointActionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers one {@link ApprovalRequestorNotifier} bean per business type
 * that routes through the approval engine (see live values checked
 * 2026-09-17: {@code SES_REQUEST}, {@code SES_STICKER_REQUEST} — matches
 * {@code RequestApprovalBridge}/{@code StickerApprovalBridge}'s
 * {@code businessRefType()}). Add one more {@code @Bean} line here when a
 * new module starts triggering approvals — no other change needed, the
 * notifier class itself is generic.
 */
@Configuration
@RequiredArgsConstructor
public class ApprovalRequestorNotifierConfig {

    private final SesApprovalInstanceRepository instanceRepository;
    private final SesApprovalHistoryRepository historyRepository;
    private final SesApprovalTaskRepository taskRepository;
    private final SesApprovalTaskCheckpointRepository checkpointRepository;
    private final ApprovalMailService approvalMailService;
    private final ObjectProvider<CheckpointActionProvider> checkpointActionProviders;

    @Bean
    public ApprovalRequestorNotifier sesRequestRequestorNotifier() {
        return new ApprovalRequestorNotifier("SES_REQUEST", instanceRepository, historyRepository,
                taskRepository, checkpointRepository, approvalMailService, checkpointActionProviders);
    }

    @Bean
    public ApprovalRequestorNotifier sesStickerRequestRequestorNotifier() {
        return new ApprovalRequestorNotifier("SES_STICKER_REQUEST", instanceRepository, historyRepository,
                taskRepository, checkpointRepository, approvalMailService, checkpointActionProviders);
    }
}
