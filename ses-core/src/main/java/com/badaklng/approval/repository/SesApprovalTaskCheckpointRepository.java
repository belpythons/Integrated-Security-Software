package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalTaskCheckpoint;
import com.badaklng.approval.entity.TaskCheckpointStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesApprovalTaskCheckpointRepository extends JpaRepository<SesApprovalTaskCheckpoint, Long> {

    List<SesApprovalTaskCheckpoint> findByTaskIdOrderByCheckpointIdAsc(Long taskId);

    List<SesApprovalTaskCheckpoint> findByTaskIdAndStatus(Long taskId, TaskCheckpointStatus status);

    /**
     * checkpoint_key is a free-text convention, not DB-unique, but a step
     * should only ever carry one checkpoint per key in practice — first
     * match wins if an admin duplicates a key by mistake.
     */
    Optional<SesApprovalTaskCheckpoint> findFirstByTaskIdAndCheckpointKey(Long taskId, String checkpointKey);

    List<SesApprovalTaskCheckpoint> findByCheckpointKeyAndStatus(String checkpointKey, TaskCheckpointStatus status);

    boolean existsByTaskIdInAndStatus(List<Long> taskIds, TaskCheckpointStatus status);

    /**
     * Which of the given tasks carry a checkpoint in {@code status} — the
     * bulk answer behind {@code isEditableBatch}, where a boolean
     * {@code exists} per row would be a query per row.
     */
    List<SesApprovalTaskCheckpoint> findByTaskIdInAndStatus(List<Long> taskIds, TaskCheckpointStatus status);
}
