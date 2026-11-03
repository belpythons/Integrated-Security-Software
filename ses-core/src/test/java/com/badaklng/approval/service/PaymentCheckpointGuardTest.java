package com.badaklng.approval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The payment gate: a request type that needs paying must not be able to start
 * an approval whose chain has nowhere to verify the payment.
 *
 * <p><b>What was actually wrong.</b> {@code needs_payment_flag} had no
 * influence on approval at all. Chain selection is
 * {@code SES_FORM_APPROVAL_MAP} keyed on the type code, and that mapping is
 * (deliberately) unseeded for an admin to wire by hand. Map a paying type to
 * {@code BADGE_DEFAULT} — which carries only the screening and badge
 * checkpoints — and the request is approved and scheduled with the proof of
 * payment unread. Nothing complains, because
 * {@code PaymentService.approve} → {@code completeCheckpointByKey} finds no
 * matching checkpoint and WARN-no-ops.</p>
 *
 * <p>These tests cover the parts that are provable without Oracle: that the
 * flag reaches {@code TriggerOptions} and defaults OFF for every existing
 * caller, and that the migration which repairs the data is shaped the way its
 * own header claims. The refusal itself needs a live chain and is listed as
 * unverified in the stage document.</p>
 */
class PaymentCheckpointGuardTest {

    // ---------------------------------------------------------------
    // TriggerOptions
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Existing three-argument callers are unchanged: the gate is OFF")
    void defaultsOff() {
        // Every pre-existing overload funnels into the 3-arg constructor. If
        // that started defaulting to true, every non-paying request type in
        // the system would refuse to submit the moment this shipped — a
        // guard that blocks everything is worse than the hole it closes.
        ApprovalService.TriggerOptions opts = new ApprovalService.TriggerOptions(
                true, true, Collections.<ApprovalService.EvaluatorInput>emptyList());

        assertFalse(opts.isRequirePaymentCheckpoint());
        assertFalse(ApprovalService.TriggerOptions.defaults().isRequirePaymentCheckpoint());
    }

    @Test
    @DisplayName("The four-argument form carries the flag through")
    void fourArgCarriesTheFlag() {
        ApprovalService.TriggerOptions opts = new ApprovalService.TriggerOptions(
                false, false, Collections.<ApprovalService.EvaluatorInput>emptyList(), true);

        assertTrue(opts.isRequirePaymentCheckpoint());
        // The other three must not be disturbed by the new parameter's arrival.
        assertFalse(opts.isRequireRequestorManagerStep());
        assertFalse(opts.isRequireProcurementManagerStep());
        assertTrue(opts.getEvaluators().isEmpty());
    }

    @Test
    @DisplayName("A null evaluator list still degrades to empty, not NPE")
    void nullEvaluatorsStillSafe() {
        // Pre-existing behaviour; the added constructor must not lose it.
        assertTrue(new ApprovalService.TriggerOptions(true, true, null, true)
                .getEvaluators().isEmpty());
    }

    // ---------------------------------------------------------------
    // The request module passes it
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RequestService derives the flag from needs_payment_flag")
    void requestServicePassesTheFlag() throws Exception {
        // A source read, because the alternative is a @SpringBootTest with a
        // live paying type and a live chain. What matters is that the wiring
        // exists at all: without it the guard is inert and silently so, which
        // is the exact failure mode it was written to end.
        String src = read(Paths.get("src", "main", "java", "com", "badaklng", "request",
                "service", "RequestService.java"));

        assertTrue(src.contains("requirePaymentCheckpoint = \"Y\".equals(type.getNeedsPaymentFlag())"),
                "needs_payment_flag must be read into the trigger options");
        assertTrue(src.contains("requirePaymentCheckpoint))"),
                "and it must actually be passed to TriggerOptions");
    }

