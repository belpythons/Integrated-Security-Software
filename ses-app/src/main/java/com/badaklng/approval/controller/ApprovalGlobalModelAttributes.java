package com.badaklng.approval.controller;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.platform.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects the current user's pending-approval count into every view model
 * so navbar.html can show a badge on the Approval link without every
 * controller having to add it explicitly.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class ApprovalGlobalModelAttributes {

    private final ApprovalService approvalService;

    @ModelAttribute("pendingApprovalCount")
    public long pendingApprovalCount() {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null || "anonymousUser".equals(username) || "SYSTEM".equals(username)) {
            return 0;
        }
        try {
            return approvalService.countMyPendingApprovalsFromView(username);
        } catch (Exception e) {
            return 0;
        }
    }
}
