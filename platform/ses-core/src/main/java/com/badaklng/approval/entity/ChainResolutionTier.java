package com.badaklng.approval.entity;

/**
 * Which tier of SES_FORM_APPROVAL_MAP resolved a chain for an approval
 * instance.
 * <ul>
 *   <li>{@code OVERRIDE} — a mapping row with a matching non-null match_key.</li>
 *   <li>{@code DEFAULT} — the form's default mapping row (match_key IS NULL).</li>
 * </ul>
 */
public enum ChainResolutionTier {
    OVERRIDE,
    DEFAULT
}
