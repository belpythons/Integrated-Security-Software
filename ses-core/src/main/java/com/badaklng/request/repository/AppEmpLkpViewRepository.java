package com.badaklng.request.repository;

import com.badaklng.request.entity.AppEmpLkpView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only repository for APP_EMP_LKP_VIEW (active employee lookup master).
 */
@Repository
public interface AppEmpLkpViewRepository extends JpaRepository<AppEmpLkpView, String> {

    Optional<AppEmpLkpView> findByEmplid(String emplid);

    @Query("SELECT e FROM AppEmpLkpView e WHERE " +
            "LOWER(e.emplid) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(e.nameDisplay) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(e.deptDesc) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(e.positionDesc) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY e.nameDisplay")
    List<AppEmpLkpView> searchByTerm(@Param("term") String term, Pageable pageable);

    @Query("SELECT e FROM AppEmpLkpView e WHERE e.deptId = :deptId ORDER BY e.positionDesc ASC")
    List<AppEmpLkpView> findByDeptIdOrderByPositionDescAsc(@Param("deptId") String deptId);

    List<AppEmpLkpView> findBySectionId(String sectionId);

    List<AppEmpLkpView> findByDeptId(String deptId);
}
