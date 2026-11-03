package com.badaklng.approval.entity;

/**
 * Type of assignee on an approval step.
 * <ul>
 *   <li>{@code POSITION} — a fixed position in APP_POSITION_VIEW; the current holder resolves at approval time.</li>
 *   <li>{@code USER} — a fixed APP_USER (for external/local users without a position).</li>
 *   <li>{@code REQUESTOR_MANAGER} — dynamic: resolve the requestor's direct manager at approval time.</li>
 *   <li>{@code REPORT_TO} — dynamic: target SES_USER_EXT.report_to_pos_id directly, no manager lookup.</li>
 * </ul>
 */
public enum AssigneeType {
    POSITION,
    USER,
    REQUESTOR_MANAGER,
    REPORT_TO
}
