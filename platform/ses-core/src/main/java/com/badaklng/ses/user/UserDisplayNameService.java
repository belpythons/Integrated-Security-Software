package com.badaklng.ses.user;

import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a stored user reference into the name a human recognises.
 *
 * <p>Extracted from {@code ApprovalMailService.displayName}, which was the
 * only place in the app that got this right. Everywhere else rendered the
 * stored reference verbatim, so the same person appeared as "SITI AMINAH" in
 * a notification email and as "saminah" on the page that email linked to.</p>
 *
 * <p><b>Two identifiers, deliberately both accepted.</b> The columns
 * disagree: {@code SesApprovalInstance.requestorUserId} and
 * {@code SesRequest.requestorUserId} hold the <em>username</em> despite their
 * names (see {@code SesRequest}'s javadoc), while
 * {@code SesApprovalHistory.actorUserId} and {@code SesApprovalTask.completedBy}
 * hold the {@code APP_USER.USER_ID} UUID. Resolving only by username once put
 * a raw "00000000-0000-0000-0000-000000000001" in front of a requestor as the
 * person who had rejected their submission.</p>
 *
 * <p>Never returns null or an empty string for a non-blank reference: an
 * unresolvable reference comes back as itself. A name is context, and a blank
 * cell tells the reader less than a raw username does.</p>
 */
@Service
@RequiredArgsConstructor
public class UserDisplayNameService {

    private final AppUserRepository appUserRepository;

    /** Real name for one reference — username or user_id — or the reference itself. */
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "userDisplayNames", key = "#userRef", unless = "#result == null")
    public String displayName(String userRef) {
        if (isBlank(userRef)) {
            return null;
        }
        String ref = userRef.trim();
        AppUser user = appUserRepository.findByUsername(ref)
                .orElseGet(() -> appUserRepository.findById(ref).orElse(null));
        return nameOf(user, ref);
    }

    /**
     * Same resolution for a whole listing, in ONE query.
     *
     * <p>A worklist or a history page holds hundreds of rows over a handful of
     * distinct people; calling {@link #displayName} per row would issue one or
     * two selects per row for an answer that repeats. Callers look the result
     * up with {@code getOrDefault(ref, ref)}.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, String> displayNames(Collection<String> userRefs) {
        if (userRefs == null || userRefs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> byRef = nameIndex();

        Map<String, String> resolved = new HashMap<>();
        for (String ref : userRefs) {
            if (isBlank(ref)) {
                continue;
            }
            String key = ref.trim();
            resolved.put(ref, byRef.getOrDefault(key, key));
        }
        return resolved;
    }

    private static final long NAME_INDEX_TTL_MS = 5 * 60_000L;
    private volatile Map<String, String> cachedNameIndex;
    private volatile long cachedNameIndexExpiresAt;

    // ponytail: full APP_USER read per 5 min, staleness bounded by TTL; swap for per-ref lookup if the table outgrows memory.
    private Map<String, String> nameIndex() {
        long now = System.currentTimeMillis();
        Map<String, String> cached = cachedNameIndex;
        if (cached != null && cachedNameIndexExpiresAt > now) {
            return cached;
        }
        Map<String, String> byRef = new HashMap<>();
        for (AppUser u : appUserRepository.findAll()) {
            String name = nameOf(u, null);
            if (name == null) {
                continue;
            }
            if (!isBlank(u.getUsername())) {
                byRef.put(u.getUsername().trim(), name);
            }
            if (!isBlank(u.getUserId())) {
                byRef.put(u.getUserId().trim(), name);
            }
        }
        cachedNameIndex = byRef;
        cachedNameIndexExpiresAt = now + NAME_INDEX_TTL_MS;
        return byRef;
    }

    private static String nameOf(AppUser user, String fallback) {
        if (user == null) {
            return fallback;
        }
        if (!isBlank(user.getFullName())) {
            return user.getFullName().trim();
        }
        return !isBlank(user.getUsername()) ? user.getUsername().trim() : fallback;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
