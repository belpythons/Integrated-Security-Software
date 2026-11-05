package com.badaklng.scheduling.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.request.entity.ScreeningStatus;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.scheduling.entity.ScheduleStatus;
import com.badaklng.scheduling.entity.ScheduleType;
import com.badaklng.scheduling.entity.SesRequestSchedule;
import com.badaklng.scheduling.repository.SesRequestScheduleHistoryRepository;
import com.badaklng.scheduling.repository.SesRequestScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningDraftTest {

    @Mock
    private SesRequestScheduleRepository scheduleRepository;
    @Mock
    private SesRequestScheduleHistoryRepository historyRepository;
    @Mock
    private SesRequestRepository requestRepository;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private SesRequestWorkerRepository workerRepository;

    private SchedulingService service;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(scheduleRepository, historyRepository,
                requestRepository, approvalService, appUserRepository, workerRepository);
    }

    @Test
    @DisplayName("saveScreeningDraft updates partial worker statuses and keeps schedule SCHEDULED without triggering approval checkpoint")
    void draftSavesPartialWorkerStatusAndLeavesScheduleScheduled() {
        Long scheduleId = 100L;
        Long requestId = 50L;

        SesRequestSchedule schedule = new SesRequestSchedule();
        schedule.setScheduleId(scheduleId);
        schedule.setRequestId(requestId);
        schedule.setScheduleType(ScheduleType.SCREENING);
        schedule.setStatus(ScheduleStatus.SCHEDULED);

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        SesRequestWorker w1 = new SesRequestWorker();
        w1.setWorkerId(1L);
        w1.setRequestId(requestId);
        w1.setWorkerName("Worker 1");

        SesRequestWorker w2 = new SesRequestWorker();
        w2.setWorkerId(2L);
        w2.setRequestId(requestId);
        w2.setWorkerName("Worker 2");

        when(workerRepository.findByRequestIdOrderByLineNoAsc(requestId))
                .thenReturn(Arrays.asList(w1, w2));

        Map<Long, String> statusMap = new HashMap<>();
        statusMap.put(1L, ScreeningStatus.ACCEPTED); // Worker 2 is left untouched

        Date passDate = new Date();
        Map<Long, Date> dateMap = new HashMap<>();
        dateMap.put(1L, passDate);

        Map<Long, String> reasonMap = new HashMap<>();

        SesRequestSchedule result = service.saveScreeningDraft(scheduleId, statusMap, dateMap, reasonMap, "draft note", "testuser");

        assertEquals(ScheduleStatus.SCHEDULED, result.getStatus(), "Schedule status must remain SCHEDULED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SesRequestWorker>> captor = ArgumentCaptor.forClass(List.class);
        verify(workerRepository).saveAll(captor.capture());

        List<SesRequestWorker> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(ScreeningStatus.ACCEPTED, saved.get(0).getScreeningStatus());
        assertEquals(passDate, saved.get(0).getScreeningDate());
        assertNull(saved.get(0).getScreeningReason());

        assertEquals(ScreeningStatus.PENDING, saved.get(1).getScreeningStatus(), "Untouched worker must remain PENDING");

        // Crucial: Approval checkpoint MUST NOT be completed on draft save!
        verify(approvalService, never()).completeCheckpointByKey(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Changing a worker from ACCEPTED to REJECTED in draft updates reason and clears date")
    void draftSwitchingStatusUpdatesFieldsCorrectly() {
        Long scheduleId = 101L;
        Long requestId = 51L;

        SesRequestSchedule schedule = new SesRequestSchedule();
        schedule.setScheduleId(scheduleId);
        schedule.setRequestId(requestId);
        schedule.setScheduleType(ScheduleType.SCREENING);
        schedule.setStatus(ScheduleStatus.SCHEDULED);

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        SesRequestWorker w1 = new SesRequestWorker();
        w1.setWorkerId(10L);
        w1.setRequestId(requestId);
        w1.setWorkerName("Worker 10");
        w1.setScreeningStatus(ScreeningStatus.ACCEPTED);
        w1.setScreeningDate(new Date());

        when(workerRepository.findByRequestIdOrderByLineNoAsc(requestId))
                .thenReturn(Arrays.asList(w1));

        Map<Long, String> statusMap = new HashMap<>();
        statusMap.put(10L, ScreeningStatus.REJECTED);

        Map<Long, String> reasonMap = new HashMap<>();
        reasonMap.put(10L, "Hasil tensi tinggi");

        service.saveScreeningDraft(scheduleId, statusMap, null, reasonMap, null, "admin");

        assertEquals(ScreeningStatus.REJECTED, w1.getScreeningStatus());
        assertEquals("Hasil tensi tinggi", w1.getScreeningReason());
        assertNull(w1.getScreeningDate(), "screeningDate must be cleared when rejected");
    }

    @Test
    @DisplayName("Draft throws exception when schedule is not SCREENING or not SCHEDULED")
    void draftThrowsOnInvalidScheduleState() {
        Long scheduleId = 102L;
        SesRequestSchedule badgeSchedule = new SesRequestSchedule();
        badgeSchedule.setScheduleId(scheduleId);
        badgeSchedule.setScheduleType(ScheduleType.BADGE_RETRIEVAL);
        badgeSchedule.setStatus(ScheduleStatus.SCHEDULED);

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(badgeSchedule));

        assertThrows(IllegalStateException.class, () ->
                service.saveScreeningDraft(scheduleId, new HashMap<>(), null, null, null, "admin"));
    }
}
