package com.badaklng.cash.service;

import com.badaklng.cash.entity.SesCashExpense;
import com.badaklng.cash.repository.SesCashExpenseRepository;
import com.badaklng.payment.service.PaymentService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * "Pengeluaran Kas" — money leaving the till, and the three figures that make
 * the cash position readable.
 *
 * <p>The ledger is the counterpart of {@code ses_request_payment}: that table
 * records money coming IN, this one records it going OUT. Nothing attaches an
 * expense to a business record, because an expense is not a step of any
 * workflow — it is a line an admin writes.</p>
 *
 * <p><b>Metric definitions</b> (product owner, 2026-10-05):</p>
 * <ul>
 *   <li><b>Kas Masuk</b> = sum of {@code paid_amount} over every
 *       {@code VERIFIED} payment. This is the definition {@code /payment/recap}
 *       already uses, and reusing it is the point: two screens showing
 *       different totals for "money received" would be worse than either
 *       number being slightly off.</li>
 *   <li><b>Kas Keluar</b> = sum of {@code amount} over every ACTIVE expense.</li>
 *   <li><b>Balance</b> = Masuk − Keluar.</li>
 * </ul>
 *
 * <p>The spec described Kas Masuk as "verified payments <i>before handover</i>".
 * There is no notion of handover anywhere in this application — no column, no
 * screen, no status — so it is deliberately NOT invented here. Adding one would
 * mean a new table and a new workflow nobody asked to build; the figure shown
 * is therefore all verified cash, and this paragraph exists so that is a stated
 * limitation rather than a silent reinterpretation.</p>
 */
@Service
@RequiredArgsConstructor
public class CashExpenseService {

    private final SesCashExpenseRepository expenseRepository;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public List<SesCashExpense> findActive() {
        return expenseRepository.findByActiveFlagOrderByExpenseDateDescExpenseIdDesc("Y");
    }

    /** One page of {@link #findActive}, optionally filtered by a free-text term. */
    @Transactional(readOnly = true)
    public Page<SesCashExpense> findActive(String term, Pageable pageable) {
        return expenseRepository.search("Y", term, pageable);
    }

    @Transactional(readOnly = true)
    public SesCashExpense findById(Long expenseId) {
        return expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Pengeluaran kas tidak ditemukan: " + expenseId));
    }

    /**
     * Records one expense.
     *
     * <p>Validation is here rather than relying on the CHECK constraints: a
     * raw ORA-02290 reaching the admin says nothing useful, and "nominal harus
     * lebih besar dari nol" says exactly what to do.</p>
     */
    @Transactional
    public SesCashExpense save(SesCashExpense input, String username) {
        String paidTo = input.getPaidTo() == null ? "" : input.getPaidTo().trim();
        if (paidTo.isEmpty()) {
            throw new IllegalArgumentException("Kolom \"Dikeluarkan ke\" wajib diisi.");
        }
        if (input.getAmount() == null || input.getAmount() <= 0) {
            throw new IllegalArgumentException("Nominal harus lebih besar dari nol.");
        }

        SesCashExpense row;
        if (input.getExpenseId() == null) {
            row = new SesCashExpense();
            row.setCreatedBy(username);
            row.setCreatedDate(new Date());
        } else {
            row = findById(input.getExpenseId());
            row.setUpdatedBy(username);
            row.setUpdatedDate(new Date());
        }
        row.setPaidTo(paidTo);
        row.setAmount(input.getAmount());
        row.setNotes(input.getNotes());
        // Defaults to today rather than refusing: the date is a convenience on
        // a form whose real content is "who, how much, why", and an admin
        // recording an expense as they make it means today every time.
        row.setExpenseDate(input.getExpenseDate() != null ? input.getExpenseDate() : new Date());
        row.setActiveFlag("Y");
        return expenseRepository.save(row);
    }

    /**
     * SOFT delete. A cash ledger is a financial record: a mistaken row leaves
     * the listing and the totals, but the row and its audit trail — who created
     * it and who deactivated it — survive.
     */
    @Transactional
    public void softDelete(Long expenseId, String username) {
        SesCashExpense row = findById(expenseId);
        row.setActiveFlag("N");
        row.setUpdatedBy(username);
        row.setUpdatedDate(new Date());
        expenseRepository.save(row);
    }

    /** The three figures, computed as aggregates rather than by loading rows. */
    @Transactional(readOnly = true)
    public CashSummary summary() {
        long in = paymentService.totalVerifiedAmount();
        long out = expenseRepository.sumActiveAmount();
        return new CashSummary(in, out, in - out);
    }

    /**
     * Kas Masuk / Kas Keluar / Balance, in IDR whole Rupiah.
     *
     * <p>{@code balance} is stored rather than computed by the caller so the
     * dashboard widget and the Pengeluaran Kas screen cannot subtract in
     * different orders — a sign error that looks plausible on both screens is
     * exactly the kind of bug nobody reports.</p>
     */
    @Getter
    public static class CashSummary {
        private final long totalIn;
        private final long totalOut;
        private final long balance;

        public CashSummary(long totalIn, long totalOut, long balance) {
            this.totalIn = totalIn;
            this.totalOut = totalOut;
            this.balance = balance;
        }
    }
}
