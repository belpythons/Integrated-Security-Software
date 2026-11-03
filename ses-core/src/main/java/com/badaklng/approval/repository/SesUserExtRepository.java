package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesUserExt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SesUserExtRepository extends JpaRepository<SesUserExt, String> {

    Optional<SesUserExt> findByUserId(String userId);
}
