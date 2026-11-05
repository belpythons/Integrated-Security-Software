package com.badaklng.cash.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * One cash expense — money leaving the till, the counterpart of
 * {@code ses_request_payment}, which records money coming in.
 *
 * <p>Attached to no business record. A cash expense is not a step of any
 * workflow; it is a ledger line an admin writes at {@code /admin/cash-expense},
 * and the Balance widget is simply verified payments in minus active expenses
 * out.</p>
 *
 * <p>{@code amount} is {@code NUMBER(15)} — IDR, whole Rupiah, no decimals,
 * deliberately the same unit and precision as
 * {@code ses_request_payment.paid_amount} and
 * {@code ses_request_type.payment_amount}. Storing both sides the same way is
 * the precondition for {@code Balance = Masuk - Keluar} meaning anything.
 * There is a DB CHECK that it is strictly positive: a negative "expense" would
 * be an income entry wearing the wrong hat, and would silently corrupt the
 * balance.</p>
 *
 * <p>Deletion is <b>soft</b> ({@code activeFlag = 'N'}). A cash ledger is a
 * financial record: a mistaken row leaves the listing and the totals, but the
 * row and its audit trail — who created it and who deactivated it — survive.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ses_cash_expense")
public class SesCashExpense extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_cash_expense_seq")
    @SequenceGenerator(name = "ses_cash_expense_seq",
                       sequenceName = "SEQ_SES_CASH_EXPENSE", allocationSize = 1)
    @Column(name = "expense_id")
    private Long expenseId;

    /** "Dikeluarkan ke mana" — the recipient, or the purpose. */
    @Column(name = "paid_to", length = 200, nullable = false)
    private String paidTo;

    /** IDR, whole Rupiah, always greater than zero. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Temporal(TemporalType.DATE)
    @Column(name = "expense_date", nullable = false)
    private Date expenseDate;

    /** {@code 'N'} = soft-deleted; excluded from both the listing and the totals. */
    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";
}
