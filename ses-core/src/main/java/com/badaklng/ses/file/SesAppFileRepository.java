package com.badaklng.ses.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesAppFileRepository extends JpaRepository<SesAppFile, String> {

    List<SesAppFile> findByRefId(String refId);

    List<SesAppFile> findByRefIdStartingWith(String refIdPrefix);

    void deleteByRefId(String refId);

    /** Metadata-only projection — skips loading the {@code file_data} blob. */
    Optional<FileMeta> findFileMetaByFileId(String fileId);

    interface FileMeta {
        String getFileName();
        String getFileType();
        Long getFileSize();
    }
}
