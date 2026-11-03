package com.badaklng.approval.service;

import com.badaklng.approval.entity.SesUserExt;
import com.badaklng.approval.repository.SesUserExtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintenance of SES_USER_EXT — the SES-owned per-user extension holding
 * the "report to who" position (fallback for dynamic manager resolution).
 */
@Service
@RequiredArgsConstructor
public class SesUserExtService {

    private final SesUserExtRepository userExtRepository;

    @Transactional(readOnly = true)
    public SesUserExt findByUserId(String userId) {
        return userExtRepository.findByUserId(userId).orElse(null);
    }

    /**
     * Upsert the report-to position for a user.
     */
    @Transactional
    public SesUserExt save(String userId, String reportToPosId) {
        SesUserExt ext = userExtRepository.findByUserId(userId).orElseGet(() -> {
            SesUserExt e = new SesUserExt();
            e.setUserId(userId);
            return e;
        });
        ext.setReportToPosId(reportToPosId == null || reportToPosId.trim().isEmpty() ? null : reportToPosId.trim());
        return userExtRepository.save(ext);
    }
}
