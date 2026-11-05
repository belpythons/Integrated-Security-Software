package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalStepTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesApprovalStepTaskRepository extends JpaRepository<SesApprovalStepTask, Long> {

    @Query("SELECT t FROM SesApprovalStepTask t WHERE t.stepId = :stepId ORDER BY t.orderBy ASC, t.stepTaskId ASC")
    List<SesApprovalStepTask> findByStepIdOrderByOrderByAscStepTaskIdAsc(@Param("stepId") Long stepId);

    void deleteByStepId(Long stepId);
}
