package com.badaklng.request.repository;

import com.badaklng.request.entity.SesZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesZoneRepository extends JpaRepository<SesZone, String> {

    @Query("select z from SesZone z where z.activeFlag = :activeFlag order by z.orderBy asc")
    List<SesZone> findByActiveFlagOrderByOrderByAsc(@Param("activeFlag") String activeFlag);

    @Query("select z from SesZone z order by z.orderBy asc")
    List<SesZone> findAllByOrderByOrderByAsc();
}
