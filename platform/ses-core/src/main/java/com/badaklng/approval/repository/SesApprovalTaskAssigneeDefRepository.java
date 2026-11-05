package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalTaskAssigneeDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SesApprovalTaskAssigneeDefRepository extends JpaRepository<SesApprovalTaskAssigneeDef, Long> {

    @Query("select a from SesApprovalTaskAssigneeDef a where a.taskId = :taskId order by a.orderBy asc, a.taskAssigneeDefId asc")
    List<SesApprovalTaskAssigneeDef> findByTaskIdOrderByOrderByAscTaskAssigneeDefIdAsc(@Param("taskId") Long taskId);

    /**
     * The frozen assignee DEFINITIONS of a whole instance in ONE query — the
     * batch counterpart of the finder above, for the approval summary card's
     * "Ditujukan Kepada" column. Needed for steps that have not been activated
     * yet: those have no resolved rows, only definitions, so the card previews
     * them live.
     *
     * <p>Same 1000-element IN-list caveat as
     * {@code SesApprovalTaskAssigneeRepository.findByTaskIdIn}.</p>
     */
    @Query("select a from SesApprovalTaskAssigneeDef a where a.taskId in :taskIds "
         + "order by a.taskId asc, a.orderBy asc, a.taskAssigneeDefId asc")
    List<SesApprovalTaskAssigneeDef> findByTaskIdIn(@Param("taskIds") Collection<Long> taskIds);
}
