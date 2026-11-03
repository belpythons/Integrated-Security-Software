package com.badaklng.scheduling.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SesRequestScheduleTest {

    @Test
    @DisplayName("isNoClearedScreening returns true only for SCREENING with NO_SHOW and all-failed reason")
    void testIsNoClearedScreeningSchedule() {
        SesRequestSchedule schedule = new SesRequestSchedule();
        schedule.setScheduleType(ScheduleType.SCREENING);
        schedule.setStatus(ScheduleStatus.NO_SHOW);
        schedule.setResolutionReason("Tidak ada peserta yang lolos (0 lolos, 3 tidak)");

        assertTrue(schedule.isNoClearedScreening());

        // Backwards compatibility for legacy "0 lolos, X tidak" reason
        schedule.setResolutionReason("0 lolos, 2 tidak");
        assertTrue(schedule.isNoClearedScreening());

        // Standard no-show where people genuinely didn't arrive
        schedule.setResolutionReason("Pemohon dan pekerja tidak hadir di lokasi");
        assertFalse(schedule.isNoClearedScreening());

        // Badge retrieval schedule type must not trigger no-cleared screening
        schedule.setScheduleType(ScheduleType.BADGE_RETRIEVAL);
        schedule.setResolutionReason("Tidak ada peserta yang lolos (0 lolos, 3 tidak)");
        assertFalse(schedule.isNoClearedScreening());

        // Scheduled status must return false
        schedule.setScheduleType(ScheduleType.SCREENING);
        schedule.setStatus(ScheduleStatus.SCHEDULED);
        assertFalse(schedule.isNoClearedScreening());

        // Attended status must return false
        schedule.setStatus(ScheduleStatus.ATTENDED);
        assertFalse(schedule.isNoClearedScreening());
    }

    @Test
    @DisplayName("SesRequestScheduleHistory isNoClearedScreening checks action and reason")
    void testIsNoClearedScreeningHistory() {
        SesRequestScheduleHistory hist = new SesRequestScheduleHistory();
        hist.setAction(ScheduleHistoryAction.NO_SHOW);
        hist.setReason("Tidak ada peserta yang lolos (0 lolos, 2 tidak)");
        assertTrue(hist.isNoClearedScreening());

        hist.setReason("0 lolos, 2 tidak");
        assertTrue(hist.isNoClearedScreening());

        hist.setReason("Petugas melaporkan peserta tidak hadir");
        assertFalse(hist.isNoClearedScreening());

        hist.setAction(ScheduleHistoryAction.ATTENDED);
        hist.setReason("Tidak ada peserta yang lolos");
        assertFalse(hist.isNoClearedScreening());
    }
}
