package com.badaklng.request.repository;

import com.badaklng.request.entity.SesRequestTypeDoc;
import com.badaklng.request.entity.SesRequestTypeDocId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesRequestTypeDocRepository extends JpaRepository<SesRequestTypeDoc, SesRequestTypeDocId> {

    @Query("select d from SesRequestTypeDoc d where d.typeCode = :typeCode order by d.orderBy asc")
    List<SesRequestTypeDoc> findByTypeCodeOrderByOrderByAsc(@Param("typeCode") String typeCode);

    void deleteByTypeCode(String typeCode);

    boolean existsByDocCode(String docCode);
}
