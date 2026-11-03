package com.badaklng.request.repository;

import com.badaklng.request.entity.SesExtUploadRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * The upload-session bridge rows.
 *
 * <p>Derived queries only — nothing here needs {@code @Query}.</p>
 *
 * <p>Note what is deliberately ABSENT: a {@code findByUploadToken}. Resolving
 * a token is {@code UploadTokenService.resolve}'s job and it reads
 * {@code SES_REQUEST}. Adding the finder here is how a future caller would
 * accidentally authenticate against the projection instead of the authority —
 * see {@link SesExtUploadRequest}'s class javadoc.</p>
 */
@Repository
public interface SesExtUploadRequestRepository extends JpaRepository<SesExtUploadRequest, Long> {

    Optional<SesExtUploadRequest> findByRequestId(Long requestId);
}
