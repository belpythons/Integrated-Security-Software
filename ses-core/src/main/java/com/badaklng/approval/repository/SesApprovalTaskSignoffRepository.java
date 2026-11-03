package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalTaskSignoff;
import com.badaklng.approval.entity.SignoffStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesApprovalTaskSignoffRepository extends JpaRepository<SesApprovalTaskSignoff, Long> {

    /**
     * One task's sign-offs, in the order the requestor picked the people.
     *
     * <p><b>The name is deliberately NOT in derived-query style.</b> The
     * obvious name — {@code findByTaskIdOrderByOrderByAscSignoffIdAsc} — is
     * unusable: Spring Data splits a derived name on the literal
     * {@code OrderBy}, and the property being ordered on is itself called
     * {@code orderBy}, so the parser throws "OrderBy must not be used more
     * than once in a method name" <em>at context startup</em> and takes the
     * whole application down. That is not a theoretical hazard; it is how
     * this method was first written, and the test suite caught it.</p>
     *
     * <p>An explicit {@code @Query} fixes the behaviour, but a derived-looking
     * name on top of it is a trap for the next reader: it invites someone to
     * delete the annotation. {@code findByTaskIdOrdered} cannot be mistaken
     * for a derived query, so the annotation obviously carries the ordering.</p>
     *
     * <p>Same trap, different compromise, as
     * {@code SesApprovalStepTaskRepository.findByStepIdOrderByOrderByAscStepTaskIdAsc},
     * which kept the derived-style name. See also the note in
     * {@code SesStickerTypeRepository}.</p>
     */
    @Query("select s from SesApprovalTaskSignoff s where s.taskId = :taskId "
         + "order by s.orderBy asc, s.signoffId asc")
    List<SesApprovalTaskSignoff> findByTaskIdOrdered(@Param("taskId") Long taskId);

    /**
     * "Did the requestor name anyone for this step at all?"
     *
     * <p>Asked at activation to decide whether a {@code SECURITY_STAFF} step
     * runs or is skipped. A count rather than {@link #findByTaskIdOrdered},
     * because the rows themselves are not wanted — only whether there are
     * any — and the answer is needed for every task of every activation.</p>
     */
    long countByTaskId(Long taskId);

    /** "Is this consensus task still waiting on anyone?" — the question asked on every approve. */
    long countByTaskIdAndStatus(Long taskId, SignoffStatus status);

    /**
     * This person's row on this task, if they are one of the people it is
     * waiting on. Absent means they are not an evaluator of this task at all —
     * which is how the service tells "not your task" from "your turn".
     */
    Optional<SesApprovalTaskSignoff> findByTaskIdAndEmplId(Long taskId, String emplId);

    /**
     * Every sign-off of a whole instance in ONE query, for the Approval Route
     * table. The per-task finder above would issue one select per step, on top
     * of the per-step assignee and checkpoint lookups the card already pays for.
     *
     * <p>Ordered by task, then display order, then id, so the caller can group
     * sequentially and the rows render in the order the requestor picked them.</p>
     *
     * <p>Oracle caps an IN list at 1000 elements. An instance has as many tasks
     * as its chain has steps — single digits in practice — so this is safe
     * here; anyone reusing it across a whole listing must chunk.</p>
     */
    @Query("select s from SesApprovalTaskSignoff s where s.taskId in :taskIds "
         + "order by s.taskId asc, s.orderBy asc, s.signoffId asc")
    List<SesApprovalTaskSignoff> findByTaskIdIn(@Param("taskIds") Collection<Long> taskIds);

    @Query("SELECT DISTINCT t.instanceId FROM SesApprovalTaskSignoff s, SesApprovalTask t "
         + "WHERE s.taskId = t.taskId AND (s.emplId = :username OR (:userId IS NOT NULL AND s.userId = :userId))")
    List<Long> findDistinctInstanceIdsBySignoffUser(@Param("username") String username, @Param("userId") String userId);
}

