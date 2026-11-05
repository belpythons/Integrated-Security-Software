package com.badaklng.approval.repository;

import com.badaklng.approval.entity.AppApprovalView;
import com.badaklng.approval.entity.AppApprovalViewId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only repository over APP_APPROVAL_VIEW (sql/36) — SES's unified
 * pending-approval inbox, keyed by the current user's EMPLID (== username,
 * see sql/34's cleansing convention).
 */
public interface AppApprovalViewRepository extends JpaRepository<AppApprovalView, AppApprovalViewId> {

    List<AppApprovalView> findByApproverId(String approverId);

    long countByApproverId(String approverId);
}
