package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesFormApprovalMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesFormApprovalMapRepository extends JpaRepository<SesFormApprovalMap, Long> {

    List<SesFormApprovalMap> findByFormCodeOrderByMatchKeyAsc(String formCode);

    Optional<SesFormApprovalMap> findByFormCodeAndMatchKeyAndActiveFlag(String formCode, String matchKey, String activeFlag);

    @Query("SELECT m FROM SesFormApprovalMap m WHERE m.formCode = :fc AND m.matchKey IS NULL AND m.activeFlag = 'Y'")
    Optional<SesFormApprovalMap> findActiveDefault(@Param("fc") String formCode);

    /**
     * Active-only on purpose: saveMappings soft-deactivates rows rather than
     * deleting them, so a raw countByChainId would keep a chain "in use"
     * forever once it had ever been mapped. See ApprovalChainService.isInUse.
     */
    long countByChainIdAndActiveFlag(Long chainId, String activeFlag);

    /**
     * Drops the soft-deleted mapping rows of a chain. Needed before a chain
     * hard delete: isInUse ignores inactive rows, but FK_SES_FAM_CHAIN does
     * not, so leaving them behind turns the delete into an ORA-02292.
     */
    long deleteByChainIdAndActiveFlag(Long chainId, String activeFlag);
}
