package com.badaklng.request.repository;

import com.badaklng.request.entity.SesRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesRequestTypeRepository extends JpaRepository<SesRequestType, String> {

    @Query("select t from SesRequestType t where t.activeFlag = :activeFlag order by t.orderBy asc")
    List<SesRequestType> findByActiveFlagOrderByOrderByAsc(@Param("activeFlag") String activeFlag);

    @Query("select t from SesRequestType t order by t.orderBy asc")
    List<SesRequestType> findAllByOrderByOrderByAsc();
}
