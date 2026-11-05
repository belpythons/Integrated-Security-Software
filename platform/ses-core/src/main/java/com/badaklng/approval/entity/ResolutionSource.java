package com.badaklng.approval.entity;

/**
 * How a task assignee was resolved.
 * <ul>
 *   <li>{@code STATIC_POSITION} — fixed position, current holder taken from APP_POSITION_VIEW.</li>
 *   <li>{@code STATIC_USER} — fixed APP_USER targeted directly.</li>
 *   <li>{@code DYNAMIC_MANAGER} — REQUESTOR_MANAGER resolved to a manager position/holder.</li>
 *   <li>{@code REPORT_TO_FALLBACK} — REQUESTOR_MANAGER fell back to SES_USER_EXT.report_to_pos_id.</li>
 *   <li>{@code NO_POSITION_FALLBACK} — requestor has no position; used report_to directly.</li>
 *   <li>{@code MANAGER_SKIP} — requestor is already a manager / above one; step skipped (no assignee).</li>
 *   <li>{@code REPORT_TO_DIRECT} — REPORT_TO assignee type: SES_USER_EXT.report_to_pos_id targeted directly, no manager lookup.</li>
 * </ul>
 */
public enum ResolutionSource {
    STATIC_POSITION,
    STATIC_USER,
    DYNAMIC_MANAGER,
    REPORT_TO_FALLBACK,
    NO_POSITION_FALLBACK,
    MANAGER_SKIP,
    REPORT_TO_DIRECT
}
