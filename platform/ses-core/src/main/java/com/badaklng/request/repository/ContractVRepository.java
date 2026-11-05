package com.badaklng.request.repository;

import com.badaklng.request.entity.ContractV;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Read-only repository over CONTRACT_V (see ContractV's javadoc for how that resolves per environment). */
@Repository
public interface ContractVRepository extends JpaRepository<ContractV, String> {

    /** Contract Number field's Select2 AJAX search source; matches contract no, company or title. */
    @Query("SELECT c FROM ContractV c WHERE " +
            "LOWER(c.noKontrak) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(c.company) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(c.judulPekerjaan) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "ORDER BY c.noKontrak")
    List<ContractV> searchByTerm(@Param("term") String term, Pageable pageable);
}
