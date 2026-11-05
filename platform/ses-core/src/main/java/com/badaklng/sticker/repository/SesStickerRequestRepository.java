package com.badaklng.sticker.repository;

import com.badaklng.sticker.entity.SesStickerRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesStickerRequestRepository extends JpaRepository<SesStickerRequest, Long> {

    List<SesStickerRequest> findByRequestorUserIdOrderByStickerRequestIdDesc(String requestorUserId);

    /**
     * One page of a requestor's own sticker requests, optionally narrowed by a
     * free-text term matched against the columns the list screen shows.
     * A null term skips the condition entirely, keeping the unsearched case an
     * indexed lookup on {@code requestor_user_id}.
     */
    @Query("SELECT r FROM SesStickerRequest r WHERE r.requestorUserId = :username "
            + "AND (:term IS NULL OR UPPER(r.requestNo) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "     OR UPPER(r.requestorName) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "     OR UPPER(r.status) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "     OR EXISTS (SELECT 1 FROM SesStickerVehicle v WHERE v.stickerRequestId = r.stickerRequestId AND UPPER(v.policeNo) LIKE UPPER(CONCAT('%', :term, '%')))) "
            + "AND (:status IS NULL OR r.status = :status "
            + "     OR (:status = 'WAITING_APPROVAL' AND r.status = 'PENDING')) "
            + "AND (:stickerTypeCode IS NULL OR r.stickerTypeCode = :stickerTypeCode)")
    Page<SesStickerRequest> searchMine(@Param("username") String username,
                                       @Param("term") String term,
                                       @Param("status") String status,
                                       @Param("stickerTypeCode") String stickerTypeCode,
                                       Pageable pageable);

    default Page<SesStickerRequest> searchMine(String username, String term, String status, Pageable pageable) {
        return searchMine(username, term, status, null, pageable);
    }

    default Page<SesStickerRequest> searchMine(String username, String term, Pageable pageable) {
        return searchMine(username, term, null, null, pageable);
    }

    /**
     * Rows owned by any of the given usernames — the scoped dashboard and
     * history views, which previously pulled the whole table with
     * {@code findAll()} and filtered in memory.
     *
     * <p>Uses the index on {@code requestor_user_id}, which means the match
     * is exact. The column is always written from the authenticated username
     * and a check against ELLDEV found zero whitespace-padded values, so the
     * {@code .trim()} the in-memory filter used to apply is redundant.
     * ponytail: if padded values ever appear, scoping would silently narrow;
     * the fix is {@code TRIM(r.requestorUserId) IN :usernames}, at the cost
     * of the index.
     *
     * <p>Chunk the collection with {@code InChunks} (Oracle's 1000-expression cap).
     */
    List<SesStickerRequest> findByRequestorUserIdIn(java.util.Collection<String> usernames);

    Optional<SesStickerRequest> findByRequestNo(String requestNo);

    Optional<SesStickerRequest> findByApprovalInstanceId(Long approvalInstanceId);

    List<SesStickerRequest> findAllByOrderByStickerRequestIdDesc();

    @Query("SELECT s.status, COUNT(s) FROM SesStickerRequest s GROUP BY s.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT DISTINCT s.requestorUserId FROM SesStickerRequest s WHERE s.requestorUserId IS NOT NULL")
    List<String> findDistinctRequestorUserIds();

    List<SesStickerRequest> findByCreatedDateAfter(Date date);

    long countByStatus(String status);

    @Query("SELECT COUNT(r) FROM SesStickerRequest r WHERE r.status IN ('PENDING', 'WAITING_APPROVAL')")
    long countPendingReview();

    @Query("SELECT r.stickerTypeCode, COUNT(r) FROM SesStickerRequest r GROUP BY r.stickerTypeCode")
    java.util.List<Object[]> countByStickerTypeGrouped();

    @Query("SELECT r FROM SesStickerRequest r WHERE r.status IN ('PENDING', 'WAITING_APPROVAL') ORDER BY r.stickerRequestId DESC")
    java.util.List<SesStickerRequest> findRecentPending(org.springframework.data.domain.Pageable pageable);
}
