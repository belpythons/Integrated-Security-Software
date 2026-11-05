package com.badaklng.approval.spi;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Immutable (key, label) pair — the usual way to hand a
 * {@link CheckpointTaskType} bean to a business module without writing a
 * whole class per checkpoint:
 * <pre>
 *   {@literal @}Bean
 *   public CheckpointTaskType screeningScheduleCheckpoint() {
 *       return SimpleCheckpointTaskType.of("SCREENING_SCHEDULE", "Screening scheduled");
 *   }
 * </pre>
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
public class SimpleCheckpointTaskType implements CheckpointTaskType {
    private final String key;
    private final String label;
}
