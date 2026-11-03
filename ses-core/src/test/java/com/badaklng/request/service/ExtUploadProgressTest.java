package com.badaklng.request.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upload page's completeness indicator, and the rule that decides whether
 * a stored document may be deleted.
 *
 * <p>Plain JUnit; both are static/pure over the DTOs for that reason. Neither
 * looks like it needs a test, and both encode a decision that is wrong in a
 * way nobody would notice from the code:</p>
 * <ul>
 *   <li>Counting optional documents in the progress bar leaves a ready
 *       submission reading as incomplete.</li>
 *   <li>Offering Delete on a carried-forward renewal document lets a
 *       contractor remove a file belonging to a different, already-approved
 *       request.</li>
 * </ul>
 */
class ExtUploadProgressTest {

    private static RequestService.WorkerDocDto staged(String code, boolean mandatory, long extDocId) {
        return new RequestService.WorkerDocDto(code, code, mandatory, "file-" + code,
                extDocId, code + ".pdf", 1024L);
    }

    private static RequestService.WorkerDocDto carriedForward(String code, boolean mandatory) {
        // A renewal's document: a fileId is present, but no staging row, so no
        // extDocId. This is the shape that must NOT be deletable.
        return new RequestService.WorkerDocDto(code, code, mandatory, "file-" + code);
    }

    private static RequestService.WorkerDocDto missing(String code, boolean mandatory) {
        return new RequestService.WorkerDocDto(code, code, mandatory, null);
    }

    private static RequestService.WorkerRowDto worker(RequestService.WorkerDocDto... docs) {
        return new RequestService.WorkerRowDto(1L, 1, "BUDI", "1234567890123456", "NIK",
                null, null, null, null, "M", Arrays.asList(docs));
    }

    // ---------------------------------------------------------------
    // Progress
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Optional documents are excluded from the completeness count")
    void optionalDocsDoNotHoldTheBarBack() {
        // THE assertion in this class. Both mandatory documents are in and
        // nothing is outstanding, so the bar must read 100% even though an
        // optional slot is empty. Including optionals here is how a submission
        // that is ready to send looks unfinished to the person sending it.
        RequestService.MandatoryProgress p = RequestService.mandatoryProgress(Collections.singletonList(
                worker(staged("MEDICAL", true, 1L),
                       staged("SAFETY", true, 2L),
                       missing("EXTRA", false))));

        assertEquals(2, p.getTotal(), "the optional slot must not be counted");
        assertEquals(2, p.getDone());
        assertEquals(100, p.getPercent());
        assertTrue(p.isComplete());
    }

    @Test
    @DisplayName("A type demanding no documents reports 100%, not 0%")
    void noRequirementsIsComplete() {
        // Nothing is outstanding. An empty bar would say the opposite, and
        // total==0 is also the division that would throw.
        RequestService.MandatoryProgress p =
                RequestService.mandatoryProgress(Collections.singletonList(worker()));

        assertEquals(0, p.getTotal());
        assertEquals(100, p.getPercent());
        assertTrue(p.isComplete());
    }

    @Test
    @DisplayName("Partial completion rounds down, so 100% means genuinely finished")
    void partialRoundsDown() {
        // 2 of 3 is 66.67%. Rounding UP anywhere here would let 100% appear
        // while a mandatory document is still missing — the integer division
        // is load-bearing, not sloppy.
        RequestService.MandatoryProgress p = RequestService.mandatoryProgress(Collections.singletonList(
                worker(staged("A", true, 1L), staged("B", true, 2L), missing("C", true))));

        assertEquals(66, p.getPercent());
        assertFalse(p.isComplete());
    }

    @Test
    @DisplayName("Carried-forward documents count as present")
    void renewalDocsCountTowardsProgress() {
        // A renewal's document is already on the request; the contractor has
        // nothing to do for it. Counting it as missing would demand papers an
        // earlier submission already proved.
        RequestService.MandatoryProgress p = RequestService.mandatoryProgress(Collections.singletonList(
                worker(carriedForward("MEDICAL", true), missing("SAFETY", true))));

        assertEquals(1, p.getDone());
        assertEquals(50, p.getPercent());
    }

    @Test
    @DisplayName("Every worker's documents are counted, not just the first")
    void allWorkersAreCounted() {
        List<RequestService.WorkerRowDto> workers = Arrays.asList(
                worker(staged("A", true, 1L)),
                worker(missing("A", true)),
                worker(missing("A", true)));
        RequestService.MandatoryProgress p = RequestService.mandatoryProgress(workers);

        assertEquals(3, p.getTotal());
        assertEquals(1, p.getDone());
        assertEquals(33, p.getPercent());
    }

    @Test
    @DisplayName("Null and empty inputs do not throw")
    void degeneratesSafely() {
        assertEquals(100, RequestService.mandatoryProgress(null).getPercent());
        assertEquals(100, RequestService.mandatoryProgress(
                Collections.<RequestService.WorkerRowDto>emptyList()).getPercent());
    }

    // ---------------------------------------------------------------
    // Deletability
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Only a document staged in THIS session is deletable")
    void onlyStagedDocsAreDeletable() {
        assertTrue(staged("MEDICAL", true, 42L).isDeletable(),
                "the contractor must be able to take back what they just sent");

        assertFalse(carriedForward("MEDICAL", true).isDeletable(),
                "a renewal shares its app_file row with the previous approved request; deleting it"
                        + " here would reach into that request's documents");

        assertFalse(missing("MEDICAL", true).isDeletable(),
                "there is nothing to delete");
    }

    @Test
    @DisplayName("The legacy 4-argument shape leaves the staging fields empty")
    void legacyConstructorIsStagingFree() {
        // workerRows() (the requestor's own form) still builds DTOs this way.
        // If this overload ever started inventing an extDocId, the requestor's
        // page would grow Delete buttons wired to rows that do not exist.
        RequestService.WorkerDocDto d = new RequestService.WorkerDocDto("A", "Doc A", true, "f1");

        assertEquals("f1", d.getFileId());
        assertEquals(null, d.getExtDocId());
        assertEquals(null, d.getFileName());
        assertEquals(null, d.getFileSize());
        assertFalse(d.isDeletable());
    }
}
