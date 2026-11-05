package com.badaklng.cash.controller;

import com.badaklng.cash.model.CashExpenseForm;
import com.badaklng.cash.service.CashExpenseService;
import com.badaklng.payment.service.FinancialReportExcelService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.platform.system.service.AppLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.badaklng.cash.entity.SesCashExpense;
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * "Pengeluaran Kas" at {@code /admin/cash-expense} (read) plus
 * {@code /admin/cash-expense/form} (virtual CRUD-grant URL — no mapping needs
 * to exist there, only the {@code app_page} row). Both are seeded by
 * {@code sql/50_cash_expense_seed.sql}, along with the menu entry and the
 * ADMIN/SYSADM grants.
 *
 * <p>One page, not the usual list/form pair: the form is three fields and
 * belongs beside the history it writes to, so an admin can enter a row and see
 * the balance move without a page change. The {@code /form} URL still exists
 * because that is where this application hangs CREATE/UPDATE/DELETE rights —
 * see the same convention on {@code /admin/sticker-type}.</p>
 *
 * <p>No sidebar or {@code SecurityConfig} change was needed: the menu is
 * database-driven ({@code APP_MENU} / {@code APP_ROLE_MENU}) and everything
 * outside {@code /actuator} and {@code /switchuser} is already
 * {@code authenticated()} with per-controller guards.</p>
 */
@Controller
@RequestMapping("/admin/cash-expense")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSADM')")
@RequiredArgsConstructor
public class CashExpenseController {

    /** Matches the flatpickr already initialised on this page, and <input type="date">. */
    private static final String ISO_DATE = "yyyy-MM-dd";

    private final CashExpenseService cashExpenseService;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final FinancialReportExcelService excelService;

    /**
     * The three-sheet financial report, reached from the Histori Kas card.
     *
     * <p>The SAME workbook the Rekap Pembayaran screen downloads — one
     * report, two entry points. Generating a cash-only variant here would
     * give the two screens different answers to "what is the balance", which
     * is the one figure both of them already display.</p>
     *
     * <p>Guarded on the read URL, not {@code /form}: this hands out
     * information the page already shows, and gating a download behind a WRITE
     * grant would deny it to exactly the people whose job is reading it.</p>
     */
    @GetMapping(value = "/report.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> reportXlsx(@RequestParam(value = "from", required = false)
                                             @DateTimeFormat(pattern = ISO_DATE) Date from,
                                             @RequestParam(value = "to", required = false)
                                             @DateTimeFormat(pattern = ISO_DATE) Date to) {
        pageAccessGuard.require(CashExpenseForm.LIST_URL);
        byte[] xlsx = excelService.render(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + excelService.fileNameFor(from, to) + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", pageAccessGuard.require(CashExpenseForm.LIST_URL));
        populate(model, new CashExpenseForm(), q, page, size);
        return "admin/cash-expense/list";
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(CashExpenseForm.LIST_URL));
        CashExpenseForm form = new CashExpenseForm();
        com.badaklng.cash.entity.SesCashExpense e = cashExpenseService.findById(id);
        form.setExpenseId(e.getExpenseId());
        form.setPaidTo(e.getPaidTo());
        form.setAmount(e.getAmount());
        form.setNotes(e.getNotes());
        form.setExpenseDate(e.getExpenseDate());
        populate(model, form);
        return "admin/cash-expense/list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("expenseForm") CashExpenseForm form, Model model,
                       RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(CashExpenseForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        boolean isCreate = form.getExpenseId() == null;
        try {
            Long id = cashExpenseService.save(form.toEntity(), username).getExpenseId();
            logService.log(isCreate ? "SAVE" : "UPDATE", "SesCashExpense",
                    "Saved cash expense " + id, id, "SES_CASH_EXPENSE", request);
            ra.addFlashAttribute("successMsg", "Pengeluaran kas tersimpan.");
            return "redirect:" + CashExpenseForm.LIST_URL;
        } catch (IllegalArgumentException ex) {
            // Re-render the SAME submitted form rather than redirecting: a
            // redirect is a fresh GET that would rebuild a blank form and throw
            // away what the admin just typed.
            model.addAttribute("pageTitle", pageAccessGuard.require(CashExpenseForm.LIST_URL));
            model.addAttribute("errorMsg", ex.getMessage());
            populate(model, form);
            return "admin/cash-expense/list";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(CashExpenseForm.FORM_URL, "DELETE");
        try {
            cashExpenseService.softDelete(id, SecurityUtils.getCurrentUsername());
            logService.log("DELETE", "SesCashExpense",
                    "Deactivated cash expense " + id, id, "SES_CASH_EXPENSE", request);
            ra.addFlashAttribute("successMsg",
                    "Pengeluaran kas dinonaktifkan. Barisnya tetap tersimpan sebagai catatan audit.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + CashExpenseForm.LIST_URL;
    }

    /** Both the history and the three figures, for every render of this page. */
    private void populate(Model model, CashExpenseForm form) {
        populate(model, form, null, null, null);
    }

    /**
     * The history page plus the three figures.
     *
     * <p>The figures come from {@code summary()}, which aggregates in SQL over
     * the whole ledger -- deliberately not from the page on screen, or the
     * balance would change as the reader paged through it.
     */
    private void populate(Model model, CashExpenseForm form,
                          String q, Integer page, Integer size) {
        model.addAttribute("expenseForm", form);
        Page<SesCashExpense> paged = cashExpenseService.findActive(PageRequests.term(q),
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "expenseDate", "expenseId")));
        model.addAttribute("expenses", paged.getContent());
        model.addAttribute("page", paged);
        model.addAttribute("cash", cashExpenseService.summary());
    }
}
