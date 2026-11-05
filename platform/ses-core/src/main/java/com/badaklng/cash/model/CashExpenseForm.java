package com.badaklng.cash.model;

import com.badaklng.cash.entity.SesCashExpense;
import com.badaklng.ses.validation.SafeText;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Web binding target for the Pengeluaran Kas form.
 */
@Getter
@Setter
@NoArgsConstructor
public class CashExpenseForm {

    public static final String LIST_URL = "/admin/cash-expense";
    /** Virtual CRUD-grant URL — no controller maps it; only the app_page row exists. */
    public static final String FORM_URL = "/admin/cash-expense/form";

    /** Null on create. */
    private Long expenseId;

    @NotBlank(message = "Penerima dana wajib diisi")
    @Size(max = 100, message = "Nama penerima maksimal 100 karakter")
    @SafeText
    private String paidTo;

    /** IDR, whole Rupiah. */
    @NotNull(message = "Jumlah pengeluaran wajib diisi")
    @Positive(message = "Jumlah pengeluaran harus lebih besar dari 0")
    private Long amount;

    @Size(max = 500, message = "Keterangan maksimal 500 karakter")
    @SafeText
    private String notes;

    @NotNull(message = "Tanggal pengeluaran wajib diisi")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date expenseDate;

    public SesCashExpense toEntity() {
        SesCashExpense e = new SesCashExpense();
        e.setExpenseId(expenseId);
        e.setPaidTo(paidTo);
        e.setAmount(amount);
        e.setNotes(notes);
        e.setExpenseDate(expenseDate);
        return e;
    }
}
