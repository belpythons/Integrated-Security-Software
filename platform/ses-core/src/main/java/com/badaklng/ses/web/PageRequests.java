package com.badaklng.ses.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.List;

/**
 * One place to turn {@code ?page=&size=} into a {@link Pageable} the list
 * screens can trust.
 *
 * <p>Spring's {@code @PageableDefault} would cover the happy path, but not the
 * two things that matter here: {@code size} arrives from a query string a user
 * can edit, and an unbounded {@code size=100000} would put the whole table
 * back into one response — the exact problem pagination was added to remove.
 * The clamp lives here rather than in each controller so it cannot be applied
 * in eight places and forgotten in the ninth.
 */
public final class PageRequests {

    /** Matches the default selected in the shared pager toolbar. */
    public static final int DEFAULT_SIZE = 25;

    /**
     * Highest page size a caller may ask for.
     *
     * <p>100 rows is roughly 280 KB of rendered HTML on these screens (~2.8 KB
     * per row, measured). Above that the page stops being a page.
     */
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /** Clamped page request, newest-first by {@code sortBy}. */
    public static Pageable of(Integer page, Integer size, String sortBy) {
        return PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, sortBy));
    }

    /** Clamped page request with the caller's own sort. */
    public static Pageable of(Integer page, Integer size, Sort sort) {
        return PageRequest.of(safePage(page), safeSize(size), sort);
    }

    public static int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public static int safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** Null/blank-safe trim, so a blank {@code ?q=} means "no search" rather than "match empty". */
    public static String term(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Case-insensitive "does any of these fields contain the term".
     *
     * <p>For screens whose searchable text is not a column: the payment and
     * approval lists assemble each row across modules through an SPI, so the
     * transaction number and the module name a reader searches for do not
     * exist in the table being queried. Filtering the built rows keeps the
     * search meaning the same as on the screens that can push it into SQL.
     *
     * <p>A null or blank term matches everything, so callers can pass
     * {@link #term(String)} straight through without branching.
     */
    public static boolean matches(String term, String... fields) {
        if (term == null || term.trim().isEmpty()) {
            return true;
        }
        String needle = term.trim().toUpperCase();
        for (String field : fields) {
            if (field != null && field.toUpperCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Slices an already-materialised list into a {@link Page}.
     *
     * <p>For the one feed that cannot be paginated in SQL: {@code /history}
     * merges rows from every {@code HistorySource} bean in memory, and
     * {@code /approval} assembles its worklist from a view plus per-instance
     * reads. Paging here does not reduce what is read, but it does bound what
     * is <em>rendered</em> — which is the measured problem on those screens.
     * Anything backed by a single table should use a repository
     * {@code Page<T>} instead, and most do.
     */
    public static <T> Page<T> slice(List<T> all, Pageable pageable) {
        if (all == null || all.isEmpty()) {
            return new PageImpl<>(Collections.<T>emptyList(), pageable, 0);
        }
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    /** Self-check: {@code java PageRequests} after changing the bounds above. */
    public static void main(String[] args) {
        assert safeSize(null) == DEFAULT_SIZE : "null size -> default";
        assert safeSize(0) == DEFAULT_SIZE : "zero size -> default";
        assert safeSize(-5) == DEFAULT_SIZE : "negative size -> default";
        assert safeSize(10) == 10 : "size passes through";
        assert safeSize(100000) == MAX_SIZE : "oversized request must be clamped";
        assert safePage(null) == 0 && safePage(-3) == 0 && safePage(4) == 4 : "page bounds";
        assert term(null) == null && term("   ") == null && "x".equals(term("  x ")) : "term trimming";

        List<Integer> all = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            all.add(i);
        }
        Page<Integer> p0 = slice(all, PageRequest.of(0, 3));
        Page<Integer> p2 = slice(all, PageRequest.of(2, 3));
        Page<Integer> past = slice(all, PageRequest.of(9, 3));
        assert p0.getContent().equals(java.util.Arrays.asList(0, 1, 2)) : "first slice";
        assert p2.getContent().equals(java.util.Arrays.asList(6)) : "last partial slice";
        assert past.getContent().isEmpty() : "page past the end must be empty, not an exception";
        assert p0.getTotalElements() == 7 && p0.getTotalPages() == 3 : "totals come from the full list";
        assert slice(null, PageRequest.of(0, 3)).getTotalElements() == 0 : "null list";

        assert matches(null, "apa pun") : "null term matches";
        assert matches("  ", "apa pun") : "blank term matches";
        assert matches("req", "REQ-2026-0001") : "case-insensitive";
        assert matches("2026", null, "REQ-2026-0001") : "null field skipped";
        assert !matches("zzz", "REQ-2026-0001", null) : "no match";

        System.out.println("PageRequests self-check OK (default=" + DEFAULT_SIZE + ", max=" + MAX_SIZE + ")");
    }
}
