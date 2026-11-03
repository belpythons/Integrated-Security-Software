package com.badaklng.scheduling.spi;

import com.badaklng.request.spi.RequestDetailContributor;
import com.badaklng.scheduling.entity.SesRequestSchedule;
import com.badaklng.scheduling.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The coupling surface FROM request/view.html back INTO this module: implements
 * request.spi.RequestDetailContributor so the requestor's read-only request
 * detail page can show a "Scheduled Event" card (see
 * scheduling/fragments/schedule-detail.html) without the request module ever
 * importing anything from com.badaklng.scheduling.
 */
@Component
@RequiredArgsConstructor
public class SchedulingRequestBridge implements RequestDetailContributor {

    private final SchedulingService schedulingService;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> contribute(Long requestId) {
        List<SesRequestSchedule> schedules = schedulingService.findByRequest(requestId);
        return Collections.singletonMap("schedules", schedules);
    }
}
