package com.badaklng.request.repository;

import com.badaklng.request.entity.AppEmpInfoView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Read-only repository for the APP_EMP_INFO_VIEW employee master view. */
@Repository
public interface AppEmpInfoViewRepository extends JpaRepository<AppEmpInfoView, String> {

    Optional<AppEmpInfoView> findByEmplid(String emplid);
}
