package com.badaklng.request.repository;

import com.badaklng.request.entity.SesDocRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesDocRequirementRepository extends JpaRepository<SesDocRequirement, String> {

    List<SesDocRequirement> findAllByOrderByDocNameAsc();
}
