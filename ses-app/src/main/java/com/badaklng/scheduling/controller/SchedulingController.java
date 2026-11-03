package com.badaklng.scheduling.controller;

import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.service.RequestService;
import com.badaklng.scheduling.entity.ScheduleType;
import com.badaklng.ses.jdbc.InChunks;
import com.badaklng.scheduling.entity.SesRequestSchedule;
import com.badaklng.scheduling.entity.SesRequestScheduleHistory;
import com.badaklng.scheduling.model.CalendarDay;
import com.badaklng.scheduling.model.ScheduleQueueRow;
import com.badaklng.scheduling.service.SchedulingService;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduling screens: "/scheduling/queue" (admin-only — requests currently
 * blocked on a SCREENING_SCHEDULE or BADGE_RETRIEVAL_SCHEDULE checkpoint,
 * read) + "/scheduling/manage/{requestId}/{type}" (virtual CRUD/SUBMIT-grant
 * URL family for create/reschedule/cancel/attend/no-show — a scheduling
 * manager gets the full action set, a plain requestor gets a read-only view
 * of their own request's schedule, gated by an ownership check) +
 * "/scheduling/calendar" (month-grid + events list, open to every user —
 * every schedule for a manager, only the viewer's own for anyone else; see
 * {@code isManager} throughout this class). The request detail page also
 * gets a compact read-only summary via
 * {@code com.badaklng.scheduling.spi.SchedulingRequestBridge}, independent
 * of this controller.
 */
@Controller
@RequestMapping("/scheduling")
@RequiredArgsConstructor
public class SchedulingController {

    public static final String QUEUE_URL = "/scheduling/queue";
    public static final String FORM_URL = "/scheduling/queue/form";
    public static final String CALENDAR_URL = "/scheduling/calendar";

    private final SchedulingService schedulingService;
    private final RequestService requestService;
    private final SesRequestRepository requestRepository;
    private final SesRequestTypeRepository requestTypeRepository;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final UserDisplayNameService userDisplayNameService;
    private final com.badaklng.approval.service.ApprovalService approvalService;

    // ---------------------------------------------------------------
    // Queue
    // ---------------------------------------------------------------

    @GetMapping("/queue")
    public String queue(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(QUEUE_URL));
        // Collect the queue for every schedule type first, then resolve the
        // three things each row needs in three queries for the whole page.
        // The per-row shape (findById request + findById type + findCurrent)
        // measured 60 queries for one render of this screen.
        Map<ScheduleType, List<Long>> idsByType = new LinkedHashMap<>();
        Set<Long> allRequestIds = new LinkedHashSet<>();
        for (ScheduleType type : ScheduleType.values()) {
            List<Long> ids = schedulingService.findQueue(type).stream()
                    .map(SesApprovalInstance::getBusinessRefId)
                    .collect(Collectors.toList());
            idsByType.put(type, ids);
            allRequestIds.addAll(ids);
        }

        Map<Long, SesRequest> requestById = new HashMap<>();
        for (SesRequest r : InChunks.flatMap(allRequestIds, requestRepository::findAllById)) {
            requestById.put(r.getRequestId(), r);
        }
        Map<String, String> typeNameByCode = new HashMap<>();
        List<String> typeCodes = requestById.values().stream()
                .map(SesRequest::getTypeCode)
                .filter(code -> code != null)
                .distinct()
                .collect(Collectors.toList());
        for (SesRequestType t : InChunks.flatMap(typeCodes, requestTypeRepository::findAllById)) {
            typeNameByCode.put(t.getTypeCode(), t.getTypeName());
        }
        // One mutable row per (request, schedule type) -- see
        // SesRequestSchedule's class javadoc -- so this composite key is unique.
        Map<String, SesRequestSchedule> scheduleByRequestAndType = new HashMap<>();
        for (SesRequestSchedule sc : schedulingService.findCurrentFor(allRequestIds)) {
            scheduleByRequestAndType.put(sc.getRequestId() + "|" + sc.getScheduleType(), sc);
        }

        List<ScheduleQueueRow> rows = new ArrayList<>();
        for (Map.Entry<ScheduleType, List<Long>> entry : idsByType.entrySet()) {
            ScheduleType type = entry.getKey();
            for (Long requestId : entry.getValue()) {
                SesRequest request = requestById.get(requestId);
                if (request == null) {
                    continue;
                }
                String typeName = typeNameByCode.getOrDefault(request.getTypeCode(), request.getTypeCode());
                rows.add(new ScheduleQueueRow(requestId, request.getRequestNo(), typeName,
                        request.getRequestorUserId(), type,
                        scheduleByRequestAndType.get(requestId + "|" + type)));
            }
        }
        model.addAttribute("rows", rows);
        // Nama lengkap di samping username. Satu query batch untuk seluruh
        // halaman lewat UserDisplayNameService.displayNames — layar ini
        // menampilkan username mentah sejak dibuat, yaitu regresi yang
        // javadoc service itu sendiri tulis sebagai alasan keberadaannya.
        model.addAttribute("requestorNames", userDisplayNameService.displayNames(
                rows.stream().map(ScheduleQueueRow::getRequestorUserId).collect(Collectors.toList())));
        return "scheduling/queue";
    }

    // ---------------------------------------------------------------
    // Manage one (request, type)
    // ---------------------------------------------------------------

    @GetMapping("/manage/{requestId}/{type}")
    public String manage(@PathVariable Long requestId, @PathVariable ScheduleType type, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(FORM_URL));
        SesRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        boolean isManager = pageAccessGuard.hasAction(FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        if (!isManager && (request.getRequestorUserId() == null || !request.getRequestorUserId().equalsIgnoreCase(username))) {
            throw new AccessDeniedException("You do not have access to this schedule.");
        }
        model.addAttribute("isManager", isManager);

        model.addAttribute("requestId", requestId);
        model.addAttribute("scheduleType", type);
        requestService.detailModel(requestId).forEach(model::addAttribute);
        model.addAttribute("requestNo", request.getRequestNo());
        model.addAttribute("approvalSummary", approvalService.summaryModel("SES_REQUEST", requestId));

        Optional<SesRequestSchedule> schedule = schedulingService.findCurrent(requestId, type);
        model.addAttribute("schedule", schedule.orElse(null));
        List<SesRequestScheduleHistory> history = schedule
                .map(s -> schedulingService.findHistory(s.getScheduleId()))
                .orElse(java.util.Collections.<SesRequestScheduleHistory>emptyList());
        model.addAttribute("history", history);

        // The history table's "By" column used to print actor_user_id raw,
        // which is an APP_USER.USER_ID — a UUID. This controller already
        // injects UserDisplayNameService and already uses it in calendar() and
        // queue(); manage() was simply the one method that never did, so it
        // was the only screen in the module still showing one.
        //
        // displayNames() batches the whole column into one query, which is why
        // it is preferred over a displayName() call per row.
        model.addAttribute("historyActorNames", userDisplayNameService.displayNames(
                history.stream()
                        .map(SesRequestScheduleHistory::getActorUserId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet())));
        model.addAttribute("checkpointReachability", schedulingService.checkpointReachability(requestId, type));

        // The per-worker checklist. Only for SCREENING: badge retrieval is
        // collected by whoever turns up and has no per-worker outcome to
        // record, and the cleared-worker list it would show is already the
        // result of the screening that preceded it.
        if (type == ScheduleType.SCREENING) {
            List<com.badaklng.request.entity.SesRequestWorker> screeningWorkers = schedulingService.workersOf(requestId);
            model.addAttribute("screeningWorkers", screeningWorkers);
            // Need Ext Page Upload is a per-type toggle the admin already sets at
            // /admin/request-type — reused as-is here: when on, the checklist
            // shows whatever each worker already uploaded via the ext-upload-page
            // flow, so the reviewer does not have to leave this screen to check.
            boolean showWorkerDocs = requestTypeRepository.findById(request.getTypeCode())
                    .map(SesRequestType::isNeedExtPageUpload).orElse(false);
            if (showWorkerDocs) {
                model.addAttribute("workerDocs", requestService.findWorkerDocsByWorkerIds(
                        screeningWorkers.stream()
                                .map(com.badaklng.request.entity.SesRequestWorker::getWorkerId)
                                .collect(Collectors.toList())));
            }
        } else {
            model.addAttribute("clearedWorkers", schedulingService.clearedWorkers(requestId));
        }
        return "scheduling/manage";
    }

    @PostMapping("/manage/{requestId}/{type}/create")
    public String create(@PathVariable Long requestId, @PathVariable ScheduleType type,
                         @RequestParam String scheduledDate, @RequestParam String scheduledTime,
                         @RequestParam(required = false) String note,
                         @RequestParam(value = "returnTo", required = false) String returnTo,
                         RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        checkCanManage(requestId, username);
        try {
            schedulingService.create(requestId, type, parseDateTime(scheduledDate, scheduledTime), note, username);
            logService.log("SAVE", "SesRequestSchedule", "Scheduled " + type + " for request " + requestId,
                    requestId, "SES_REQUEST_SCHEDULE", request);
            ra.addFlashAttribute("successMsg", "Schedule created.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, "/scheduling/manage/" + requestId + "/" + type);
    }

    @PostMapping("/{scheduleId}/reschedule")
    public String reschedule(@PathVariable Long scheduleId, @RequestParam String scheduledDate,
                             @RequestParam String scheduledTime,
                             @RequestParam(required = false) String note,
                             @RequestParam(value = "returnTo", required = false) String returnTo,
                             RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestSchedule schedule = schedulingService.findById(scheduleId);
        checkCanManage(schedule.getRequestId(), username);
        try {
            schedulingService.reschedule(scheduleId, parseDateTime(scheduledDate, scheduledTime), note, username);
            logService.log("UPDATE", "SesRequestSchedule", "Rescheduled " + scheduleId, scheduleId,
                    "SES_REQUEST_SCHEDULE", request);
            ra.addFlashAttribute("successMsg", "Schedule updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, "/scheduling/manage/" + schedule.getRequestId() + "/" + schedule.getScheduleType());
    }

    @PostMapping("/{scheduleId}/cancel")
    public String cancel(@PathVariable Long scheduleId, @RequestParam("reason") String reason,
                         @RequestParam(value = "returnTo", required = false) String returnTo,
                         RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestSchedule schedule = schedulingService.findById(scheduleId);
        checkCanManage(schedule.getRequestId(), username);
        try {
            schedulingService.cancel(scheduleId, reason, username);
            logService.log("UPDATE", "SesRequestSchedule", "Cancelled schedule " + scheduleId, scheduleId,
                    "SES_REQUEST_SCHEDULE", request);
            ra.addFlashAttribute("successMsg", "Schedule cancelled.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, "/scheduling/manage/" + schedule.getRequestId() + "/" + schedule.getScheduleType());
    }

    @PostMapping("/{scheduleId}/attend")
    public String attend(@PathVariable Long scheduleId, @RequestParam(required = false) String note,
                         @RequestParam(value = "returnTo", required = false) String returnTo,
                         RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestSchedule schedule = schedulingService.findById(scheduleId);
        checkCanManage(schedule.getRequestId(), username);
        try {
            schedulingService.markAttended(scheduleId, note, username);
            logService.log("APPROVE", "SesRequestSchedule", "Marked attended " + scheduleId, scheduleId,
                    "SES_REQUEST_SCHEDULE", request);
            ra.addFlashAttribute("successMsg", "Attendance confirmed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, "/scheduling/manage/" + schedule.getRequestId() + "/" + schedule.getScheduleType());
    }

    /**
     * Records every worker's screening outcome in one post.
     *
     * <p>The statuses arrive as {@code status[<workerId>]=ATTENDED}, which
     * Spring binds straight into a {@code Map<Long, String>} — the same shape
     * as the checklist in the form, so there is no index to keep in sync
     * between the two.</p>
     *
     * <p>Completeness is enforced in {@code SchedulingService}, not here. The
     * form also disables its submit button until every worker is set, but a
     * disabled button is a convenience and this endpoint is reachable
     * directly.</p>
     */
    @PostMapping("/{scheduleId}/screening-draft")
    public String screeningDraft(@PathVariable Long scheduleId,
                                 @RequestParam(value = "status", required = false) Map<Long, String> statusParam,
                                 @RequestParam(value = "screeningDate", required = false) Map<Long, String> screeningDateRawParam,
                                 @RequestParam(value = "screeningReason", required = false) Map<Long, String> screeningReasonParam,
                                 @RequestParam(value = "note", required = false) String note,
                                 @RequestParam(value = "returnTo", required = false) String returnTo,
                                 RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestSchedule schedule = schedulingService.findById(scheduleId);
        checkCanManage(schedule.getRequestId(), username);
        try {
            ParsedScreeningParams p = parseScreeningParams(request, statusParam, screeningDateRawParam, screeningReasonParam);
            schedulingService.saveScreeningDraft(scheduleId, p.status, p.dateByWorkerId, p.screeningReason, note, username);
            ra.addFlashAttribute("successMsg", "Draf hasil screening berhasil disimpan.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, "/scheduling/manage/" + schedule.getRequestId() + "/" + schedule.getScheduleType());
    }

    @PostMapping("/{scheduleId}/screening-result")
    public String screeningResult(@PathVariable Long scheduleId,
                                  @RequestParam(value = "status", required = false) Map<Long, String> statusParam,
                                  @RequestParam(value = "screeningDate", required = false) Map<Long, String> screeningDateRawParam,
                                  @RequestParam(value = "screeningReason", required = false) Map<Long, String> screeningReasonParam,
                                  @RequestParam(value = "note", required = false) String note,
                                  @RequestParam(value = "returnTo", required = false) String returnTo,
                                  RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestSchedule schedule = schedulingService.findById(scheduleId);
        checkCanManage(schedule.getRequestId(), username);
        try {
            ParsedScreeningParams p = parseScreeningParams(request, statusParam, screeningDateRawParam, screeningReasonParam);

            SesRequestSchedule saved = schedulingService.completeScreening(scheduleId, p.status, p.dateByWorkerId, p.screeningReason, note, username);
            logService.log(saved.getStatus() == com.badaklng.scheduling.entity.ScheduleStatus.ATTENDED
                            ? "APPROVE" : "REJECT",
                    "SesRequestSchedule",
                    "Screening completed for " + scheduleId + ": " + saved.getResolutionReason(),
                    scheduleId, "SES_REQUEST_SCHEDULE", request);
            if (saved.getStatus() == com.badaklng.scheduling.entity.ScheduleStatus.ATTENDED) {
                ra.addFlashAttribute("successMsg",
                        "Screening selesai diverifikasi: " + saved.getResolutionReason()
                                + ". Pengajuan lanjut ke pengambilan badge untuk pekerja yang lolos.");
            } else {
                ra.addFlashAttribute("successMsg",
                        "Tidak ada peserta yang lolos screening. Silakan jadwalkan screening ulang melalui menu Ubah Jadwal.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, "/scheduling/manage/" + schedule.getRequestId() + "/" + schedule.getScheduleType());
    }

    private static class ParsedScreeningParams {
        Map<Long, String> status = new java.util.HashMap<>();
        Map<Long, java.util.Date> dateByWorkerId = new java.util.HashMap<>();
        Map<Long, String> screeningReason = new java.util.HashMap<>();
    }

    private ParsedScreeningParams parseScreeningParams(HttpServletRequest request,
                                                       Map<Long, String> statusParam,
                                                       Map<Long, String> screeningDateRawParam,
                                                       Map<Long, String> screeningReasonParam) {
        ParsedScreeningParams p = new ParsedScreeningParams();
        if (statusParam != null) p.status.putAll(statusParam);
        Map<Long, String> screeningDateRaw = new java.util.HashMap<>();
        if (screeningDateRawParam != null) screeningDateRaw.putAll(screeningDateRawParam);
        if (screeningReasonParam != null) p.screeningReason.putAll(screeningReasonParam);

        java.util.regex.Pattern statusPattern = java.util.regex.Pattern.compile("^status\\[(\\d+)\\]$");
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("^screeningDate\\[(\\d+)\\]$");
        java.util.regex.Pattern reasonPattern = java.util.regex.Pattern.compile("^screeningReason\\[(\\d+)\\]$");

        java.util.Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames != null && paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String paramVal = request.getParameter(paramName);
            if (paramVal == null) continue;

            java.util.regex.Matcher mStatus = statusPattern.matcher(paramName);
            if (mStatus.matches()) {
                try {
                    p.status.put(Long.parseLong(mStatus.group(1)), paramVal.trim());
                } catch (NumberFormatException ignored) {}
                continue;
            }

            java.util.regex.Matcher mDate = datePattern.matcher(paramName);
            if (mDate.matches()) {
                try {
                    screeningDateRaw.put(Long.parseLong(mDate.group(1)), paramVal.trim());
                } catch (NumberFormatException ignored) {}
                continue;
            }

            java.util.regex.Matcher mReason = reasonPattern.matcher(paramName);
            if (mReason.matches()) {
                try {
                    p.screeningReason.put(Long.parseLong(mReason.group(1)), paramVal.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        for (java.util.Map.Entry<Long, String> e : screeningDateRaw.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                try {
                    p.dateByWorkerId.put(e.getKey(), java.sql.Date.valueOf(e.getValue()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return p;
    }

    @PostMapping("/{scheduleId}/no-show")
    public String noShow(@PathVariable Long scheduleId, @RequestParam("reason") String reason,
                         @RequestParam(value = "returnTo", required = false) String returnTo,
                         RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestSchedule schedule = schedulingService.findById(scheduleId);
        checkCanManage(schedule.getRequestId(), username);
        try {
            schedulingService.markNoShow(scheduleId, reason, username);
            logService.log("REJECT", "SesRequestSchedule", "Marked no-show " + scheduleId, scheduleId,
                    "SES_REQUEST_SCHEDULE", request);
            ra.addFlashAttribute("successMsg", "Marked as no-show.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, "/scheduling/manage/" + schedule.getRequestId() + "/" + schedule.getScheduleType());
    }

    private void checkCanManage(Long requestId, String username) {
        if (pageAccessGuard.hasAction(FORM_URL, "SUBMIT")) {
            return;
        }
        if (requestId != null && approvalService.canActOnBusiness("SES_REQUEST", requestId, username)) {
            return;
        }
        pageAccessGuard.requireAction(FORM_URL, "SUBMIT");
    }

    // ---------------------------------------------------------------
    // Calendar
    // ---------------------------------------------------------------

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                           Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(CALENDAR_URL));

        // Scheduling managers (SUBMIT on the manage page) see every schedule; everyone
        // else sees only the events on requests they themselves submitted — a plain
        // page grant doesn't scope by ownership, so that filtering happens here.
        boolean isManager = pageAccessGuard.hasAction(FORM_URL, "SUBMIT");
        model.addAttribute("isManager", isManager);

        Calendar today = Calendar.getInstance();
        int y = year != null ? year : today.get(Calendar.YEAR);
        int m = month != null ? month : today.get(Calendar.MONTH) + 1; // 1-12

        Calendar monthStart = Calendar.getInstance();
        monthStart.clear();
        monthStart.set(y, m - 1, 1, 0, 0, 0);
        Calendar monthEnd = (Calendar) monthStart.clone();
        monthEnd.add(Calendar.MONTH, 1);

        List<SesRequestSchedule> schedules = schedulingService.findBetween(monthStart.getTime(), monthEnd.getTime());
        if (!isManager) {
            String username = SecurityUtils.getCurrentUsername();
            Set<Long> ownRequestIds = requestRepository.findByRequestorUserIdOrderByRequestIdDesc(username).stream()
                    .map(SesRequest::getRequestId).collect(Collectors.toSet());
            schedules = schedules.stream().filter(s -> ownRequestIds.contains(s.getRequestId())).collect(Collectors.toList());
        }
        model.addAttribute("schedules", schedules);

        Map<Integer, List<SesRequestSchedule>> byDay = new HashMap<>();
        for (SesRequestSchedule s : schedules) {
            Calendar c = Calendar.getInstance();
            c.setTime(s.getScheduledDate());
            byDay.computeIfAbsent(c.get(Calendar.DAY_OF_MONTH), k -> new ArrayList<>()).add(s);
        }

        // One query for the whole month instead of findById per schedule.
        Map<Long, SesRequest> calendarRequests = new HashMap<>();
        for (SesRequest r : InChunks.flatMap(
                schedules.stream().map(SesRequestSchedule::getRequestId).collect(Collectors.toList()),
                requestRepository::findAllById)) {
            calendarRequests.put(r.getRequestId(), r);
        }
        Map<Long, String> requestNoById = new HashMap<>();
        Map<Long, String> requestorById = new HashMap<>();
        for (SesRequestSchedule s : schedules) {
            SesRequest r = calendarRequests.get(s.getRequestId());
            requestNoById.put(s.getRequestId(), r != null ? r.getRequestNo() : "#" + s.getRequestId());
            requestorById.put(s.getRequestId(), r != null ? r.getRequestorUserId() : null);
        }
        model.addAttribute("requestNoById", requestNoById);
        model.addAttribute("requestorById", requestorById);
        // Nama lengkap di samping username. Satu query batch untuk seluruh
        // halaman lewat UserDisplayNameService.displayNames — layar ini
        // menampilkan username mentah sejak dibuat, yaitu regresi yang
        // javadoc service itu sendiri tulis sebagai alasan keberadaannya.
        model.addAttribute("requestorNames", userDisplayNameService.displayNames(requestorById.values()));

        // Leading filler cells so day 1 lands under the right weekday column (Sun=1..Sat=7 -> 0-indexed).
        int leading = monthStart.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH);
        boolean isCurrentMonth = y == today.get(Calendar.YEAR) && m == today.get(Calendar.MONTH) + 1;
        int todayDom = today.get(Calendar.DAY_OF_MONTH);

        List<CalendarDay> days = new ArrayList<>();
        for (int i = 0; i < leading; i++) {
            days.add(new CalendarDay(null, false, false, java.util.Collections.emptyList()));
        }
        for (int d = 1; d <= daysInMonth; d++) {
            days.add(new CalendarDay(d, true, isCurrentMonth && d == todayDom,
                    byDay.getOrDefault(d, java.util.Collections.emptyList())));
        }
        while (days.size() % 7 != 0) {
            days.add(new CalendarDay(null, false, false, java.util.Collections.emptyList()));
        }
        // Thymeleaf's #lists utility has no partition() method — chunk into weeks here instead.
        List<List<CalendarDay>> weeks = new ArrayList<>();
        for (int i = 0; i < days.size(); i += 7) {
            weeks.add(days.subList(i, i + 7));
        }
        model.addAttribute("weeks", weeks);

        model.addAttribute("year", y);
        model.addAttribute("month", m);
        Calendar prev = (Calendar) monthStart.clone();
        prev.add(Calendar.MONTH, -1);
        Calendar next = (Calendar) monthStart.clone();
        next.add(Calendar.MONTH, 1);
        model.addAttribute("prevYear", prev.get(Calendar.YEAR));
        model.addAttribute("prevMonth", prev.get(Calendar.MONTH) + 1);
        model.addAttribute("nextYear", next.get(Calendar.YEAR));
        model.addAttribute("nextMonth", next.get(Calendar.MONTH) + 1);
        model.addAttribute("monthLabel", new SimpleDateFormat("MMMM yyyy").format(monthStart.getTime()));

        return "scheduling/calendar";
    }

    @GetMapping("/calendar/events")
    @ResponseBody
    public List<Map<String, Object>> calendarEvents(
            @RequestParam(value = "start", required = false) String startStr,
            @RequestParam(value = "end", required = false) String endStr) {
        pageAccessGuard.require(CALENDAR_URL);
        boolean isManager = pageAccessGuard.hasAction(FORM_URL, "SUBMIT");

        Date start = parseIsoInstant(startStr);
        Date end = parseIsoInstant(endStr);
        if (start == null || end == null) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -1);
            start = c.getTime();
            c.add(Calendar.MONTH, 3);
            end = c.getTime();
        }

        List<SesRequestSchedule> schedules = schedulingService.findBetween(start, end);
        if (!isManager) {
            String username = SecurityUtils.getCurrentUsername();
            Set<Long> ownRequestIds = requestRepository.findByRequestorUserIdOrderByRequestIdDesc(username).stream()
                    .map(SesRequest::getRequestId).collect(Collectors.toSet());
            schedules = schedules.stream().filter(s -> ownRequestIds.contains(s.getRequestId())).collect(Collectors.toList());
        }

        Set<Long> requestIds = schedules.stream().map(SesRequestSchedule::getRequestId).collect(Collectors.toSet());
        Map<Long, SesRequest> requestCache = new HashMap<>();
        for (SesRequest r : InChunks.flatMap(requestIds, requestRepository::findAllById)) {
            requestCache.put(r.getRequestId(), r);
        }

        List<String> usernames = requestCache.values().stream()
                .filter(Objects::nonNull)
                .map(SesRequest::getRequestorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<String, String> displayNames = userDisplayNameService.displayNames(usernames);

        SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd-MM-yyyy HH:mm");

        List<Map<String, Object>> events = new ArrayList<>();
        for (SesRequestSchedule s : schedules) {
            SesRequest req = requestCache.get(s.getRequestId());
            String reqNo = req != null ? req.getRequestNo() : "#" + s.getRequestId();
            String reqUser = req != null ? req.getRequestorUserId() : "-";
            String reqName = displayNames.getOrDefault(reqUser, reqUser);
            String status = s.getStatus() != null ? s.getStatus().name() : "SCHEDULED";
            String typeLabel = s.getScheduleType() != null ? s.getScheduleType().getLabel() : "Screening";

            Map<String, Object> ev = new HashMap<>();
            ev.put("id", s.getScheduleId());
            ev.put("title", timeFmt.format(s.getScheduledDate()) + " " + typeLabel + " — " + reqNo);
            ev.put("start", isoFmt.format(s.getScheduledDate()));
            ev.put("className", "fc-status-" + status.toLowerCase());
            ev.put("url", "/scheduling/manage/" + s.getRequestId() + "/" + s.getScheduleType());

            Map<String, Object> props = new HashMap<>();
            props.put("scheduleId", s.getScheduleId());
            props.put("requestId", s.getRequestId());
            props.put("requestNo", reqNo);
            props.put("scheduleType", typeLabel);
            props.put("scheduleTypeCode", s.getScheduleType() != null ? s.getScheduleType().name() : "");
            props.put("status", status);
            props.put("isNoClearedScreening", s.isNoClearedScreening());
            props.put("scheduledDateFormatted", dateFmt.format(s.getScheduledDate()));
            props.put("requestorUserId", reqUser);
            props.put("requestorName", reqName);
            props.put("note", s.getNote());
            props.put("manageUrl", "/scheduling/manage/" + s.getRequestId() + "/" + s.getScheduleType());
            ev.put("extendedProps", props);

            events.add(ev);
        }
        return events;
    }

    private Date parseIsoInstant(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Date.from(java.time.OffsetDateTime.parse(s).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return Date.from(java.time.Instant.parse(s));
        } catch (Exception ignored) {
        }
        try {
            java.time.LocalDate ld = java.time.LocalDate.parse(s.substring(0, Math.min(10, s.length())));
            return Date.from(ld.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }
        return null;
    }


    /**
     * Where to land after an action: back to the approval detail page the
     * inline form was rendered on, when it asked for that, else this module's
     * own page as before.
     *
     * <p>Whitelisted to the one prefix rather than "any relative path": a
     * redirect target taken from a request parameter is an open redirect
     * waiting to happen, and this only ever needs the one destination.</p>
     */
    private static String redirectTo(String returnTo, String fallback) {
        if (returnTo != null && returnTo.startsWith("/approval/detail/")
                && returnTo.indexOf("//") < 0 && returnTo.indexOf(':') < 0) {
            return "redirect:" + returnTo;
        }
        return "redirect:" + fallback;
    }

    private Date parseDateTime(String datePart, String timePart) {
        try {
            // Separate <input type="date"> + <input type="time"> instead of a single
            // datetime-local field — platform's app.js rewrites datetime-local inputs
            // into a flatpickr instance (space-separated wire format, unreliable timing
            // on load), but leaves plain date/time inputs untouched.
            java.time.LocalDate date = java.time.LocalDate.parse(datePart);
            java.time.LocalTime time = java.time.LocalTime.parse(timePart);
            java.time.LocalDateTime parsed = java.time.LocalDateTime.of(date, time);
            return Date.from(parsed.atZone(java.time.ZoneId.systemDefault()).toInstant());
        } catch (NullPointerException | java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date/time: " + datePart + " " + timePart);
        }
    }
}
