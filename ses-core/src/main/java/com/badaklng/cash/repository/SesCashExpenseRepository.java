package com.badaklng.cash.repository;

import com.badaklng.cash.entity.SesCashExpense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesCashExpenseRepository extends JpaRepository<SesCashExpense, Long> {

    /** The history listing: active rows only, newest expense first. */
    List<SesCashExpense> findByActiveFlagOrderByExpenseDateDescExpenseIdDesc(String activeFlag);

    /**
     * The same listing, restricted to an expense-date window.
     *
     * <p>Filters on {@code expenseDate} (the day the money left the till),
     * not on {@code createdDate} (the day somebody typed it in). Those differ
     * whenever an expense is entered late, and the report is about the cash,
     * not the data entry.</p>
     *
     * <p>{@code expenseDate} is a {@code TemporalType.DATE} with no time
     * component, so an inclusive {@code BETWEEN} over whole days behaves as
     * expected here — unlike the payment side, whose column is a TIMESTAMP.</p>
     */
    List<SesCashExpense> findByActiveFlagAndExpenseDateBetweenOrderByExpenseDateDescExpenseIdDesc(
            String activeFlag, java.util.Date from, java.util.Date to);

    /**
     * One page of the history listing, optionally narrowed by a free-text
     * term over the two columns the screen shows as text.
     *
     * <p>A null term skips the condition, so the unsearched case stays the
     * plain indexed lookup on {@code (active_flag, expense_date)}.
     */
    @Query("SELECT e FROM SesCashExpense e WHERE e.activeFlag = :activeFlag "
            + "AND (:term IS NULL OR UPPER(e.paidTo) LIKE UPPER(CONCAT('%', :term, '%')) "
            + "     OR UPPER(e.notes) LIKE UPPER(CONCAT('%', :term, '%')))")
    Page<SesCashExpense> search(@Param("activeFlag") String activeFlag,
                                @Param("term") String term,
                                Pageable pageable);

    /**
     * Total cash out, as a single aggregate.
     *
     * <p>Deliberately not {@code findAll().stream().mapToLong(...).sum()}: this
     * figure is rendered on the admin dashboard for every page load, and the
     * ledger grows without bound. {@code COALESCE} rather than a nullable
     * return so an empty ledger reads as {@code 0} and the caller never has to
     * null-check a number.</p>
     */
    @Query("select coalesce(sum(e.amount), 0) from SesCashExpense e where e.activeFlag = 'Y'")
    long sumActiveAmount();
}
