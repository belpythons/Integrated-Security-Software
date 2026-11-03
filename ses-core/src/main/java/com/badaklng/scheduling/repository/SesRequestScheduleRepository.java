package com.badaklng.scheduling.repository;

import com.badaklng.scheduling.entity.ScheduleType;
import com.badaklng.scheduling.entity.SesRequestSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesRequestScheduleRepository extends JpaRepository<SesRequestSchedule, Long> {

    List<SesRequestSchedule> findByRequestIdOrderByScheduleIdDesc(Long requestId);

    /** One mutable row per (request, type) — see SesRequestSchedule's class javadoc. */
    Optional<SesRequestSchedule> findByRequestIdAndScheduleType(Long requestId, ScheduleType scheduleType);

    /**
     * Current schedules for many requests in one query — the bulk form the
     * admin queue needs. {@code /scheduling/queue} previously called
     * {@link #findByRequestIdAndScheduleType} once per row, on top of a
     * {@code findById} for the request and another for its type: 3 queries
     * per row, 60 for one measured render. Chunk with {@code InChunks}.
     */
    List<SesRequestSchedule> findByRequestIdIn(List<Long> requestIds);

    /** Calendar view range query, regardless of status — past/cancelled/attended all show up. */
    List<SesRequestSchedule> findByScheduledDateBetweenOrderByScheduledDateAsc(Date from, Date to);
}
