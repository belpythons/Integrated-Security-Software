package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesApprovalChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesApprovalChainRepository extends JpaRepository<SesApprovalChain, Long> {

    Optional<SesApprovalChain> findByChainKey(String chainKey);

    Optional<SesApprovalChain> findByChainKeyAndActiveFlag(String chainKey, String activeFlag);

    List<SesApprovalChain> findAllByOrderByChainNameAsc();

    List<SesApprovalChain> findByActiveFlagOrderByChainNameAsc(String activeFlag);

    boolean existsByChainKeyAndChainIdNot(String chainKey, Long chainId);
}
