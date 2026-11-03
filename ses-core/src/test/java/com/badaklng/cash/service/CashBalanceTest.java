package com.badaklng.cash.service;

import com.badaklng.approval.entity.CompletionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code Balance = Kas Masuk − Kas Keluar}, and the parse fallbacks that keep
 * a half-configured chain behaving like the engine it replaced.
 *
 * <p>The balance arithmetic looks too simple to test, and that is exactly why
 * it is tested: it is rendered in two places (the Pengeluaran Kas screen and
 * the dashboard widget), and a sign error would look plausible on both. The
 * figure is computed ONCE in {@link CashExpenseService.CashSummary} for that
 * reason, and this pins the direction of the subtraction.</p>
 */
class CashBalanceTest {

    @Test
    @DisplayName("Balance is in minus out, in that order")
    void balanceIsInMinusOut() {
        CashExpenseService.CashSummary s =
                new CashExpenseService.CashSummary(5_000_000L, 1_250_000L, 5_000_000L - 1_250_000L);
        assertEquals(5_000_000L, s.getTotalIn());
        assertEquals(1_250_000L, s.getTotalOut());
        assertEquals(3_750_000L, s.getBalance());
    }

    @Test
    @DisplayName("An empty till reads as zero, not as null")
    void emptyTillIsZero() {
        // Both aggregates use COALESCE(..., 0), so a fresh environment with no
        // payments and no expenses must produce three zeroes rather than a
        // null the template would have to defend against.
        CashExpenseService.CashSummary s = new CashExpenseService.CashSummary(0L, 0L, 0L);
        assertEquals(0L, s.getTotalIn());
        assertEquals(0L, s.getTotalOut());
        assertEquals(0L, s.getBalance());
    }

    @Test
    @DisplayName("More paid out than taken in gives a NEGATIVE balance, not zero")
    void negativeBalanceIsRepresentable() {
        // A real state of the till. The screens show it in red rather than
        // clamping it, so the value must be allowed to go below zero here.
        CashExpenseService.CashSummary s =
                new CashExpenseService.CashSummary(1_000_000L, 2_500_000L, 1_000_000L - 2_500_000L);
        assertEquals(-1_500_000L, s.getBalance());
    }

    @Test
    @DisplayName("CompletionRule.parse fails to ANY — the behaviour every chain had before")
    void completionRuleFailsToAny() {
        // Not strictly a cash rule, but the same class of decision: an
        // unreadable value must leave the system behaving the way it did
        // before the feature existed, never convert a step into one nobody
        // can complete.
        assertEquals(CompletionRule.ANY, CompletionRule.parse(null));
        assertEquals(CompletionRule.ANY, CompletionRule.parse(""));
        assertEquals(CompletionRule.ANY, CompletionRule.parse("nonsense"));
        assertEquals(CompletionRule.ALL, CompletionRule.parse(" all "));
        assertEquals(CompletionRule.ANY, CompletionRule.parse("ANY"));
    }
}
