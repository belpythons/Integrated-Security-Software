package com.badaklng.request.service;

import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.repository.SesRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and checks the token behind {@code /request/upload-docs}.
 *
 * <p>That page is the only thing in SES reachable <b>without logging in</b>.
 * It has to be: the documents belong to the contractor, who has no SES
 * account and never will. So this class holds the whole security story for
 * the feature, and it is worth being explicit about what that story is.</p>
 *
 * <h3>Why a 256-bit random, not a UUID</h3>
 * <p>The specification offered "UUID or 64-character hex". UUIDv4 carries 122
 * bits of entropy and — more to the point — {@code UUID.randomUUID()} is the
 * only Java UUID generator guaranteed to be cryptographic, while the phrase
 * "UUID" invites someone to later swap in a time- or name-based one. 32 bytes
 * from {@link SecureRandom} rendered as hex removes the ambiguity: there is no
 * weaker thing the next reader can mistake for the right thing.</p>
 *
 * <h3>Why the token is not consumed on open</h3>
 * <p>{@code upload_token_used} flips only when the documents are submitted.
 * Someone collecting six workers' medical certificates will open the link,
 * close it, and come back — burning the token on first GET would turn a normal
 * work pattern into a support ticket. The email says so in as many words.</p>
 *
 * <h3>Why every rejection reads the same</h3>
 * <p>{@link #resolve} returns an empty Optional for "no such token", "already
 * used", "expired" and "the request moved on", and the page says one thing for
 * all four. Telling a stranger <em>which</em> of those happened confirms that a
 * token existed, which is the one bit of information a guesser would actually
 * gain something from.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadTokenService {

    /**
     * 32 bytes = 256 bits = 64 hex characters, matching the reference email's
     * token length. The column is {@code VARCHAR2(100)}, so this can grow
     * without a migration; it must never shrink.
     */
    private static final int TOKEN_BYTES = 32;

    /** Seven days, as promised in the email body. */
    public static final int VALID_DAYS = 7;

    /**
     * One instance, seeded once by the JVM. {@code SecureRandom} is
     * thread-safe, and creating a fresh one per call is a known way to exhaust
     * the entropy pool on a headless Linux server.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * In-memory binding mapping token -> active HTTP Session ID.
     * Ensures strict 1-session access: once opened in one browser session,
     * it cannot be opened in another session or reopened after the session ends.
     */
    private final java.util.concurrent.ConcurrentMap<String, String> tokenSessionBinding =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final SesRequestRepository requestRepository;

    /**
     * Stamps a fresh token, a fresh seven-day window and {@code used = 'N'}
     * onto {@code request}, and returns the token.
     *
     * <p>Does not save — the caller is already inside a transaction that is
     * about to persist the request, and saving here would write the row twice.</p>
     *
     * <p>Re-issuing silently invalidates whatever token was there before,
     * because the column is overwritten. That is exactly what "Kirim Ulang
     * Tautan" should do: the old email must stop working, or a forwarded copy
     * outlives the reason it was replaced.</p>
     */
    public String issue(SesRequest request) {
        if (request.getUploadToken() != null) {
            String oldNorm = normalise(request.getUploadToken());
            if (oldNorm != null) {
                tokenSessionBinding.remove(oldNorm);
            }
        }
        String token = newToken();
        request.setUploadToken(token);
        request.setUploadTokenExpiry(daysFromNow(VALID_DAYS));
        request.setUploadTokenUsed("N");
        return token;
    }

    /**
     * Resolves the token without session check (for backward compatibility / internal checks).
     */
    @Transactional(readOnly = true)
    public Optional<SesRequest> resolve(String token) {
        return resolve(token, null);
    }

    /**
     * The request this token may currently upload documents for, bound to the caller's HTTP session.
     *
     * <p>Four conditions plus 1-session binding, all required:</p>
     * <ul>
     *   <li>Token exists and matches exactly in constant time</li>
     *   <li>Token is not marked used (used = 'N')</li>
     *   <li>Token is not expired (&lt;= 7 days)</li>
     *   <li>Request is in {@code WAITING_FOR_UPLOAD} status</li>
     *   <li>Token is bound to the first HTTP session that opened it; requests from other sessions are rejected</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Optional<SesRequest> resolve(String token, String httpSessionId) {
        String candidate = normalise(token);
        if (candidate == null) {
            return Optional.empty();
        }

        Optional<SesRequest> found = requestRepository.findByUploadToken(candidate);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        SesRequest request = found.get();

        // Belt and braces over the unique index: confirm the stored value
        // really equals what was presented. A length-independent comparison
        // keeps the lookup from leaking a prefix through timing if the index
        // is ever changed to something non-exact.
        if (!constantTimeEquals(candidate, request.getUploadToken())) {
            return Optional.empty();
        }
        if (isUsed(request)) {
            return Optional.empty();
        }
        if (isExpired(request, new Date())) {
            return Optional.empty();
        }
        if (!RequestStatus.WAITING_FOR_UPLOAD.equals(request.getStatus())) {
            return Optional.empty();
        }

        // 1-Session Lifecycle binding
        if (httpSessionId != null && !httpSessionId.trim().isEmpty()) {
            String bound = tokenSessionBinding.putIfAbsent(candidate, httpSessionId.trim());
            if (bound != null && !bound.equals(httpSessionId.trim())) {
                log.warn("Upload token {} access rejected: already bound to session {} but accessed from new session {}",
                        candidate.substring(0, 8), bound, httpSessionId);
                return Optional.empty();
            }
        }

        return Optional.of(request);
    }

    /** Burns the token. Called once the documents are in and the chain is about to start. */
    public void markUsed(SesRequest request) {
        request.setUploadTokenUsed("Y");
        if (request.getUploadToken() != null) {
            String norm = normalise(request.getUploadToken());
            if (norm != null) {
                tokenSessionBinding.remove(norm);
            }
        }
    }

    /**
     * Does this request already hold a link that still works?
     *
     * <p>Exists so {@code RequestService.saveAndSubmit} can stop re-issuing on
     * every submit. It used to call {@link #issue} unconditionally in the
     * external-upload branch, which cost two things at once:</p>
     * <ul>
     *   <li><b>A duplicate email.</b> {@code WAITING_FOR_UPLOAD} is listed in
     *       {@code RequestStatus.isRequestorEditable}, so a requestor who
     *       re-submits (or double-clicks Submit) runs the branch again and
     *       queues a second mail. The {@code RESUBMIT_COOLDOWN_MILLIS} guard
     *       does not catch it: that guard is keyed on
     *       {@code approvalInstanceId != null}, and a request waiting for
     *       documents has no instance yet.</li>
     *   <li><b>A dead link in the contractor's inbox.</b> Worse than the
     *       duplicate. Re-issuing overwrites the column, so the mail the
     *       contractor is already working from stops working — and the one
     *       replacing it was sent to the same address for the same reason,
     *       which gives nobody a clue why.</li>
     * </ul>
     *
     * <p>Static and dependency-free, like the rules above, and expressed as
     * the reuse question rather than its negation so the call site reads as
     * the decision it is making. {@code isExpired} still fails closed on a
     * null window, so a hand-planted token with no expiry is re-issued rather
     * than trusted.</p>
     */
    static boolean hasLiveToken(SesRequest request, Date now) {
        return request.getUploadToken() != null
                && !request.getUploadToken().trim().isEmpty()
                && !isUsed(request)
                && !isExpired(request, now);
    }

    /** Convenience overload — {@link #hasLiveToken(SesRequest, Date)} against the wall clock. */
    public boolean hasLiveToken(SesRequest request) {
        return hasLiveToken(request, new Date());
    }

    // ---------------------------------------------------------------
    // Pure helpers — static and dependency-free so the rules above can be
    // unit-tested without Spring or Oracle, the same treatment
    // ApprovalResolverService.decide gets.
    // ---------------------------------------------------------------

    /** Null, blank, or anything that is not 64 lowercase hex characters is not a token. */
    static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toLowerCase();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        if (t.length() != TOKEN_BYTES * 2) {
            return null;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return null;
            }
        }
        return t;
    }

    /**
     * A row with no {@code used} value at all is treated as unused.
     *
     * <p>Every row written since sql/61 carries {@code 'N'} from the column
     * default, so this only covers a token planted by hand. "Unused" is the
     * permissive reading, but the expiry and status checks still have to pass,
     * so it cannot open anything on its own.</p>
     */
    static boolean isUsed(SesRequest request) {
        return "Y".equalsIgnoreCase(request.getUploadTokenUsed());
    }

    /**
     * A token with no expiry date is treated as EXPIRED.
     *
     * <p>The opposite of the {@code used} default, and deliberately so: a
     * missing expiry means nobody ever set a window, and "no window" must not
     * read as "no limit" for the one credential that bypasses login.</p>
     */
    static boolean isExpired(SesRequest request, Date now) {
        Date expiry = request.getUploadTokenExpiry();
        return expiry == null || expiry.before(now);
    }

    /**
     * Compares without short-circuiting on the first differing character.
     *
     * <p>{@code String.equals} returns as soon as it finds a mismatch, so the
     * time it takes is a (very noisy) function of how long a shared prefix is.
     * Over a LAN that is not a practical attack against 256 bits of entropy —
     * but this comparison is on the authentication path for an unauthenticated
     * page, and writing the careful version costs four lines.</p>
     */
    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    static Date daysFromNow(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
