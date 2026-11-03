package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalTaskAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SesApprovalTaskAssigneeRepository extends JpaRepository<SesApprovalTaskAssignee, Long> {

    List<SesApprovalTaskAssignee> findByTaskIdOrderByTaskAssigneeIdAsc(Long taskId);

    /**
     * Every frozen assignee of a whole instance in ONE query, for the approval
     * summary card. The per-task finder above would issue one select per step,
     * and the card already pays for a checkpoint lookup per step on top.
     *
     * <p>Ordered by task then id so the caller can group sequentially and the
     * names render in the order they were resolved.</p>
     *
     * <p>Oracle caps an IN list at 1000 elements. An approval instance has as
     * many tasks as its chain has steps — single digits in practice — so this
     * is safe here; anyone reusing it over a whole listing must chunk.</p>
     */
    @Query("select a from SesApprovalTaskAssignee a where a.taskId in :taskIds "
         + "order by a.taskId asc, a.taskAssigneeId asc")
    List<SesApprovalTaskAssignee> findByTaskIdIn(@Param("taskIds") Collection<Long> taskIds);

    @Query("SELECT DISTINCT t.instanceId FROM SesApprovalTaskAssignee a, SesApprovalTask t "
         + "WHERE a.taskId = t.taskId AND (a.emplId = :username OR (:userId IS NOT NULL AND a.userId = :userId))")
    List<Long> findDistinctInstanceIdsByAssignee(@Param("username") String username, @Param("userId") String userId);
}

