package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesApprovalTaskRepository extends JpaRepository<SesApprovalTask, Long> {

    List<SesApprovalTask> findByInstanceIdOrderByStepOrderAsc(Long instanceId);

    /**
     * Tasks of many instances in one query — the bulk form list pages need.
     * {@code isEditable} was calling the single-instance finder once per row
     * just to decide whether to grey out an Edit button, which is two
     * queries per row on /request and /sticker. Chunk with {@code InChunks}.
     */
    List<SesApprovalTask> findByInstanceIdIn(List<Long> instanceIds);

    Optional<SesApprovalTask> findByInstanceIdAndStepOrder(Long instanceId, Integer stepOrder);

    List<SesApprovalTask> findByInstanceIdAndStatus(Long instanceId, TaskStatus status);
}
