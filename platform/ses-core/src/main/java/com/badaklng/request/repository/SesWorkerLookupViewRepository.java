package com.badaklng.request.repository;

import com.badaklng.request.entity.SesWorkerLookupView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Read-only repository over SES_WORKER_LOOKUP_VIEW (see ses/sql/23_worker_lookup_view.sql). */
@Repository
public interface SesWorkerLookupViewRepository extends JpaRepository<SesWorkerLookupView, Long> {

    /**
     * Worker Data table's autofill search — the SINGLE source now backing
     * RequestController#workerSearch. Matches the term against badge, name,
     * NIK or jabatan; the view itself is already deduplicated one row per
     * real badge, so no further merging is needed here.
     */
    @Query("SELECT w FROM SesWorkerLookupView w WHERE " +
            "LOWER(w.badge) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(w.workerName) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(w.nik) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(w.jabatan) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY w.workerName")
    List<SesWorkerLookupView> searchByTerm(@Param("term") String term, Pageable pageable);

    /**
     * Searches specifically by badge number prefix (e.g. typing '1' matches badges starting with 1).
     * Ordered by badge ascending so the most relevant numeric matches appear at the top.
     */
    @Query("SELECT w FROM SesWorkerLookupView w WHERE " +
            "w.badge IS NOT NULL AND LOWER(w.badge) LIKE LOWER(CONCAT(:term, '%')) " +
            "ORDER BY w.badge ASC")
    List<SesWorkerLookupView> searchByBadgePrefix(@Param("term") String term, Pageable pageable);

    /**
     * Searches specifically by NIK prefix.
     */
    @Query("SELECT w FROM SesWorkerLookupView w WHERE " +
            "w.nik IS NOT NULL AND LOWER(w.nik) LIKE LOWER(CONCAT(:term, '%')) " +
            "ORDER BY w.nik ASC")
    List<SesWorkerLookupView> searchByNikPrefix(@Param("term") String term, Pageable pageable);

    @Query("SELECT w FROM SesWorkerLookupView w WHERE " +
            "w.workerName IS NOT NULL AND LOWER(w.workerName) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY w.workerName ASC")
    List<SesWorkerLookupView> searchByName(@Param("term") String term, Pageable pageable);

    /**
     * Department/Section/Jabatan single-field lookups (see RequestController's
     * departmentSearch/sectionSearch/jabatanSearch) — these fields only make
     * sense to search/autofill against their OWN column, never the whole
     * person record, so unlike searchByTerm() above these return plain
     * DISTINCT strings, not worker rows.
     */
    @Query("SELECT DISTINCT w.department FROM SesWorkerLookupView w WHERE " +
            "w.department IS NOT NULL AND LOWER(w.department) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY w.department")
    List<String> searchDepartments(@Param("term") String term, Pageable pageable);

    @Query("SELECT DISTINCT w.section FROM SesWorkerLookupView w WHERE " +
            "w.section IS NOT NULL AND LOWER(w.section) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY w.section")
    List<String> searchSections(@Param("term") String term, Pageable pageable);

    @Query("SELECT DISTINCT w.jabatan FROM SesWorkerLookupView w WHERE " +
            "w.jabatan IS NOT NULL AND LOWER(w.jabatan) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY w.jabatan")
    List<String> searchJabatans(@Param("term") String term, Pageable pageable);
}
