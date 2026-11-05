package com.badaklng.scheduling.repository;

import com.badaklng.scheduling.entity.SesRequestScheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesRequestScheduleHistoryRepository extends JpaRepository<SesRequestScheduleHistory, Long> {

    List<SesRequestScheduleHistory> findByScheduleIdOrderByHistoryIdAsc(Long scheduleId);
}
