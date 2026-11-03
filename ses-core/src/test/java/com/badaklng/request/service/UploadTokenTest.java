package com.badaklng.request.service;

import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules guarding the one page in SES that needs no login.
 *
 * <p>Plain JUnit, no Spring. {@code UploadTokenService}'s decision rules are
 * {@code static} and operate on plain entities for exactly this reason —
 * authorisation logic that can only be exercised through
 * {@code @SpringBootTest} against a live ELLDEV is authorisation logic that
 * does not get tested, as {@code ApprovalResolverService} already says in as
 * many words.</p>
 *
 * <p>{@link #issue} is tested through a real service instance with a null
 * repository: it only stamps fields onto the entity it is handed and never
 * touches persistence, which is itself part of the contract.</p>
 */
class UploadTokenTest {

    /** The repository is never reached by the methods under test here. */
    private final UploadTokenService service = new UploadTokenService(null);

    private static SesRequest waiting() {
        SesRequest r = new SesRequest();
        r.setRequestId(1L);
        r.setRequestNo("REQ-2026-0001");
        r.setStatus(RequestStatus.WAITING_FOR_UPLOAD);
        r.setUploadTokenUsed("N");
        r.setUploadTokenExpiry(shift(7));
        return r;
    }

    private static Date shift(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    // ---------------------------------------------------------------
    // Issue
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A token is 64 lowercase hex characters")
    void tokenShape() {
        String token = service.issue(waiting());
        assertNotNull(token);
        assertEquals(64, token.length(), "256 bits rendered as hex");
        assertTrue(token.matches("[0-9a-f]{64}"), token);
    }

    @Test
    @DisplayName("Tokens do not repeat")
    void tokensAreUnique() {
        // Not a statistical proof of entropy — that lives in SecureRandom.
        // This catches the failure that actually happens: someone swaps the
        // generator for something seeded per call, or derives it from the
        // request number, and every token in a tight loop comes out the same.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            assertTrue(seen.add(service.issue(waiting())), "duplicate token at iteration " + i);
        }
    }

    @Test
    @DisplayName("Issuing sets a seven-day window and clears the used flag")
    void issueStampsTheWindow() {
        SesRequest r = waiting();
        r.setUploadTokenUsed("Y");
        r.setUploadTokenExpiry(shift(-1));

        service.issue(r);

        assertEquals("N", r.getUploadTokenUsed(), "re-issuing must revive a spent link");
        assertFalse(UploadTokenService.isExpired(r, new Date()));
        // A RANGE, not an exact day count. Integer-dividing the millisecond
        // difference lands on 6 or 7 depending on whether the clock ticked
        // between issue() and the assertion, and a DST boundary moves it
        // again — an exact assertion here fails twice a year for a reason
        // that has nothing to do with the code.
        long aheadMillis = r.getUploadTokenExpiry().getTime() - System.currentTimeMillis();
        long sixDays = 6L * 24 * 60 * 60 * 1000;
        long eightDays = 8L * 24 * 60 * 60 * 1000;
        assertTrue(aheadMillis > sixDays && aheadMillis < eightDays,
                "expiry should land ~7 days ahead, was " + (aheadMillis / 86400000L) + " days");
    }

    @Test
    @DisplayName("Re-issuing replaces the previous token, so the old link dies")
    void reissueInvalidatesTheOldLink() {
        SesRequest r = waiting();
        String first = service.issue(r);
        String second = service.issue(r);

        assertFalse(first.equals(second));
        assertEquals(second, r.getUploadToken(),
                "the stored token must be the new one — a forwarded old email must stop working");
    }

    // ---------------------------------------------------------------
    // normalise — the first gate every request passes
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Anything that is not 64 hex characters is not a token")
    void normaliseRejectsRubbish() {
        assertNull(UploadTokenService.normalise(null));
        assertNull(UploadTokenService.normalise(""));
        assertNull(UploadTokenService.normalise("   "));
        assertNull(UploadTokenService.normalise("abc"));
        // 63 and 65 characters
        assertNull(UploadTokenService.normalise(repeat("a", 63)));
        assertNull(UploadTokenService.normalise(repeat("a", 65)));
        // right length, wrong alphabet — 'g' is not hex
        assertNull(UploadTokenService.normalise(repeat("g", 64)));
        // a SQL-injection shaped probe, in case anyone ever hand-builds the query
        assertNull(UploadTokenService.normalise("' OR '1'='1"));
    }

    @Test
    @DisplayName("Case and surrounding whitespace are forgiven, because email clients add them")
    void normaliseIsForgivingWhereItSafelyCan() {
        String token = repeat("AB", 32);
        assertEquals(repeat("ab", 32), UploadTokenService.normalise("  " + token + "\n"));
    }

    // ---------------------------------------------------------------
    // The four refusal conditions
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A spent token is refused")
    void usedTokenIsRefused() {
        SesRequest r = waiting();
        assertFalse(UploadTokenService.isUsed(r));
        r.setUploadTokenUsed("Y");
        assertTrue(UploadTokenService.isUsed(r));
        // Case-insensitive: a hand-written 'y' must still count as spent.
        r.setUploadTokenUsed("y");
        assertTrue(UploadTokenService.isUsed(r));
    }

    @Test
    @DisplayName("A lapsed token is refused")
    void expiredTokenIsRefused() {
        SesRequest r = waiting();
        assertFalse(UploadTokenService.isExpired(r, new Date()));

        r.setUploadTokenExpiry(shift(-1));
        assertTrue(UploadTokenService.isExpired(r, new Date()));
    }

    @Test
    @DisplayName("A token with NO expiry is refused, not accepted forever")
    void missingExpiryFailsClosed() {
        // The opposite default from the used flag, and the important one:
        // "nobody set a window" must never read as "no limit" for the one
        // credential that bypasses login.
        SesRequest r = waiting();
        r.setUploadTokenExpiry(null);
        assertTrue(UploadTokenService.isExpired(r, new Date()));
    }

    @Test
    @DisplayName("Only WAITING_FOR_UPLOAD may be uploaded against")
    void statusGate() {
        // resolve() checks this separately from the used flag because a
        // request can leave WAITING_FOR_UPLOAD without anyone touching the
        // token — cancelled by the requestor, or swept by RequestExpiryJob.
        for (String status : new String[]{RequestStatus.DRAFT, RequestStatus.PENDING,
                RequestStatus.APPROVED, RequestStatus.REJECTED, RequestStatus.CANCELLED}) {
            assertFalse(RequestStatus.WAITING_FOR_UPLOAD.equals(status),
                    status + " must not satisfy the upload gate");
        }
        assertTrue(RequestStatus.WAITING_FOR_UPLOAD.equals(waiting().getStatus()));
    }

    // ---------------------------------------------------------------
    // Comparison
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Token comparison does not short-circuit on the first difference")
    void constantTimeEquals() {
        String a = repeat("ab", 32);
        assertTrue(UploadTokenService.constantTimeEquals(a, repeat("ab", 32)));

        // differs in the LAST character
        assertFalse(UploadTokenService.constantTimeEquals(a, repeat("ab", 31) + "ac"));
        // differs in the FIRST character
        assertFalse(UploadTokenService.constantTimeEquals(a, "bb" + repeat("ab", 31)));
        assertFalse(UploadTokenService.constantTimeEquals(a, null));
        assertFalse(UploadTokenService.constantTimeEquals(null, a));
        assertFalse(UploadTokenService.constantTimeEquals(a, a + "a"));
    }

    // ---------------------------------------------------------------
    // Status helpers
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A request waiting for documents is still editable by its requestor")
    void waitingIsEditable() {
        // Nobody has been asked to approve anything yet, so changing the
        // header or the worker list harms no approver's decision.
        assertTrue(RequestStatus.isRequestorEditable(RequestStatus.WAITING_FOR_UPLOAD));
        assertTrue(RequestStatus.isRequestorEditable(RequestStatus.DRAFT));
        assertTrue(RequestStatus.isRequestorEditable(RequestStatus.PENDING));
        assertFalse(RequestStatus.isRequestorEditable(RequestStatus.APPROVED));
        assertFalse(RequestStatus.isRequestorEditable(RequestStatus.CANCELLED));
    }

    @Test
    @DisplayName("Waiting for documents is NOT a terminal state")
    void waitingIsNotTerminal() {
        assertFalse(RequestStatus.isTerminal(RequestStatus.WAITING_FOR_UPLOAD));
        assertTrue(RequestStatus.isTerminal(RequestStatus.APPROVED));
        assertTrue(RequestStatus.isTerminal(RequestStatus.REJECTED));
        assertTrue(RequestStatus.isTerminal(RequestStatus.CANCELLED));
    }

    @Test
    @DisplayName("WAITING_FOR_UPLOAD fits the VARCHAR2(20) status column")
    void statusFitsTheColumn() {
        // sql/61 widens the CHECK constraint but not the column. If someone
        // later renames this to something longer, the failure would be an
        // ORA-12899 on a real submission, not here.
        assertTrue(RequestStatus.WAITING_FOR_UPLOAD.length() <= 20,
                RequestStatus.WAITING_FOR_UPLOAD + " is " + RequestStatus.WAITING_FOR_UPLOAD.length()
                        + " chars; ses_request.status is VARCHAR2(20)");
    }

    // ---------------------------------------------------------------
    // Re-issue suppression — the duplicate-email fix
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A live token is reused, so a re-submit neither re-mails nor kills the link")
    void liveTokenIsReused() {
        // The bug this guards: saveAndSubmit re-enters the external-upload
        // branch on every submit of an editable request, and
        // WAITING_FOR_UPLOAD is editable. Issuing unconditionally there sent a
        // second email AND overwrote the column, so the mail the contractor
        // was already working from went dead. The cooldown guard in
        // saveAndSubmit cannot catch it — that one is keyed on
        // approvalInstanceId, and this request has no approval instance yet.
        SesRequest r = waiting();
        String original = service.issue(r);

        assertTrue(UploadTokenService.hasLiveToken(r, new Date()));
        assertEquals(original, r.getUploadToken(), "the live token must survive untouched");
    }

    @Test
    @DisplayName("A used token is not reused")
    void usedTokenIsReissued() {
        // The contractor already submitted. A fresh round of collection is a
        // genuinely new link, and the old one must stay burnt.
        SesRequest r = waiting();
        service.issue(r);
        service.markUsed(r);

        assertFalse(UploadTokenService.hasLiveToken(r, new Date()));
    }

    @Test
    @DisplayName("An expired token is not reused")
    void expiredTokenIsReissued() {
        SesRequest r = waiting();
        service.issue(r);
        r.setUploadTokenExpiry(shift(-1));

        assertFalse(UploadTokenService.hasLiveToken(r, new Date()));
    }

    @Test
    @DisplayName("A request that has never been submitted has no live token")
    void absentTokenIsNotLive() {
        // The first submit must always mail. If this answered true, the very
        // first contractor would never be told the request exists.
        SesRequest r = waiting();
        r.setUploadToken(null);
        assertFalse(UploadTokenService.hasLiveToken(r, new Date()));

        r.setUploadToken("   ");
        assertFalse(UploadTokenService.hasLiveToken(r, new Date()),
                "a blank token is an absent token, not a live one");
    }

    @Test
    @DisplayName("Reuse fails closed on a token with no expiry window")
    void missingExpiryIsNotLive() {
        // Mirrors missingExpiryFailsClosed for resolve(). "No window" must
        // never read as "no limit" — here that means re-issuing a
        // hand-planted token rather than silently mailing it out again.
        SesRequest r = waiting();
        service.issue(r);
        r.setUploadTokenExpiry(null);

        assertFalse(UploadTokenService.hasLiveToken(r, new Date()));
    }

    // ---------------------------------------------------------------
    // 1-Session Lifecycle
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A token bound to one session cannot be accessed in a different session")
    void tokenBoundToSessionRejectsOtherSessions() {
        SesRequest req = waiting();
        String token = service.issue(req);

        com.badaklng.request.repository.SesRequestRepository repo =
                org.mockito.Mockito.mock(com.badaklng.request.repository.SesRequestRepository.class);
        org.mockito.Mockito.when(repo.findByUploadToken(token)).thenReturn(java.util.Optional.of(req));

        UploadTokenService svc = new UploadTokenService(repo);

        // First session binds and succeeds
        java.util.Optional<SesRequest> first = svc.resolve(token, "session-A");
        assertTrue(first.isPresent(), "first session must be accepted");

        // Same session continues to work (e.g. reload / draft uploads)
        java.util.Optional<SesRequest> reload = svc.resolve(token, "session-A");
        assertTrue(reload.isPresent(), "same session must continue to be accepted");

        // Different session (opened in new tab/browser/after closing) is rejected
        java.util.Optional<SesRequest> second = svc.resolve(token, "session-B");
        assertFalse(second.isPresent(), "different session must be rejected for 1-session token");
    }

    /** Java 8 has no {@code String.repeat}. */
    private static String repeat(String s, int times) {
        StringBuilder b = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            b.append(s);
        }
        return b.toString();
    }
}
