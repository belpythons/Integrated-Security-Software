package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesApprovalInstanceRepository extends JpaRepository<SesApprovalInstance, Long> {

    List<SesApprovalInstance> findByRequestorUserIdOrderByInstanceIdDesc(String requestorUserId);

    List<SesApprovalInstance> findByBusinessRefTypeAndBusinessRefIdAndStatus(
            String businessRefType, Long businessRefId, ApprovalStatus status);

    /** Used by ApprovalRequestorNotifier to look up the instance an ApprovalOutcomeListener fired for. */
    List<SesApprovalInstance> findByBusinessRefTypeAndBusinessRefIdAndStatusOrderByInstanceIdDesc(
            String businessRefType, Long businessRefId, ApprovalStatus status);

    /**
     * Newest instance for a business record whatever its status — what a
     * business screen needs to show "where is this in the approval flow",
     * including after the flow finished. The status-filtered lookups above
     * answer a different question (an outcome listener's own instance).
     */
    java.util.Optional<SesApprovalInstance> findFirstByBusinessRefTypeAndBusinessRefIdOrderByInstanceIdDesc(
            String businessRefType, Long businessRefId);

    List<SesApprovalInstance> findByStatusOrderByInstanceIdDesc(ApprovalStatus status);

    long countByChainId(Long chainId);

    long countByFormCode(String formCode);
}
