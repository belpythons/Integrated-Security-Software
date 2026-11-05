package com.badaklng.approval.repository;

import com.badaklng.approval.entity.SesForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesFormRepository extends JpaRepository<SesForm, Long> {

    Optional<SesForm> findByFormCode(String formCode);

    List<SesForm> findAllByOrderByActiveFlagDescFormNameAsc();
}
