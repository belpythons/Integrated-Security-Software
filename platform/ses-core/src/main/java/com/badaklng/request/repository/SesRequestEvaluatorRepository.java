package com.badaklng.request.repository;

import com.badaklng.request.entity.SesRequestEvaluator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesRequestEvaluatorRepository extends JpaRepository<SesRequestEvaluator, Long> {

    /** In the order the requestor picked them — the order the route table renders. */
    List<SesRequestEvaluator> findByRequestIdOrderByLineNoAscEvaluatorIdAsc(Long requestId);

    long countByRequestId(Long requestId);

    /**
     * Clears a request's evaluator list before rewriting it.
     *
     * <p>Replace-wholesale rather than diff-and-merge: the list is small, has no
     * children of its own, and nothing points at an evaluator row — once
     * {@code ApprovalService#trigger()} has run, the approval holds its own
     * frozen copy in {@code ses_approval_task_signoff}. Deleting and reinserting
     * here therefore cannot disturb an approval that is already running, which
     * is exactly the hazard that makes the same shortcut wrong for worker rows
     * (see the orphan-file note in {@code sql/51}).</p>
     */
    @Modifying
    @Query("delete from SesRequestEvaluator e where e.requestId = :requestId")
    void deleteByRequestId(@Param("requestId") Long requestId);
}
