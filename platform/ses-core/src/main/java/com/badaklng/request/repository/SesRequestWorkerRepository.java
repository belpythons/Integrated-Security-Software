package com.badaklng.request.repository;

import com.badaklng.request.entity.SesRequestWorker;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesRequestWorkerRepository extends JpaRepository<SesRequestWorker, Long> {

    List<SesRequestWorker> findByRequestIdOrderByLineNoAsc(Long requestId);

    long countByRequestId(Long requestId);

    /** Total worker rows across many requests — one query for the dashboard's "Jumlah Pekerja" KPI, no dedupe. */
    long countByRequestIdIn(List<Long> requestIds);

    /**
     * Per-request worker counts in one query, for list pages and reports that
     * need a count for every row they render.
     *
     * <p>Prefer this over calling {@link #countByRequestId} in a loop: that
     * was one {@code COUNT(*)} round trip per payment row on /payment,
     * /payment/verify and /payment/recap. Requests with no workers are simply
     * absent from the result (GROUP BY cannot emit a zero row), so callers
     * must default a missing key to 0.
     *
     * <p>Chunk {@code requestIds} with {@code InChunks} — Oracle caps an
     * {@code IN} list at 1000 expressions.
     */
    @Query("SELECT w.requestId, COUNT(w) FROM SesRequestWorker w "
            + "WHERE w.requestId IN :requestIds GROUP BY w.requestId")
    List<Object[]> countGroupedByRequestId(@Param("requestIds") List<Long> requestIds);

    void deleteByRequestId(Long requestId);

    /** Worker-name search's "past request data" source — matches name, NIK or
     * jabatan against past worker rows across all requests. */
    @Query("SELECT w FROM SesRequestWorker w WHERE " +
            "LOWER(w.workerName) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(w.nik) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(w.jabatan) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY w.updatedDate DESC")
    List<SesRequestWorker> searchByTerm(@Param("term") String term, Pageable pageable);
}
