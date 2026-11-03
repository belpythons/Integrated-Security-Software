package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesApprovalStepRepository extends JpaRepository<SesApprovalStep, Long> {

    List<SesApprovalStep> findByChainIdOrderByStepOrderAsc(Long chainId);

    Optional<SesApprovalStep> findByChainIdAndStepOrder(Long chainId, Integer stepOrder);

    long countByChainId(Long chainId);

    Optional<SesApprovalStep> findFirstByChainIdOrderByStepOrderDesc(Long chainId);
}
