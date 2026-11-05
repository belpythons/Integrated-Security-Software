package com.badaklng.request.repository;

import com.badaklng.request.entity.SesRequest;
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
public interface SesRequestRepository extends JpaRepository<SesRequest, Long> {

    List<SesRequest> findByRequestorUserIdOrderByRequestIdDesc(String requestorUserId);

    /**
     * One page of a requestor's own submissions, optionally narrowed by a
     * free-text term.
     *
     * <p>The term is matched against the three columns the list screen
     * actually shows -- request number, type code and status -- rather than
     * every column, because those are what a reader scans for. {@code UPPER}
     * on both sides makes it case-insensitive; the leading wildcard means it
     * cannot use the index on {@code requestor_user_id} for the term itself,
     * but that index still narrows to one requestor first, which is what
     * keeps this cheap.
     *
     * <p>A null term skips the whole condition rather than matching '%%', so
     * the unsearched case stays a plain indexed lookup.
     */
    @Query("SELECT r FROM SesRequest r WHERE r.requestorUserId = :username "
            + "AND (:term IS NULL OR UPPER(r.requestNo) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "     OR UPPER(r.typeCode) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "     OR UPPER(r.status) LIKE UPPER(CONCAT('%', :term, '%'))) "
            + "AND (:status IS NULL OR r.status = :status "
            + "     OR (:status = 'WAITING_APPROVAL' AND r.status = 'PENDING')) "
            + "AND (:typeCode IS NULL OR r.typeCode = :typeCode)")
    Page<SesRequest> searchMine(@Param("username") String username,
                                @Param("term") String term,
                                @Param("status") String status,
                                @Param("typeCode") String typeCode,
                                Pageable pageable);

    default Page<SesRequest> searchMine(String username, String term, String status, Pageable pageable) {
        return searchMine(username, term, status, null, pageable);
    }

    default Page<SesRequest> searchMine(String username, String term, Pageable pageable) {
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
    List<SesRequest> findByRequestorUserIdIn(java.util.Collection<String> usernames);

    Optional<SesRequest> findByApprovalInstanceId(Long approvalInstanceId);

    /**
     * Lookup by the human-facing request number — what a QR scan and the
     * {@code /verify/{requestNo}} URL carry.
     *
     * <p>{@code request_no} is UNIQUE at the DB level
     * ({@code uq_ses_request_no}), so this can safely return a single
     * Optional rather than a list.</p>
     */
    Optional<SesRequest> findByRequestNo(String requestNo);

    boolean existsByTypeCode(String typeCode);

    /**
     * The request a document-upload link points at.
     *
     * <p>Indexed UNIQUE at the DB level ({@code uq_ses_request_upload_token},
     * sql/61), so a single Optional is safe. Oracle does not index an
     * all-NULL key, so the many rows with no token cost nothing here.</p>
     *
     * <p>Returning the row is NOT the same as accepting the token —
     * {@code UploadTokenService.resolve} still has to check expiry, single-use
     * and status. This finder deliberately knows none of that: a repository
     * that silently filtered would make "no such token" and "your link
     * expired" indistinguishable to the caller, and the caller is the only
     * place that can tell the visitor something useful.</p>
     */
    Optional<SesRequest> findByUploadToken(String uploadToken);

    /**
     * Submissions stuck waiting for documents that nobody ever uploaded.
     *
     * <p>Ordered oldest first so a sweep that hits its batch limit makes
     * progress on the worst cases rather than re-reading the same page.</p>
     */
    List<SesRequest> findByStatusAndCreatedDateBeforeOrderByCreatedDateAsc(String status, Date createdBefore);

    @Query("SELECT r.status, COUNT(r) FROM SesRequest r GROUP BY r.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT DISTINCT r.requestorUserId FROM SesRequest r WHERE r.requestorUserId IS NOT NULL")
    List<String> findDistinctRequestorUserIds();

    List<SesRequest> findByCreatedDateAfter(Date date);
}
