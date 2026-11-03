package com.badaklng.request.job;

import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.entity.SesRequestWorkerDoc;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestWorkerDocRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.ses.file.AppFileService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Cancels badge requests whose documents were never uploaded.
 *
 * <p>The upload link lives seven days. A request whose link lapses is not
 * lost — the requestor can reissue it from the detail page — but one that
 * nobody ever comes back to would otherwise sit in
 * {@link RequestStatus#WAITING_FOR_UPLOAD} forever, cluttering the list and
 * the dashboard's in-flight count with work that is not actually happening.</p>
 *
 * <h3>Why the threshold is much longer than the link</h3>
 * <p>{@code REQUEST_UPLOAD_EXPIRY_DAYS} defaults to <b>30</b>, not 7.
 * Cancelling the moment the link lapsed would delete work during the exact
 * window the resend button exists to rescue — somebody back from two weeks'
 * leave would find their submission gone rather than one click from alive.
 * Thirty days says "nobody is coming back" with far more confidence than
 * seven.</p>
 *
 * <h3>What it destroys</h3>
 * <p>Partially uploaded documents and their {@code app_file} BLOBs. Those are
 * unreachable once the request is cancelled — no screen lists them and no
 * approval references them — so leaving them would be the same orphan leak
 * {@code sql/51} exists to clean up after. The request row, its workers and
 * its history all survive; only the status changes and the blobs go.</p>
 *
 * <p>⚠️ {@code app.scheduler.auto-start=false} in this application, so this
 * job does nothing until someone starts the {@code REQ_EXPIRY} schedule once
 * at {@code /admin/scheduler}. That is deliberate — a sweeper that deletes
 * things should not begin on its own the first time a WAR is deployed
 * somewhere new.</p>
 */
@Slf4j
@DisallowConcurrentExecution
public class RequestExpiryJob implements Job {

    /** Matches the {@code app_cat} row seeded by {@code sql/61}. */
    public static final String SCHEDULE_KEY = "REQ_EXPIRY";

    private static final String DAYS_PARAM = "REQUEST_UPLOAD_EXPIRY_DAYS";
    private static final int DEFAULT_DAYS = 30;

    /**
     * Upper bound per tick. A sweep that tried to cancel ten thousand rows in
     * one transaction would hold locks for minutes; the finder returns oldest
     * first, so the next tick simply continues.
     */
    private static final int BATCH = 200;

    @Autowired private SesRequestRepository requestRepository;
    @Autowired private SesRequestWorkerRepository workerRepository;
    @Autowired private SesRequestWorkerDocRepository workerDocRepository;
    @Autowired private AppFileService appFileService;
    @Autowired private AppParameterService paramService;

    @Override
    public void execute(JobExecutionContext context) {
        int days = days();
        Date cutoff = daysAgo(days);

        List<SesRequest> stale = requestRepository
                .findByStatusAndCreatedDateBeforeOrderByCreatedDateAsc(
                        RequestStatus.WAITING_FOR_UPLOAD, cutoff);
        if (stale.isEmpty()) {
            return;
        }

        int cancelled = 0;
        int blobsFreed = 0;
        for (SesRequest request : stale) {
            if (cancelled >= BATCH) {
                log.info("RequestExpiryJob: batch limit {} reached; {} older rows left for the next tick",
                        BATCH, stale.size() - cancelled);
                break;
            }
            try {
                blobsFreed += cancelOne(request);
                cancelled++;
            } catch (RuntimeException ex) {
                // One bad row must not stall the sweep, exactly as SesMailJob
                // swallows a single undeliverable address.
                log.warn("RequestExpiryJob: could not cancel {}: {}",
                        request.getRequestNo(), ex.getMessage());
            }
        }

        if (cancelled > 0) {
            log.info("RequestExpiryJob: cancelled {} request(s) older than {} days with no documents; "
                    + "{} orphan file(s) removed", cancelled, days, blobsFreed);
        }
    }

    /** @return how many {@code app_file} rows this request's documents freed. */
    private int cancelOne(SesRequest request) {
        int freed = 0;
        for (SesRequestWorker worker : workerRepository
                .findByRequestIdOrderByLineNoAsc(request.getRequestId())) {
            for (SesRequestWorkerDoc doc : workerDocRepository.findByWorkerId(worker.getWorkerId())) {
                if (doc.getFileId() == null || doc.getFileId().trim().isEmpty()) {
                    continue;
                }
                try {
                    appFileService.delete(doc.getFileId());
                    freed++;
                } catch (RuntimeException ex) {
                    // A missing blob is not a reason to keep the request open.
                    log.debug("RequestExpiryJob: file {} already gone", doc.getFileId());
                }
            }
            workerDocRepository.deleteByWorkerId(worker.getWorkerId());
        }

        request.setStatus(RequestStatus.CANCELLED);
        // Kill the token too. The request is terminal now, and resolve()
        // already refuses on status — but leaving a live-looking token on a
        // dead request is the kind of thing that reads as a bug later.
        request.setUploadTokenUsed("Y");
        requestRepository.save(request);
        return freed;
    }

    /** Blank-safe and non-positive-safe: a misconfigured row must not sweep everything. */
    private int days() {
        String raw = paramService.getValue(DAYS_PARAM, String.valueOf(DEFAULT_DAYS));
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_DAYS;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 1) {
                log.warn("{} is {}; refusing to sweep with a non-positive window, using {}",
                        DAYS_PARAM, parsed, DEFAULT_DAYS);
                return DEFAULT_DAYS;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("{} is not a number ({}); using {}", DAYS_PARAM, raw, DEFAULT_DAYS);
            return DEFAULT_DAYS;
        }
    }

    private static Date daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return cal.getTime();
    }
}
