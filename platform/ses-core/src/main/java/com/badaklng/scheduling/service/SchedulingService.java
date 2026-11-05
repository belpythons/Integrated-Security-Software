package com.badaklng.scheduling.service;

import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.service.ApprovalService;
import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.request.entity.ScreeningStatus;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.scheduling.entity.ScheduleHistoryAction;
import com.badaklng.scheduling.entity.ScheduleStatus;
import com.badaklng.scheduling.entity.ScheduleType;
import com.badaklng.scheduling.entity.SesRequestSchedule;
import com.badaklng.scheduling.entity.SesRequestScheduleHistory;
import com.badaklng.scheduling.repository.SesRequestScheduleHistoryRepository;
import com.badaklng.scheduling.repository.SesRequestScheduleRepository;
import com.badaklng.ses.jdbc.InChunks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Core service for the scheduling module. Like PaymentService, this is one
 * of the modules intentionally allowed to depend on
 * {@code com.badaklng.request}'s repository/entity types directly —
 * {@code ses_request_schedule.request_id} is a real DB FK, not a soft
 * pointer.
 * <p>
 * Reaching SCHEDULED never resolves the gated checkpoint by itself — only
 * an admin explicitly attesting the requestor showed up ({@link
 * #markAttended}) does. A no-show ({@link #markNoShow}) leaves the
 * checkpoint PENDING; the admin must reschedule or cancel — the system
 * never auto-resolves attendance.
 */
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private static final String BUSINESS_REF_TYPE = "SES_REQUEST";
    private static final String SOURCE_REF_PREFIX = "SES_SCHEDULE:";

    private final SesRequestScheduleRepository scheduleRepository;
    private final SesRequestScheduleHistoryRepository historyRepository;
    private final SesRequestRepository requestRepository;
    private final ApprovalService approvalService;
    private final AppUserRepository appUserRepository;
    private final SesRequestWorkerRepository workerRepository;

    // ---------------------------------------------------------------
    // Finders
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public SesRequestSchedule findById(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
    }

    @Transactional(readOnly = true)
    public Optional<SesRequestSchedule> findCurrent(Long requestId, ScheduleType type) {
        return scheduleRepository.findByRequestIdAndScheduleType(requestId, type);
    }

    /**
     * Current schedules for many requests, every type, in one query — the
     * bulk form of {@link #findCurrent}. Callers key the result themselves on
     * {@code (requestId, scheduleType)}, which is unique by design (see
     * {@code SesRequestSchedule}'s class javadoc).
     *
     * <p>Exists because the admin queue needs one schedule per (request,
     * type) pair for every row it renders, and calling {@link #findCurrent}
     * in that loop was a query per row.
     */
    @Transactional(readOnly = true)
    public List<SesRequestSchedule> findCurrentFor(Collection<Long> requestIds) {
        return InChunks.flatMap(requestIds, scheduleRepository::findByRequestIdIn);
    }

    /** Every schedule (any type) for a request, most recently created type first — used by request/view.html. */
    @Transactional(readOnly = true)
    public List<SesRequestSchedule> findByRequest(Long requestId) {
        return scheduleRepository.findByRequestIdOrderByScheduleIdDesc(requestId);
    }

    @Transactional(readOnly = true)
    public List<SesRequestScheduleHistory> findHistory(Long scheduleId) {
        return historyRepository.findByScheduleIdOrderByHistoryIdAsc(scheduleId);
    }

    /**
     * Admin queue for one schedule type: every request whose approval
     * instance is CURRENTLY blocked on that type's checkpoint key — i.e. a
     * "new scheduling task" the moment the instance reaches the configured
     * step. Requests already carrying a SCHEDULED/NO_SHOW/CANCELLED row
     * still show up here (checkpoint stays PENDING through all of those) so
     * the admin can act again from the same queue.
     */
    @Transactional(readOnly = true)
    public List<SesApprovalInstance> findQueue(ScheduleType type) {
        return approvalService.findActiveByCheckpointKey(BUSINESS_REF_TYPE, type.getCheckpointKey());
    }

    /** Informational only — whether marking attended right now would actually unlock a chain step. */
    @Transactional(readOnly = true)
    public ApprovalService.CheckpointReachability checkpointReachability(Long requestId, ScheduleType type) {
        return approvalService.checkCheckpoint(BUSINESS_REF_TYPE, requestId, type.getCheckpointKey());
    }

    // ---------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------

    /**
     * First schedule for a (request, type) — fails if one already exists;
     * use {@link #reschedule} instead.
     * <p>
     * Also enforces step ordering server-side: create() only succeeds while
     * {@code type}'s checkpoint is the instance's ACTIVE gate. Without this,
     * an admin who knows/guesses a requestId could POST straight to
     * BADGE_RETRIEVAL's create URL before SCREENING's step ever resolved —
     * bypassing screening entirely. The admin queue (findQueue) already
     * only lists ACTIVE checkpoints, but that's advisory UI filtering; this
     * is the actual enforcement.
     */
    @Transactional
    public SesRequestSchedule create(Long requestId, ScheduleType type, Date scheduledDate, String note, String username) {
        if (scheduledDate == null) {
            throw new IllegalArgumentException("A schedule date and time is required.");
        }
        if (scheduleRepository.findByRequestIdAndScheduleType(requestId, type).isPresent()) {
            throw new IllegalStateException("A schedule already exists for this request — use reschedule instead.");
        }
        requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        ApprovalService.CheckpointReachability reachability = checkpointReachability(requestId, type);
        if (reachability != ApprovalService.CheckpointReachability.ACTIVE) {
            throw new IllegalStateException(unreachableMessage(type, reachability));
        }

        SesRequestSchedule schedule = new SesRequestSchedule();
        schedule.setRequestId(requestId);
        schedule.setScheduleType(type);
        schedule.setStatus(ScheduleStatus.SCHEDULED);
        schedule.setScheduledDate(scheduledDate);
        schedule.setNote(note);
        schedule = scheduleRepository.save(schedule);

        appendHistory(schedule.getScheduleId(), ScheduleHistoryAction.CREATED, scheduledDate, note, username);
        notifyRequestor(schedule, type.getLabel() + " Dijadwalkan",
                "Jadwal <b>" + type.getLabel() + "</b> untuk pengajuan Anda telah ditetapkan.",
                "TERJADWAL", note, "SCHEDULE_SET");
        return schedule;
    }

    /**
     * Sets a new date/time and puts the row back to SCHEDULED — valid from
     * any non-ATTENDED status (SCHEDULED itself, NO_SHOW, or CANCELLED), so
     * this is also how a no-show or a cancelled booking gets a fresh slot.
     */
    @Transactional
    public SesRequestSchedule reschedule(Long scheduleId, Date newDate, String note, String username) {
        if (newDate == null) {
            throw new IllegalArgumentException("A schedule date and time is required.");
        }
        SesRequestSchedule schedule = findById(scheduleId);
        if (schedule.getStatus() == ScheduleStatus.ATTENDED) {
            throw new IllegalStateException("Cannot reschedule — the requestor already attended.");
        }
        schedule.setScheduledDate(newDate);
        schedule.setStatus(ScheduleStatus.SCHEDULED);
        schedule.setNote(note);
        schedule.setResolutionReason(null);
        schedule.setResolvedBy(null);
        schedule.setResolvedDate(null);
        schedule = scheduleRepository.save(schedule);

        // Reset per-worker screening status when rescheduling a screening session.
        if (schedule.getScheduleType() == ScheduleType.SCREENING) {
            for (SesRequestWorker w : workerRepository.findByRequestIdOrderByLineNoAsc(schedule.getRequestId())) {
                w.setScreeningStatus(ScreeningStatus.PENDING);
                w.setScreeningDate(null);
                w.setScreeningReason(null);
            }
            workerRepository.saveAll(workerRepository.findByRequestIdOrderByLineNoAsc(schedule.getRequestId()));
        }

        appendHistory(schedule.getScheduleId(), ScheduleHistoryAction.RESCHEDULED, newDate, note, username);
        notifyRequestor(schedule, "Perubahan Jadwal " + schedule.getScheduleType().getLabel(),
                "Jadwal <b>" + schedule.getScheduleType().getLabel() + "</b> untuk pengajuan Anda telah diubah.",
                "DIJADWALKAN ULANG", note, "SCHEDULE_MOVED");
        return schedule;
    }

    /**
     * Tells the requestor their screening / badge-retrieval date changed.
     *
     * <p>Neither this module nor payment sent the requestor anything before —
     * a date could be set, moved or cancelled and the only way to find out was
     * to go and look at the calendar. The approval module owns requestor
     * contact details, so the mail is composed there; this just says what
     * happened.</p>
     *
     * <p>Cancelled uses the amber PENDING palette rather than a red one on
     * purpose: a cancelled slot is "a new date is coming", not a rejection.</p>
     *
     * <p>SES_REQUEST is hardcoded because ses_request_schedule.request_id IS a
     * request id — this module schedules nothing else.</p>
     */
    private void notifyRequestor(SesRequestSchedule schedule, String headline, String intro,
                                 String statusLabel, String note, String mailKey) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("Jenis Jadwal", schedule.getScheduleType().getLabel());
        if (schedule.getScheduledDate() != null && schedule.getStatus() != ScheduleStatus.CANCELLED) {
            detail.put("Tanggal & Jam", new SimpleDateFormat("dd-MM-yyyy HH:mm").format(schedule.getScheduledDate()));
        }
        approvalService.notifyRequestorInfo("SES_REQUEST", schedule.getRequestId(),
                ApprovalStatus.PENDING, headline, intro, statusLabel, detail, note, mailKey);
    }

    /** Admin cancels a still-open schedule — a reason is mandatory, same convention as payment rejection. */
    @Transactional
    public SesRequestSchedule cancel(Long scheduleId, String reason, String username) {
        requireReason(reason, "cancel");
        SesRequestSchedule schedule = findById(scheduleId);
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED && schedule.getStatus() != ScheduleStatus.NO_SHOW) {
            throw new IllegalStateException("Cannot cancel while status is " + schedule.getStatus());
        }
        String userId = resolveUserId(username);
        schedule.setStatus(ScheduleStatus.CANCELLED);
        schedule.setResolutionReason(reason);
        schedule.setResolvedBy(userId);
        schedule.setResolvedDate(new Date());
        schedule = scheduleRepository.save(schedule);

        appendHistory(schedule.getScheduleId(), ScheduleHistoryAction.CANCELLED, null, reason, username);
        notifyRequestor(schedule, "Jadwal " + schedule.getScheduleType().getLabel() + " Dibatalkan",
                "Jadwal <b>" + schedule.getScheduleType().getLabel()
                        + "</b> untuk pengajuan Anda dibatalkan. Anda akan dihubungi untuk jadwal pengganti.",
                "DIBATALKAN", reason, "SCHEDULE_CANCELLED");
        return schedule;
    }

    /**
     * Admin attests the requestor showed up — the ONLY path that completes
     * the gated checkpoint. Allowed only from SCHEDULED; unconditionally
     * calls {@link ApprovalService#completeCheckpointByKey} — a safe no-op
     * (logged at WARN, never throws) if no chain step is currently
     * configured with this schedule type's checkpoint key.
     */
    @Transactional
    public SesRequestSchedule markAttended(Long scheduleId, String note, String username) {
        SesRequestSchedule schedule = findById(scheduleId);
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot mark attended while status is " + schedule.getStatus());
        }
        String userId = resolveUserId(username);
        schedule.setStatus(ScheduleStatus.ATTENDED);
        schedule.setResolvedBy(userId);
        schedule.setResolvedDate(new Date());
        schedule = scheduleRepository.save(schedule);

        appendHistory(schedule.getScheduleId(), ScheduleHistoryAction.ATTENDED, null, note, username);

        approvalService.completeCheckpointByKey(BUSINESS_REF_TYPE, schedule.getRequestId(),
                schedule.getScheduleType().getCheckpointKey(), SOURCE_REF_PREFIX + scheduleId, note, userId, username);
        return schedule;
    }

    /**
     * Admin records a no-show — a reason is mandatory. Deliberately does NOT
     * touch the checkpoint itself (stays PENDING): the system never
     * auto-resolves attendance.
     * <p>
     * For SCREENING specifically, a no-show is fatal to the whole request —
     * screening happens outside the system and a no-show means it never
     * happened, so there is nothing left to gate: the entire approval
     * instance (and, via {@code RequestApprovalBridge}, SES_REQUEST.status)
     * is rejected right here, and the requestor must submit a fresh
     * request. BADGE_RETRIEVAL no-shows keep the old behavior — the admin
     * explicitly {@link #reschedule} or {@link #cancel} next.
     * <p>
     * <b>For screening, prefer {@link #completeScreening}.</b> This method
     * treats the whole roster as one outcome, which is right for badge
     * retrieval but is the all-or-nothing behaviour per-worker screening
     * exists to replace: five attendees and one absentee used to leave the
     * officer no honest answer, because the honest one threw the five away.
     * Kept for BADGE_RETRIEVAL and for a screening where genuinely nobody
     * turned up.
     */
    @Transactional
    public SesRequestSchedule markNoShow(Long scheduleId, String reason, String username) {
        requireReason(reason, "mark no-show");
        SesRequestSchedule schedule = findById(scheduleId);
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot mark no-show while status is " + schedule.getStatus());
        }
        String userId = resolveUserId(username);
        schedule.setStatus(ScheduleStatus.NO_SHOW);
        schedule.setResolutionReason(reason);
        schedule.setResolvedBy(userId);
        schedule.setResolvedDate(new Date());
        schedule = scheduleRepository.save(schedule);

        appendHistory(schedule.getScheduleId(), ScheduleHistoryAction.NO_SHOW, null, reason, username);

        if (schedule.getScheduleType() == ScheduleType.SCREENING) {
            // Mark every worker absent too, so the per-worker record agrees
            // with the schedule-level one. Without this, a request rejected
            // here would leave its workers sitting at PENDING and the badge
            // retrieval list would still be willing to print for them if the
            // rejection were ever reversed.
            markAllWorkers(schedule.getRequestId(), ScreeningStatus.NO_SHOW);
            approvalService.rejectByBusinessRef(BUSINESS_REF_TYPE, schedule.getRequestId(),
                    "Screening no-show: " + reason, userId, username);
        }
        return schedule;
    }

    // ---------------------------------------------------------------
    // Per-worker screening
    // ---------------------------------------------------------------

    /**
     * Records each worker's screening outcome and then decides what happens to
     * the request.
     *
     * <p>Replaces the all-or-nothing screening outcome. Six workers were
     * covered by a single ATTENDED/NO_SHOW, and because a screening no-show
     * rejects the whole request, an officer looking at five attendees and one
     * absentee had to choose between recording a lie and discarding five
     * people's clearance.</p>
     *
     * <h2>The rules</h2>
     * <ul>
     *   <li><b>At least one ATTENDED</b> → the schedule is ATTENDED, the
     *       SCREENING_SCHEDULE checkpoint closes, and the request moves on to
     *       badge retrieval <i>for the cleared workers only</i>.</li>
     *   <li><b>Nobody ATTENDED</b> → the whole request is rejected, with a
     *       reason naming the split. There is nothing left to gate: no badge
     *       will be printed for anyone.</li>
     * </ul>
     *
     * <p>Note what is NOT a rule: a FAILED or NO_SHOW worker does not hold the
     * request back. They are recorded, excluded from badge printing, and the
     * request proceeds for everyone else. The requestor submits a fresh
     * request for the ones who need another attempt — which is also the only
     * way the engine can work, since an approval instance has already frozen
     * its chain by this point.</p>
     *
     * @param statusByWorkerId every worker of the request, keyed by worker id.
     *        Partial maps are refused: see {@link #assertEveryWorkerDecided}.
     */
    @Transactional
    public SesRequestSchedule completeScreening(Long scheduleId, Map<Long, String> statusByWorkerId,
                                                Map<Long, Date> dateByWorkerId,
                                                Map<Long, String> reasonByWorkerId,
                                                String note, String username) {
        SesRequestSchedule schedule = findById(scheduleId);
        if (schedule.getScheduleType() != ScheduleType.SCREENING) {
            throw new IllegalStateException(
                    "Per-worker outcomes apply to screening only, not " + schedule.getScheduleType());
        }
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "Cannot complete screening while status is " + schedule.getStatus());
        }

        List<SesRequestWorker> workers = workerRepository.findByRequestIdOrderByLineNoAsc(schedule.getRequestId());
        if (workers.isEmpty()) {
            throw new IllegalStateException("Pengajuan ini tidak memiliki data pekerja.");
        }
        assertEveryWorkerDecided(workers, statusByWorkerId);

        // Validate per-status requirements: ACCEPTED must have a date, REJECTED must have a reason.
        for (SesRequestWorker w : workers) {
            String status = statusByWorkerId.get(w.getWorkerId()).trim();
            if (ScreeningStatus.ACCEPTED.equals(status)) {
                if (dateByWorkerId == null || dateByWorkerId.get(w.getWorkerId()) == null) {
                    throw new IllegalArgumentException(
                            "Tanggal kelulusan screening wajib diisi untuk " + w.getWorkerName());
                }
            } else if (ScreeningStatus.REJECTED.equals(status)) {
                if (reasonByWorkerId == null || reasonByWorkerId.get(w.getWorkerId()) == null
                        || reasonByWorkerId.get(w.getWorkerId()).trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Alasan tidak lulus wajib diisi untuk " + w.getWorkerName());
                }
            }
        }

        int cleared = 0;
        int rejected = 0;
        for (SesRequestWorker w : workers) {
            String status = statusByWorkerId.get(w.getWorkerId()).trim();
            w.setScreeningStatus(status);
            if (ScreeningStatus.isCleared(status)) {
                cleared++;
                w.setScreeningDate(dateByWorkerId != null ? dateByWorkerId.get(w.getWorkerId()) : null);
                w.setScreeningReason(null);
            } else {
                rejected++;
                w.setScreeningReason(reasonByWorkerId != null ? reasonByWorkerId.get(w.getWorkerId()).trim() : null);
                w.setScreeningDate(null);
            }
        }
        workerRepository.saveAll(workers);

        String summary = cleared + " lolos, " + rejected + " tidak"
                + (note == null || note.trim().isEmpty() ? "" : " — " + note.trim());
        String userId = resolveUserId(username);

        if (cleared == 0) {
            // Nobody cleared. Mark the schedule as NO_SHOW but do NOT reject the request permanently.
            // The request remains active; Security can reschedule and try again.
            String failSummary = "Tidak ada peserta yang lolos (" + summary + ")";
            schedule.setStatus(ScheduleStatus.NO_SHOW);
            schedule.setResolutionReason(failSummary);
            schedule.setResolvedBy(userId);
            schedule.setResolvedDate(new Date());
            schedule = scheduleRepository.save(schedule);
            appendHistory(schedule.getScheduleId(), ScheduleHistoryAction.NO_SHOW, null, failSummary, username);
            return schedule;
        }

        schedule.setStatus(ScheduleStatus.ATTENDED);
        schedule.setResolutionReason(summary);
        schedule.setResolvedBy(userId);
        schedule.setResolvedDate(new Date());
        schedule = scheduleRepository.save(schedule);
        appendHistory(schedule.getScheduleId(), ScheduleHistoryAction.ATTENDED, null, summary, username);

        approvalService.completeCheckpointByKey(BUSINESS_REF_TYPE, schedule.getRequestId(),
                schedule.getScheduleType().getCheckpointKey(), SOURCE_REF_PREFIX + scheduleId,
                summary, userId, username);
        return schedule;
    }

    /**
     * Saves screening outcomes per worker as a draft without concluding the schedule.
     *
     * <p>Workers whose status was supplied have their status, date, and reason updated.
     * The schedule remains {@link ScheduleStatus#SCHEDULED} and the approval checkpoint
     * is not completed, allowing the officer to return and complete evaluation later.</p>
     */
    @Transactional
    public SesRequestSchedule saveScreeningDraft(Long scheduleId,
                                                Map<Long, String> statusByWorkerId,
                                                Map<Long, Date> dateByWorkerId,
                                                Map<Long, String> reasonByWorkerId,
                                                String note, String username) {
        SesRequestSchedule schedule = findById(scheduleId);
        if (schedule.getScheduleType() != ScheduleType.SCREENING) {
            throw new IllegalStateException(
                    "Draf status per-pekerja hanya berlaku untuk screening, bukan " + schedule.getScheduleType());
        }
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "Tidak dapat menyimpan draf screening saat status " + schedule.getStatus());
        }

        List<SesRequestWorker> workers = workerRepository.findByRequestIdOrderByLineNoAsc(schedule.getRequestId());
        if (workers.isEmpty()) {
            throw new IllegalStateException("Pengajuan ini tidak memiliki data pekerja.");
        }

        for (SesRequestWorker w : workers) {
            if (statusByWorkerId != null && statusByWorkerId.containsKey(w.getWorkerId())) {
                String status = statusByWorkerId.get(w.getWorkerId());
                if (status != null && !status.trim().isEmpty()) {
                    status = status.trim();
                    w.setScreeningStatus(status);
                    if (ScreeningStatus.isCleared(status)) {
                        w.setScreeningDate(dateByWorkerId != null ? dateByWorkerId.get(w.getWorkerId()) : null);
                        w.setScreeningReason(null);
                    } else if (ScreeningStatus.REJECTED.equals(status)) {
                        w.setScreeningReason(reasonByWorkerId != null && reasonByWorkerId.get(w.getWorkerId()) != null
                                ? reasonByWorkerId.get(w.getWorkerId()).trim() : null);
                        w.setScreeningDate(null);
                    }
                }
            }
        }
        workerRepository.saveAll(workers);
        return schedule;
    }

    /**
     * Every worker must carry a decided status, and no posted id may be a
     * stranger.
     *
     * <p>Re-checked here even though the UI disables its own button until the
     * form is complete. A disabled button is a convenience, not a control: the
     * POST is reachable directly, and a partial submission would close the
     * screening checkpoint with some workers still PENDING — who would then be
     * neither printed nor explained.</p>
     *
     * <p>The unknown-id check is the ownership half. Without it a posted
     * {@code workerId} from a different request would be written to, which is
     * the same IDOR the upload page closes in
     * {@code RequestService.assertUploadTarget}.</p>
     */
    private void assertEveryWorkerDecided(List<SesRequestWorker> workers, Map<Long, String> statusByWorkerId) {
        if (statusByWorkerId == null) {
            throw new IllegalArgumentException("Status screening setiap pekerja wajib diisi.");
        }
        List<String> missing = new java.util.ArrayList<>();
        for (SesRequestWorker w : workers) {
            String status = statusByWorkerId.get(w.getWorkerId());
            if (!ScreeningStatus.isDecided(status)) {
                missing.add(w.getWorkerName());
                continue;
            }
            if (!ScreeningStatus.isValid(status.trim()) || ScreeningStatus.PENDING.equals(status.trim())) {
                throw new IllegalArgumentException(
                        "Status screening tidak dikenal untuk " + w.getWorkerName() + ": " + status);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Status screening belum diisi untuk: " + String.join(", ", missing));
        }

        java.util.Set<Long> known = new java.util.HashSet<>();
        for (SesRequestWorker w : workers) {
            known.add(w.getWorkerId());
        }
        for (Long posted : statusByWorkerId.keySet()) {
            if (!known.contains(posted)) {
                throw new IllegalArgumentException("Pekerja tidak ditemukan pada pengajuan ini: " + posted);
            }
        }
    }

    /** Workers of a request who cleared screening — the only ones a badge is printed for. */
    @Transactional(readOnly = true)
    public List<SesRequestWorker> clearedWorkers(Long requestId) {
        List<SesRequestWorker> out = new java.util.ArrayList<>();
        for (SesRequestWorker w : workerRepository.findByRequestIdOrderByLineNoAsc(requestId)) {
            if (ScreeningStatus.isCleared(w.getScreeningStatus())) {
                out.add(w);
            }
        }
        return out;
    }

    /** All workers of a request, for the checklist UI. */
    @Transactional(readOnly = true)
    public List<SesRequestWorker> workersOf(Long requestId) {
        return workerRepository.findByRequestIdOrderByLineNoAsc(requestId);
    }

    private void markAllWorkers(Long requestId, String status) {
        List<SesRequestWorker> workers = workerRepository.findByRequestIdOrderByLineNoAsc(requestId);
        for (SesRequestWorker w : workers) {
            w.setScreeningStatus(status);
        }
        workerRepository.saveAll(workers);
    }

    // ---------------------------------------------------------------
    // Calendar
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SesRequestSchedule> findBetween(Date from, Date to) {
        return scheduleRepository.findByScheduledDateBetweenOrderByScheduledDateAsc(from, to);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String unreachableMessage(ScheduleType type, ApprovalService.CheckpointReachability reachability) {
        switch (reachability) {
            case NOT_YET_REACHED:
                return "Cannot schedule " + type.getLabel() + " yet — an earlier approval step must complete first.";
            case ALREADY_DONE:
                return type.getLabel() + " has already been completed for this request.";
            case ALREADY_SKIPPED:
                return type.getLabel() + " was already skipped for this request.";
            case NOT_FOUND:
            default:
                return "No active approval step is currently waiting on " + type.getLabel() + " for this request.";
        }
    }

    private void requireReason(String reason, String action) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason is required to " + action + " a schedule.");
        }
    }

    private void appendHistory(Long scheduleId, ScheduleHistoryAction action, Date scheduledDate, String reason, String username) {
        SesRequestScheduleHistory h = new SesRequestScheduleHistory();
        h.setScheduleId(scheduleId);
        h.setAction(action);
        h.setScheduledDate(scheduledDate);
        h.setReason(reason);
        h.setActorUserId(resolveUserId(username));
        h.setActorDate(new Date());
        historyRepository.save(h);
    }

    private String resolveUserId(String username) {
        return appUserRepository.findByUsername(username).map(AppUser::getUserId).orElse(username);
    }
}
