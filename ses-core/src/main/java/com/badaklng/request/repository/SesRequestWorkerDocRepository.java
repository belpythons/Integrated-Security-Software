package com.badaklng.request.repository;

import com.badaklng.request.entity.SesRequestWorkerDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesRequestWorkerDocRepository extends JpaRepository<SesRequestWorkerDoc, Long> {

    List<SesRequestWorkerDoc> findByWorkerId(Long workerId);

    /**
     * Documents for many workers in one query. {@code findByWorkerId} was
     * being called once per worker while building the screening checklist
     * ({@code /scheduling/manage}) and the request detail model, which is a
     * query per worker on pages that exist to show every worker at once.
     * Chunk the ids with {@code InChunks} (Oracle's 1000-expression cap).
     */
    List<SesRequestWorkerDoc> findByWorkerIdIn(List<Long> workerIds);

    void deleteByWorkerId(Long workerId);

    boolean existsByDocCode(String docCode);

    /**
     * How many document rows point at this {@code app_file} row.
     *
     * <p>More than one is normal, not a bug: {@code createRenewalDraft} copies
     * a worker's document rows carrying the <b>same</b> {@code file_id} rather
     * than duplicating multi-megabyte BLOBs. That sharing is what makes
     * deleting a replaced file dangerous, which is the only reason this count
     * exists — see {@code RequestService#deleteFileIfUnreferenced}.</p>
     */
    long countByFileId(String fileId);
}
