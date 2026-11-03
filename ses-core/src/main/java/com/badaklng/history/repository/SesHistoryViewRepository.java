package com.badaklng.history.repository;

import com.badaklng.history.entity.SesHistoryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SesHistoryViewRepository extends JpaRepository<SesHistoryView, String>,
        JpaSpecificationExecutor<SesHistoryView> {

    /**
     * One page of the whole org's history, optionally narrowed by a term.
     *
     * <p>The term is matched against what the screen shows as text: the
     * transaction number, the module name and the status.
     */
    @Query("SELECT h FROM SesHistoryView h WHERE "
            + "(:term IS NULL OR UPPER(h.transactionNo) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "  OR UPPER(h.moduleName) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "  OR UPPER(h.requestorUserId) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "  OR UPPER(h.status) LIKE UPPER(CONCAT('%', :term, '%'))) "
            + "AND (:status IS NULL OR h.status = :status) "
            + "AND (:module IS NULL OR h.businessType = :module)")
    Page<SesHistoryView> search(@Param("term") String term,
                                @Param("status") String status,
                                @Param("module") String module,
                                Pageable pageable);

    // The scoped (non-admin) page used to live here as a static @Query, with
    // an empty-IN-list workaround for the "allowed"/"involved" collections.
    // It is now built per-call by SesHistoryViewSpecifications.scoped(),
    // because a department-wide scope can exceed Oracle's 1000-expression
    // IN limit (ORA-01795) and a static JPQL string can't chunk a
    // variable-sized collection into several OR-ed IN clauses.
}
