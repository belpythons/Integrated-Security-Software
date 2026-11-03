package com.badaklng.scheduling.spi;

import com.badaklng.approval.spi.CheckpointActionProvider;
import com.badaklng.approval.spi.CheckpointTaskType;
import com.badaklng.approval.spi.SimpleCheckpointTaskType;
import com.badaklng.scheduling.entity.ScheduleType;
import com.badaklng.scheduling.entity.SesRequestSchedule;
import com.badaklng.scheduling.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The ENTIRE coupling surface between com.badaklng.scheduling and the
 * approval module's spi package — the only file in this module allowed to
 * import com.badaklng.approval.spi.* (mirrors
 * com.badaklng.payment.spi.PaymentApprovalBridge's role for the payment
 * module). Everything else in this module calls
 * {@link com.badaklng.approval.service.ApprovalService} directly (a plain
 * {@code @Service} bean) but never touches approval's spi types.
 * <p>
 * Publishes both scheduling checkpoint keys so a chain admin can pick
 * either from the checkpoint-task dropdown at /approval/chain/form —
 * marking a schedule attended (see SchedulingService#markAttended) calls
 * ApprovalService#completeCheckpointByKey with the matching key to unlock
 * whichever chain step it's wired to.
 */
@Configuration
@RequiredArgsConstructor
public class SchedulingApprovalBridge implements CheckpointActionProvider {

    private final SchedulingService schedulingService;

    @Bean
    public CheckpointTaskType screeningScheduleCheckpoint() {
        return SimpleCheckpointTaskType.of(ScheduleType.SCREENING.getCheckpointKey(), "Screening attended");
    }

    @Bean
    public CheckpointTaskType badgeRetrievalScheduleCheckpoint() {
        return SimpleCheckpointTaskType.of(ScheduleType.BADGE_RETRIEVAL.getCheckpointKey(), "Badge retrieval attended");
    }

    /**
     * The scheduling form (create, or reschedule/attend/no-show/cancel an
     * existing one) rendered straight onto the approval detail page.
     *
     * <p>Only SES_REQUEST is scheduled — {@code ses_request_schedule.request_id}
     * is a request id, so any other business type is not ours.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, String> summaryFor(String checkpointKey, String businessRefType, Long businessRefId) {
        ScheduleType type = typeOf(checkpointKey);
        if (!"SES_REQUEST".equals(businessRefType) || type == null) {
            return Collections.emptyMap();
        }
        return schedulingService.findCurrent(businessRefId, type)
                .filter(sc -> sc.getScheduledDate() != null)
                .map(sc -> {
                    Map<String, String> rows = new LinkedHashMap<String, String>();
                    rows.put(type.getLabel(), new SimpleDateFormat("dd-MM-yyyy HH:mm").format(sc.getScheduledDate()));
                    return rows;
                })
                .orElse(Collections.<String, String>emptyMap());
    }

    /** The schedule type a checkpoint key belongs to, or null when it is not ours. */
    private static ScheduleType typeOf(String checkpointKey) {
        for (ScheduleType t : ScheduleType.values()) {
            if (t.getCheckpointKey().equals(checkpointKey)) {
                return t;
            }
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CheckpointAction> actionFor(String checkpointKey, String businessRefType,
                                                Long businessRefId, String returnTo) {
        if (!"SES_REQUEST".equals(businessRefType)) {
            return Optional.empty();
        }
        ScheduleType type = typeOf(checkpointKey);
        if (type == null) {
            return Optional.empty();
        }
        SesRequestSchedule schedule = schedulingService.findCurrent(businessRefId, type).orElse(null);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("requestId", businessRefId);
        model.put("scheduleType", type);
        model.put("schedule", schedule);
        model.put("returnTo", returnTo);
        model.put("screeningWorkers", schedulingService.workersOf(businessRefId));
        model.put("clearedWorkers", schedulingService.clearedWorkers(businessRefId));
        return Optional.of(new CheckpointAction("scheduling/fragments/manage-inline", "form", model));
    }
}
