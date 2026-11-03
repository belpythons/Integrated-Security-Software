package com.badaklng.ses.text;

import com.badaklng.platform.system.service.AppParameterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Calendar;

/**
 * Reads user-facing wording from {@code APP_PARAMETER} so a change of phrasing
 * does not need a rebuild.
 *
 * <p>This is a thin front for {@link AppParameterService}, which already
 * caches the whole table in memory and is already how {@code APP_LINK},
 * {@code MAIL_SERVER}, {@code TESTING_MODE} and the sticker base amount are
 * read. No new table, no new admin screen: the rows are editable at
 * {@code /admin/parameter} like every other parameter.
 *
 * <p><b>The fallback argument is not optional.</b> Every call site passes the
 * literal that was previously compiled in, so an un-seeded key renders exactly
 * what it rendered before rather than a blank. That is what lets the seed
 * script be deployed independently of the WAR, and what makes
 * "{@code APP_PARAMETER} was wiped" a cosmetic problem instead of blank
 * clauses on an official letter.
 *
 * <p><b>Cache caveat, inherited:</b> {@code AppParameterService} reloads only
 * on {@code save()}. Editing a row through {@code /admin/parameter} takes
 * effect immediately; a direct {@code UPDATE} in SQL needs a restart.
 *
 * <p>Templates do not need this class — {@code GlobalModelAttributes} already
 * injects {@code appParams} into every view (including ses-ext, which scans
 * {@code com.badaklng.platform}), so Thymeleaf uses
 * {@code ${appParams['KEY'] ?: 'fallback'}} directly.
 *
 * <p>Key convention: {@code SES_TXT_<AREA>_<SLUG>} where AREA is one of
 * LETTER (permit PDF), VERIFY (public QR page), MAIL, XLS, FORM, MODULE or
 * BRAND.
 */
@Service
@RequiredArgsConstructor
public class SesText {

    private final AppParameterService parameterService;

    /**
     * The configured wording for {@code key}, or {@code fallback} when the row
     * is missing or blank.
     *
     * <p><b>Trims.</b> An administrator typing into {@code /admin/parameter}
     * leaves trailing spaces, and a label with a stray space is a visible
     * defect everywhere it is rendered. The consequence: a value whose
     * leading or trailing whitespace is <em>meaningful</em> -- a separator
     * stored as {@code " s/d "} -- comes back as {@code "s/d"} and runs its
     * neighbours together. Put such spacing inside a {@link #format} template
     * ({@code "{0} s/d {1}"}) where it is interior, not at the ends.
     */
    public String get(String key, String fallback) {
        String value = parameterService.getValue(key, fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    /**
     * {@link #get} with {@code {0}}, {@code {1}}, ... replaced positionally.
     *
     * <p>Deliberately not {@code MessageFormat}: the stored text is business
     * prose typed by an administrator, and {@code MessageFormat} assigns
     * meaning to a lone apostrophe — "Pemohon'nya" would silently swallow the
     * following text. Plain token substitution has no such trap.
     *
     * <p>A null argument substitutes as an empty string, so a missing optional
     * value leaves a gap rather than printing "null" on a letter.
     */
    public String format(String key, String fallback, Object... args) {
        String text = get(key, fallback);
        if (args == null) {
            return text;
        }
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", args[i] == null ? "" : String.valueOf(args[i]));
        }
        return text;
    }

    /**
     * Current year, for copyright lines.
     *
     * <p>Not a parameter on purpose. The footer was hardcoded to "2026" in
     * three templates with three different separators; making it a row would
     * just move the staleness somewhere an administrator has to remember to
     * update every January.
     */
    public int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }
}
