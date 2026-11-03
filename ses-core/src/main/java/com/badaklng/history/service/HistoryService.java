package com.badaklng.history.service;

import com.badaklng.history.model.HistoryRow;
import com.badaklng.history.spi.HistorySource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import com.badaklng.history.entity.SesHistoryView;
import com.badaklng.history.repository.SesHistoryViewRepository;
import com.badaklng.history.repository.SesHistoryViewSpecifications;
import com.badaklng.ses.user.UserDisplayNameService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Merges every registered {@link HistorySource} bean (collected via {@code
 * ObjectProvider} — zero compile-time dependency on any business module,
 * same pattern {@code ApprovalFormService}/{@code PaymentService} use for
 * their own SPIs) into one newest-first feed. Shared by {@code
 * HistoryController} (the full Riwayat list) and the dashboard's "5
 * Pengajuan Terbaru" / "Riwayat Aktivitas Saya" table.
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ObjectProvider<HistorySource> historySources;
    private final SesHistoryViewRepository historyViewRepository;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * One page of the merged feed, read and filtered in the database.
     *
     * <p>{@link #allRowsSorted()} reads both business tables whole, merges and
     * sorts them in the JVM, and hands back everything. That is fine for the
     * dashboard's five newest rows and unworkable for /history: 37 rows
     * rendered to 107 KB of HTML, so a few thousand rows is megabytes the
     * browser must parse before showing the first ten.
     *
     * <p>{@code SES_HISTORY_V} unions the same two tables so the paging,
     * ordering and search happen in SQL. What the view cannot carry is each
     * module's URL shape, so those are still produced by the owning
     * {@link HistorySource} — by string formatting, not by a lookup, so this
     * stays free of per-row queries.
     *
     * <p>Display names are resolved once for the page, not per row.
     *
     * @param term     free-text over transaction number, module name, requestor and status
     * @param status    exact stored status code, or null for any
     * @param module    exact {@code business_type}, or null for any
     * @param allowed  usernames in scope, or null/empty for an unscoped (admin) read
     * @param involved approval instances the reader took part in; only consulted when scoped
     */
    @Transactional(readOnly = true)
    public Page<HistoryRow> pagedRows(String term, String status, String module,
                                      Set<String> allowed, Set<Long> involved,
                                      boolean unscoped, Pageable pageable) {
        Page<SesHistoryView> page = unscoped
                ? historyViewRepository.search(term, status, module, pageable)
                : historyViewRepository.findAll(
                        SesHistoryViewSpecifications.scoped(term, status, module, allowed, involved),
                        pageable);

        Map<String, HistorySource> byType = new HashMap<>();
        historySources.stream()
                .filter(src -> src.businessType() != null)
                .forEach(src -> byType.put(src.businessType(), src));

        Map<String, String> names = userDisplayNameService.displayNames(page.getContent().stream()
                .map(SesHistoryView::getRequestorUserId)
                .filter(ref -> ref != null)
                .collect(Collectors.toSet()));

        List<HistoryRow> rows = page.getContent().stream()
                .map(v -> {
                    HistorySource src = byType.get(v.getBusinessType());
                    String ref = v.getRequestorUserId();
                    return new HistoryRow(
                            v.getBusinessType(),
                            v.getModuleName(),
                            v.getTransactionNo(),
                            ref,
                            ref == null ? null : names.getOrDefault(ref, ref),
                            v.getStatus(),
                            v.getCreatedDate(),
                            src == null ? null : src.viewUrl(v.getBusinessRefId(), v.getApprovalInstanceId()),
                            src == null ? null : src.documentUrl(v.getBusinessRefId(), v.getStatus()),
                            v.getApprovalInstanceId());
                })
                .collect(Collectors.toList());

        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<HistoryRow> allRowsSorted() {
        return historySources.stream()
                .flatMap(s -> s.allRows().stream())
                .sorted(Comparator.comparing(HistoryRow::getCreatedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * The same feed narrowed to one requestor — the non-admin dashboard and
     * the non-admin Riwayat page.
     *
     * <p>Filtered here rather than in the SPI: {@link HistorySource} is a
     * deliberately bulk, unscoped contract (see its javadoc), and adding a
     * scoped method to it would mean editing every module for two screens.</p>
     *
     * <p>Matches on {@link HistoryRow#getRequestorUserId()} — the raw
     * {@code requestor_user_id}, which is the same string {@code
     * SecurityUtils.getCurrentUsername()} returns. It used to match on
     * {@code requestorDisplay}, which worked only for as long as that field
     * also held the username; now that it holds the person's real name, the
     * old comparison would have returned an empty list for everybody, with no
     * error anywhere to say why.</p>
     */
    @Transactional(readOnly = true)
    public List<HistoryRow> myRowsSorted(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return allRowsSorted().stream()
                .filter(r -> username.trim().equalsIgnoreCase(r.getRequestorUserId()))
                .collect(Collectors.toList());
    }

    /**
     * Scoped feed for Section / Department / Personal data access hierarchy.
     */
    @Transactional(readOnly = true)
    public List<HistoryRow> scopedRowsSorted(java.util.Set<String> allowedUsernames) {
        return scopedRowsSorted(allowedUsernames, Collections.emptySet());
    }

    /**
     * Scoped feed allowing section/department members, all APPROVED requests,
     * and any request where the user participated as an approver or evaluator.
     */
    @Transactional(readOnly = true)
    public List<HistoryRow> scopedRowsSorted(java.util.Set<String> allowedUsernames, java.util.Set<Long> involvedInstanceIds) {
        boolean hasAllowed = allowedUsernames != null && !allowedUsernames.isEmpty();
        boolean hasInvolved = involvedInstanceIds != null && !involvedInstanceIds.isEmpty();
        return allRowsSorted().stream()
                .filter(r -> {
                    if (r.getStatus() != null && "APPROVED".equalsIgnoreCase(r.getStatus())) {
                        return true;
                    }
                    if (hasAllowed && r.getRequestorUserId() != null
                            && allowedUsernames.contains(r.getRequestorUserId().trim())) {
                        return true;
                    }
                    if (hasInvolved && r.getInstanceId() != null
                            && involvedInstanceIds.contains(r.getInstanceId())) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
}


