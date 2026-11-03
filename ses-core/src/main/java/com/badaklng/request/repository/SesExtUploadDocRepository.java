package com.badaklng.request.repository;

import com.badaklng.request.entity.SesExtUploadDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Staging document rows.
 *
 * <p>Every finder filters on {@code isActive} explicitly rather than relying
 * on a default. A soft-delete flag that some queries honour and others forget
 * is how a document the contractor deleted reappears in the finalised set.</p>
 */
@Repository
public interface SesExtUploadDocRepository extends JpaRepository<SesExtUploadDoc, Long> {

    /** Everything still standing for one request — the upload page's read. */
    List<SesExtUploadDoc> findByRequestIdAndIsActiveOrderByWorkerIdAscDocCodeAsc(Long requestId, String isActive);

    /**
     * The single active row a save-draft upsert replaces, if any. The
     * function-based unique index in {@code sql/65} guarantees there is at
     * most one, which is what lets this return an {@code Optional}.
     */
    Optional<SesExtUploadDoc> findByRequestIdAndWorkerIdAndDocCodeAndIsActive(
            Long requestId, Long workerId, String docCode, String isActive);

    /** Used by the preview endpoint to prove a fileId belongs to this request. */
    Optional<SesExtUploadDoc> findByRequestIdAndFileIdAndIsActive(Long requestId, String fileId, String isActive);

    long countByRequestIdAndIsActive(Long requestId, String isActive);
}