    @Test
    @DisplayName("The guard runs before anything is persisted")
    void guardRefusesBeforePersisting() throws Exception {
        // The whole value of refusing rather than auto-injecting is that no
        // half-made instance is left behind. ApprovalService.trigger creates
        // the instance after its pre-flight guards; this asserts the payment
        // guard sits with them and not after.
        String src = read(Paths.get("src", "main", "java", "com", "badaklng", "approval",
                "service", "ApprovalService.java"));

        int guard = src.indexOf("opts.isRequirePaymentCheckpoint()");
        int create = src.indexOf("SesApprovalInstance instance = new SesApprovalInstance();");

        assertTrue(guard > 0, "the payment guard must exist");
        assertTrue(create > 0, "sanity: trigger() still creates an instance");
        assertTrue(guard < create,
                "the payment guard must run BEFORE the instance is created, or a refused submission"
                        + " leaves a half-built approval behind");
    }

    @Test
    @DisplayName("The refusal names the type, the chain, and where to fix it")
    void refusalIsActionable() throws Exception {
        // An admin reading this message in a flash alert has to know which of
        // the two screens to open. "Configuration error" would not.
        String src = read(Paths.get("src", "main", "java", "com", "badaklng", "approval",
                "service", "ApprovalService.java"));

        assertTrue(src.contains("tidak memuat checkpoint"));
        assertTrue(src.contains("/approval/map"), "the mapping screen must be named");
        assertTrue(src.contains("/approval/chain"), "the chain editor must be named too");
    }

    // ---------------------------------------------------------------
    // The data repair
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The migration discovers the paying chain instead of hard-coding it")
    void migrationDiscoversTheChain() throws Exception {
        // These chain keys have already been renamed once — DEFAULT/PAYMENT ->
        // BADGE_DEFAULT/BADGE_PAYMENT in sql/63 — and chain_id 2 is an
        // accident of insert order. The property that actually matters is
        // "carries a PAYMENT_VERIFICATION step task", so that is what the
        // script looks for.
        String sql = read(resolveMigration("66_paid_types_to_payment_chain.sql"));

        assertTrue(sql.contains("t.checkpoint_key = 'PAYMENT_VERIFICATION'"),
                "the target chain must be found by its checkpoint, not by name or id");
        assertFalse(sql.contains("chain_id = 2"), "chain_id 2 is an accident of insert order");
        assertFalse(sql.contains("chain_key = 'BADGE_PAYMENT'"),
                "chain keys have been renamed once already; do not depend on the current spelling");
    }

    @Test
    @DisplayName("The migration leaves non-paying types alone")
    void migrationTouchesOnlyPayingTypes() throws Exception {
        // The mirror error — a non-paying type on a paying chain — freezes a
        // PAYMENT_VERIFICATION checkpoint with no payment row behind it, which
        // can then only be cleared by a manual Mark Done / Skip. That is an
        // administrative decision, not something a migration may guess.
        String sql = read(resolveMigration("66_paid_types_to_payment_chain.sql"));

        assertTrue(sql.contains("needs_payment_flag = 'Y'"));
        assertFalse(sql.contains("needs_payment_flag = 'N'"),
                "the migration must not re-route non-paying types");
    }

    @Test
    @DisplayName("The migration stops cleanly when no paying chain exists")
    void migrationFailsSafe() throws Exception {
        // Inventing a chain here would be guessing an organisation's approval
        // flow. Saying so and changing nothing is the only honest option.
        String sql = read(resolveMigration("66_paid_types_to_payment_chain.sql"));

        assertTrue(sql.contains("Tidak ada perubahan yang dilakukan"));
        assertTrue(sql.contains("RETURN;"));
    }

    @Test
    @DisplayName("The production mirror is byte-identical")
    void productionMirrorMatches() throws Exception {
        Path dev = resolveMigration("66_paid_types_to_payment_chain.sql");
        Path prod = resolveProductionMigration("66_paid_types_to_payment_chain.sql");

        assertTrue(Files.exists(prod), "sql/production/66_... is missing");
        assertEquals(read(dev), read(prod), "sql/production/66_... has drifted from sql/66_...");
    }

    private static Path resolveMigration(String name) {
        Path p = Paths.get("sql", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", name);
        if (Files.exists(p)) return p;
        p = Paths.get("sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        return Paths.get("sql", name);
    }

    private static Path resolveProductionMigration(String name) {
        Path p = Paths.get("sql", "production", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", "production", name);
        if (Files.exists(p)) return p;
        p = Paths.get("sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        return Paths.get("sql", "production", name);
    }

    private static String read(Path p) throws Exception {
        assertTrue(Files.exists(p), p + " is missing");
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
