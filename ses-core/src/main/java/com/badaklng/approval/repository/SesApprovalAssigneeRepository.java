package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalAssignee;
import com.badaklng.approval.entity.StepRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SesApprovalAssigneeRepository extends JpaRepository<SesApprovalAssignee, Long> {

    List<SesApprovalAssignee> findByStepIdOrderByAssigneeIdAsc(Long stepId);

    long countByStepId(Long stepId);

    void deleteByStepId(Long stepId);

    /**
     * Every position any step with one of {@code roles} is aimed at, across
     * all chains.
     *
     * <p>Feeds the evaluator dedup guard: it answers "which chairs does the
     * configured flow already ask?" without anyone hard-coding
     * {@code 'EH1200'} / {@code 'EH1000'} into Java. The positions are a
     * configuration decision made at {@code /approval/chain}; a constant in
     * the source would go stale the first time procurement reorganises, and
     * it would go stale silently.</p>
     *
     * <p>Not scoped to one chain on purpose. A role is unique per chain, the
     * Badge chains are the only ones carrying these roles, and the caller is
     * asking a question about the organisation ("is this person already in the
     * procurement leg?") rather than about one particular chain.</p>
     */
    @Query("select distinct a.posId from SesApprovalAssignee a, SesApprovalStep s "
         + "where a.stepId = s.stepId and s.stepRole in :roles and a.posId is not null")
    List<String> findPosIdsByStepRoles(@Param("roles") Collection<StepRole> roles);
}
