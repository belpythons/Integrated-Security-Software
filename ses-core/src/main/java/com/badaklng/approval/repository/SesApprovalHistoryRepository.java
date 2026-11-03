package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesApprovalHistoryRepository extends JpaRepository<SesApprovalHistory, Long> {

    List<SesApprovalHistory> findByInstanceIdOrderByHistoryIdAsc(Long instanceId);

    @Query("SELECT DISTINCT h.instanceId FROM SesApprovalHistory h WHERE h.actorEmplId = :username OR (:userId IS NOT NULL AND h.actorUserId = :userId)")
    List<Long> findDistinctInstanceIdsByActor(@Param("username") String username, @Param("userId") String userId);
}

