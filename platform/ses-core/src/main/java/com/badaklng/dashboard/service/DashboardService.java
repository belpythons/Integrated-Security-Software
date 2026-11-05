package com.badaklng.dashboard.service;

import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.cash.service.CashExpenseService;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.ses.jdbc.InChunks;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates counts/trends for {@code /dashboard} across every business
 * module. Deliberately depends on {@code SesRequestRepository}/{@code
 * SesStickerRequestRepository} directly rather than through a generic SPI
 * (unlike payment/approval/history) — a dashboard is inherently a
 * cross-cutting, read-only reporting surface with no business logic a
 * module needs to own; adding a third SPI for two call sites would be
 * ceremony without payoff. If a third request-like module appears later,
 * revisit by reusing {@code com.badaklng.history.spi.HistorySource}
 * instead (it already provides createdDate/status per row, which is all
 * the trend/composition charts below actually need).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int TREND_MONTHS = 12;
    private static final DateTimeFormatter MONTH_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM yy", new Locale("id", "ID"));

    private final SesRequestRepository requestRepository;
    private final SesStickerRequestRepository stickerRequestRepository;
    private final SesRequestWorkerRepository requestWorkerRepository;
    private final PaymentService paymentService;
    private final CashExpenseService cashExpenseService;

    @org.springframework.beans.factory.annotation.Value("${ses.dashboard.snapshot-cache-ttl-ms:60000}")
    private long snapshotCacheTtlMs;

    private volatile AdminAggregates cachedAdmin;
    private volatile long cachedAdminExpiresAt;

    /**
     * The two row sets every card and chart on this page is derived from,
     * read once.
     *
     * <p>Before this existed, {@code summary()}, {@code monthlyTrend()} and
     * {@code monthlyComposition()} each began with their own
     * {@code findAll()} on both tables, and the controller calls all three
     * plus {@code monthlyCashInflow()} in one request -- so SES_REQUEST and
     * SES_STICKER_REQUEST were each materialised into the JVM four times per
     * dashboard hit. The arithmetic below is unchanged; only the reading is.
     */
    @Getter
    @RequiredArgsConstructor
    public static class Snapshot {
        private final List<SesRequest> requests;
        private final List<SesStickerRequest> stickers;
    }

    /** Org-wide snapshot: two full-table queries. Admin page reads it through {@link #adminAggregates()}. */
    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        return new Snapshot(requestRepository.findAll(), stickerRequestRepository.findAll());
    }

    @Getter
    @RequiredArgsConstructor
    public static class AdminAggregates {
        private final Summary summary;
        private final TrendChart trend;
        private final List<CompositionPoint> composition;
        private final CashChart cashInflow;
    }

    /**
     * Everything the admin /dashboard renders, assembled once per TTL --
     * same plain volatile+timestamp pattern as ApprovalService's inbox cache.
     * Staleness is bounded by the TTL; call {@link #invalidateDashboardCache()} after a
     * write that must show up immediately.
     */
    public AdminAggregates adminAggregates() {
        long now = System.currentTimeMillis();
        AdminAggregates cached = cachedAdmin;
        if (cached != null && cachedAdminExpiresAt > now) {
            return cached;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -TREND_MONTHS);
        Date since = cal.getTime();
        Snapshot s = new Snapshot(requestRepository.findByCreatedDateAfter(since),
                stickerRequestRepository.findByCreatedDateAfter(since));
        AdminAggregates fresh = new AdminAggregates(adminSummaryFast(), monthlyTrend(s),
                monthlyComposition(s), monthlyCashInflow());
        cachedAdmin = fresh;
        cachedAdminExpiresAt = now + snapshotCacheTtlMs;
        return fresh;
    }

    /**
     * Fast admin-wide summary calculated via database-level aggregation queries
     * without loading full entities into heap memory.
     */
    @Transactional(readOnly = true)
    public Summary adminSummaryFast() {
        Map<String, Long> reqStatus = requestRepository.countByStatusGrouped().stream()
                .filter(r -> r != null && r.length > 1 && r[0] != null && r[1] != null)
                .collect(Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue(), (a, b) -> a + b));
        Map<String, Long> stkStatus = stickerRequestRepository.countByStatusGrouped().stream()
                .filter(r -> r != null && r.length > 1 && r[0] != null && r[1] != null)
                .collect(Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue(), (a, b) -> a + b));

        long totalReq = reqStatus.values().stream().mapToLong(Long::longValue).sum();
        long totalStk = stkStatus.values().stream().mapToLong(Long::longValue).sum();

        long pending = reqStatus.getOrDefault("PENDING", 0L)
                + reqStatus.getOrDefault(RequestStatus.WAITING_FOR_UPLOAD, 0L)
                + stkStatus.getOrDefault("PENDING", 0L);
        long approved = reqStatus.getOrDefault("APPROVED", 0L) + stkStatus.getOrDefault("APPROVED", 0L);
        long rejected = reqStatus.getOrDefault("REJECTED", 0L) + stkStatus.getOrDefault("REJECTED", 0L);

        long verifying = paymentService.findQueue().size();

        Set<String> requestors = new HashSet<>(requestRepository.findDistinctRequestorUserIds());
        requestors.addAll(stickerRequestRepository.findDistinctRequestorUserIds());

        YearMonth thisMonth = YearMonth.now();
        long cashThisMonth = paymentService.findVerified().stream()
                .filter(p -> p.getReviewedDate() != null && toYearMonth(p.getReviewedDate()).equals(thisMonth))
                .mapToLong(p -> p.getPaidAmount() != null ? p.getPaidAmount() : 0L)
                .sum();

        CashExpenseService.CashSummary cash = cashExpenseService.summary();
        long totalWorkers = requestWorkerRepository.count();

        return new Summary(totalReq + totalStk, pending, approved, rejected, verifying,
                totalStk, requestors.size(), cashThisMonth,
                cash.getTotalIn(), cash.getTotalOut(), cash.getBalance(), totalWorkers);
    }

    public void invalidateDashboardCache() {
        cachedAdmin = null;
    }

    /**
     * Snapshot narrowed to the usernames a requestor is allowed to see -- two
     * indexed queries instead of two full table scans plus an in-memory
     * filter. An empty scope means nothing to show, and costs no query.
     */
    @Transactional(readOnly = true)
    public Snapshot snapshot(Set<String> allowedUsernames) {
        if (allowedUsernames == null || allowedUsernames.isEmpty()) {
            return new Snapshot(Collections.emptyList(), Collections.emptyList());
        }
        return new Snapshot(
                InChunks.flatMap(allowedUsernames, requestRepository::findByRequestorUserIdIn),
                InChunks.flatMap(allowedUsernames, stickerRequestRepository::findByRequestorUserIdIn));
    }

    @Transactional(readOnly = true)
    public Summary summary(Snapshot snapshot) {
        List<SesRequest> requests = snapshot.getRequests();
        List<SesStickerRequest> stickers = snapshot.getStickers();

        long pending = countInFlight(requests, stickers);
        long approved = countStatus(requests, stickers, "APPROVED");
        long rejected = countStatus(requests, stickers, "REJECTED");
        long total = requests.size() + stickers.size();

        long verifying = paymentService.findQueue().size();

        Set<String> requestors = new HashSet<>();
        requests.forEach(r -> addIfPresent(requestors, r.getRequestorUserId()));
        stickers.forEach(r -> addIfPresent(requestors, r.getRequestorUserId()));

        YearMonth thisMonth = YearMonth.now();
        long cashThisMonth = paymentService.findVerified().stream()
                .filter(p -> p.getReviewedDate() != null && toYearMonth(p.getReviewedDate()).equals(thisMonth))
                .mapToLong(p -> p.getPaidAmount() != null ? p.getPaidAmount() : 0L)
                .sum();

        CashExpenseService.CashSummary cash = cashExpenseService.summary();
        long totalWorkers = countWorkers(requests);
        return new Summary(total, pending, approved, rejected, verifying,
                stickers.size(), requestors.size(), cashThisMonth,
                cash.getTotalIn(), cash.getTotalOut(), cash.getBalance(), totalWorkers);
    }

    /**
     * The same {@link Summary} shape, but counting only what {@code username}
     * submitted — the requestor's view of {@code /dashboard}.
     *
     * <p>Reuses {@code Summary} rather than introducing a second DTO so the
     * template keeps one binding for the four cards both roles share. The
     * remaining four fields stay zero by design: verifying payments, sticker
     * totals, the requestor headcount and cash inflow are org-wide figures,
     * and the template hides those cards for a non-admin anyway.</p>
     *
     * <p>Scoped through the existing {@code findByRequestorUserId...} finders
     * (the same ones {@code RequestService#findMine}/{@code
     * StickerService#findMine} use) instead of filtering {@code findAll()} in
     * memory — a requestor has no business pulling every row in the table
     * across the wire to count three of them.</p>
     */
    @Transactional(readOnly = true)
    public Summary mySummary(String username) {
        List<SesRequest> requests = requestRepository.findByRequestorUserIdOrderByRequestIdDesc(username);
        List<SesStickerRequest> stickers =
                stickerRequestRepository.findByRequestorUserIdOrderByStickerRequestIdDesc(username);
        long totalWorkers = countWorkers(requests);

        return new Summary(requests.size() + stickers.size(),
                countInFlight(requests, stickers),
                countStatus(requests, stickers, "APPROVED"),
                countStatus(requests, stickers, "REJECTED"),
                // The admin-only fields stay zero by design — a requestor sees
                // their own submissions, never the org's cash position. The
                // template gates all of them on ${adminView} anyway; zeroing
                // them here means the data is not even sent.
                0, 0, 0, 0,
                0, 0, 0, totalWorkers);
    }

    /**
     * 12-month submission trend as ready-to-render SVG geometry (viewBox
     * {@value #CHART_WIDTH}x{@value #CHART_HEIGHT}) — computed server-side
     * so the template only has to emit a {@code <polyline>}/{@code
     * <circle>} per pre-calculated point, no chart JS library needed (none
     * is vendored in this app — see the class javadoc).
     */
    /**
     * {@link #summary} for a requestor's scope. {@code scopeSize} is the
     * number of colleagues in scope, which the org-wide variant derives from
     * the rows themselves but this one cannot -- a section with ten people
     * and two submissions still has ten people in it.
     */
    @Transactional(readOnly = true)
    public Summary scopedSummary(Snapshot snapshot, int scopeSize) {
        List<SesRequest> requests = snapshot.getRequests();
        List<SesStickerRequest> stickers = snapshot.getStickers();
        if (requests.isEmpty() && stickers.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        long pending = countInFlight(requests, stickers);
        long approved = countStatus(requests, stickers, "APPROVED");
        long rejected = countStatus(requests, stickers, "REJECTED");
        long total = requests.size() + stickers.size();
        long totalWorkers = countWorkers(requests);

        return new Summary(total, pending, approved, rejected,
                0, stickers.size(), scopeSize, 0,
                0, 0, 0, totalWorkers);
    }

    @Transactional(readOnly = true)
    public TrendChart monthlyTrend(Snapshot snapshot) {
        return buildTrendChart(snapshot.getRequests(), snapshot.getStickers());
    }

    private TrendChart buildTrendChart(List<SesRequest> requests, List<SesStickerRequest> stickers) {
        List<YearMonth> months = lastNMonths(TREND_MONTHS);

        List<String> labels = new ArrayList<>();
        List<Long> reqCounts = new ArrayList<>();
        List<Long> stkCounts = new ArrayList<>();
        long maxValue = 1;
        for (YearMonth m : months) {
            long reqCount = requests.stream().filter(r -> r.getCreatedDate() != null && toYearMonth(r.getCreatedDate()).equals(m)).count();
            long stkCount = stickers.stream().filter(r -> r.getCreatedDate() != null && toYearMonth(r.getCreatedDate()).equals(m)).count();
            labels.add(m.format(MONTH_LABEL_FORMAT));
            reqCounts.add(reqCount);
            stkCounts.add(stkCount);
            maxValue = Math.max(maxValue, Math.max(reqCount, stkCount));
        }

        List<SeriesPoint> requestSeries = toSeriesPoints(labels, reqCounts, maxValue);
        List<SeriesPoint> stickerSeries = toSeriesPoints(labels, stkCounts, maxValue);
        int bottomY = CHART_HEIGHT - CHART_PAD_BOTTOM;
        return new TrendChart(CHART_WIDTH, CHART_HEIGHT, labels, requestSeries, stickerSeries,
                toPolyline(requestSeries), toPolyline(stickerSeries),
                toSmoothPath(requestSeries), toSmoothPath(stickerSeries),
                toAreaPath(requestSeries, bottomY), toAreaPath(stickerSeries, bottomY),
                gridLines());
    }

    private static final int CHART_WIDTH = 760;
    private static final int CHART_HEIGHT = 220;
    private static final int CHART_PAD_X = 24;
    private static final int CHART_PAD_TOP = 16;
    private static final int CHART_PAD_BOTTOM = 16;
    private static final int GRID_LINE_COUNT = 3;

    private List<SeriesPoint> toSeriesPoints(List<String> labels, List<Long> values, long maxValue) {
        List<SeriesPoint> points = new ArrayList<>();
        int n = values.size();
        double xStep = n > 1 ? (double) (CHART_WIDTH - 2 * CHART_PAD_X) / (n - 1) : 0;
        double plotHeight = CHART_HEIGHT - CHART_PAD_TOP - CHART_PAD_BOTTOM;
        for (int i = 0; i < n; i++) {
            int x = (int) Math.round(CHART_PAD_X + i * xStep);
            long value = values.get(i);
            int y = (int) Math.round(CHART_HEIGHT - CHART_PAD_BOTTOM - (value / (double) maxValue) * plotHeight);
            points.add(new SeriesPoint(x, y, value, labels.get(i)));
        }
        return points;
    }

    private String toPolyline(List<SeriesPoint> points) {
        StringBuilder sb = new StringBuilder();
        for (SeriesPoint p : points) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p.getX()).append(',').append(p.getY());
        }
        return sb.toString();
    }

    /**
     * Same points as {@link #toPolyline}, but as a cubic-Bézier SVG path —
     * one curve per segment, control points held flat (same y as their own
     * endpoint) a third of the way across the segment. That flat-tangent
     * choice is what keeps the curve from overshooting past a local max/min
     * between two points, which a full Catmull-Rom spline would risk on the
     * spiky month-to-month counts these charts plot.
     */
    private String toSmoothPath(List<SeriesPoint> points) {
        if (points.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("M").append(points.get(0).getX()).append(',').append(points.get(0).getY());
        for (int i = 1; i < points.size(); i++) {
            SeriesPoint p0 = points.get(i - 1);
            SeriesPoint p1 = points.get(i);
            double dx = (p1.getX() - p0.getX()) / 3.0;
            sb.append(" C").append(p0.getX() + dx).append(',').append(p0.getY())
                    .append(' ').append(p1.getX() - dx).append(',').append(p1.getY())
                    .append(' ').append(p1.getX()).append(',').append(p1.getY());
        }
        return sb.toString();
    }

    /** The same spline, closed down to {@code bottomY} — fillable as a gradient area under the line. */
    private String toAreaPath(List<SeriesPoint> points, int bottomY) {
        if (points.isEmpty()) {
            return "";
        }
        return toSmoothPath(points)
                + " L" + points.get(points.size() - 1).getX() + ',' + bottomY
                + " L" + points.get(0).getX() + ',' + bottomY + " Z";
    }

    /** Evenly spaced horizontal guide lines across the plot area, for the background grid. */
    private List<Integer> gridLines() {
        List<Integer> lines = new ArrayList<>();
        double plotHeight = CHART_HEIGHT - CHART_PAD_TOP - CHART_PAD_BOTTOM;
        for (int i = 1; i <= GRID_LINE_COUNT; i++) {
            lines.add((int) Math.round(CHART_PAD_TOP + plotHeight * i / (GRID_LINE_COUNT + 1)));
        }
        return lines;
    }

    /**
     * 12-month status composition — pending/approved/rejected counts per
     * month, combined across modules, as a TRUE stacked bar: each month's
     * three segment heights (percent of the tallest month's total) sum to
     * that month's own total — not independently scaled to the window's
     * max, which would let concurrently-high segments overflow their
     * container. Pre-computed here so the template only applies literal
     * percentages, no client-side math.
     */
    @Transactional(readOnly = true)
    public List<CompositionPoint> monthlyComposition(Snapshot snapshot) {
        List<SesRequest> requests = snapshot.getRequests();
        List<SesStickerRequest> stickers = snapshot.getStickers();
        List<YearMonth> months = lastNMonths(TREND_MONTHS);

        List<long[]> raw = new ArrayList<>();
        long maxTotal = 1;
        for (YearMonth m : months) {
            long pending = monthStatusCount(requests, stickers, m, "PENDING")
                    + monthStatusCount(requests, stickers, m, RequestStatus.WAITING_FOR_UPLOAD);
            long approved = monthStatusCount(requests, stickers, m, "APPROVED");
            long rejected = monthStatusCount(requests, stickers, m, "REJECTED");
            raw.add(new long[]{pending, approved, rejected});
            maxTotal = Math.max(maxTotal, pending + approved + rejected);
        }

        List<CompositionPoint> result = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            long[] v = raw.get(i);
            long pending = v[0], approved = v[1], rejected = v[2];
            double pendingPct = pending * 100.0 / maxTotal;
            double approvedPct = approved * 100.0 / maxTotal;
            double rejectedPct = rejected * 100.0 / maxTotal;
            result.add(new CompositionPoint(months.get(i).format(MONTH_LABEL_FORMAT),
                    pending, approved, rejected, pendingPct, approvedPct, rejectedPct));
        }
        return result;
    }

    /**
     * 12-month cash-inflow — sum of paidAmount for VERIFIED payments,
     * bucketed by review month, as the same ready-to-render SVG line-chart
     * geometry {@link #monthlyTrend()} uses (one series instead of two).
     */
    @Transactional(readOnly = true)
    public CashChart monthlyCashInflow() {
        List<SesRequestPayment> verified = paymentService.findVerified();
        List<YearMonth> months = lastNMonths(TREND_MONTHS);

        List<String> labels = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        long maxValue = 1;
        for (YearMonth m : months) {
            long amount = verified.stream()
                    .filter(p -> p.getReviewedDate() != null && toYearMonth(p.getReviewedDate()).equals(m))
                    .mapToLong(p -> p.getPaidAmount() != null ? p.getPaidAmount() : 0L)
                    .sum();
            labels.add(m.format(MONTH_LABEL_FORMAT));
            amounts.add(amount);
            maxValue = Math.max(maxValue, amount);
        }

        List<SeriesPoint> series = toSeriesPoints(labels, amounts, maxValue);
        return new CashChart(CHART_WIDTH, CHART_HEIGHT, labels, series, toPolyline(series),
                toSmoothPath(series), toAreaPath(series, CHART_HEIGHT - CHART_PAD_BOTTOM), gridLines());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Total worker rows across the given requests.
     *
     * <p>Chunked, because the previous shape handed every request id in the
     * table to {@code countByRequestIdIn} in one go. Oracle caps an
     * {@code IN} list at 1000 expressions, so the admin dashboard would have
     * started failing with ORA-01795 the moment SES_REQUEST passed 1000 rows
     * -- not degrading, failing: the whole page 500s. The repository this
     * calls documents the cap; this is the caller finally honouring it.
     */
    private long countWorkers(List<SesRequest> requests) {
        List<Long> ids = requests.stream().map(SesRequest::getRequestId).collect(Collectors.toList());
        long total = 0;
        for (List<Long> chunk : InChunks.of(ids)) {
            total += requestWorkerRepository.countByRequestIdIn(chunk);
        }
        return total;
    }

    private long countStatus(List<SesRequest> requests, List<SesStickerRequest> stickers, String status) {
        long a = requests.stream().filter(r -> status.equals(r.getStatus())).count();
        long b = stickers.stream().filter(r -> status.equals(r.getStatus())).count();
        return a + b;
    }

    /**
     * Submissions that have been sent in and not yet decided.
     *
     * <p>Two statuses, not one. {@code WAITING_FOR_UPLOAD} is a badge request
     * whose documents have not arrived from the contractor yet — undecided,
     * exactly like {@code PENDING}, just blocked on a different party. Counting
     * only {@code PENDING} would have left those submissions in the total but
     * in none of the three status tiles, so the tiles would quietly stop adding
     * up and nobody would know where the difference went.</p>
     *
     * <p>Stickers have no such status; the filter simply never matches them.</p>
     */
    private long countInFlight(List<SesRequest> requests, List<SesStickerRequest> stickers) {
        return countStatus(requests, stickers, "PENDING")
                + countStatus(requests, stickers, RequestStatus.WAITING_FOR_UPLOAD);
    }

    private long monthStatusCount(List<SesRequest> requests, List<SesStickerRequest> stickers, YearMonth m, String status) {
        long a = requests.stream().filter(r -> status.equals(r.getStatus())
                && r.getCreatedDate() != null && toYearMonth(r.getCreatedDate()).equals(m)).count();
        long b = stickers.stream().filter(r -> status.equals(r.getStatus())
                && r.getCreatedDate() != null && toYearMonth(r.getCreatedDate()).equals(m)).count();
        return a + b;
    }

    private void addIfPresent(Set<String> set, String value) {
        if (value != null && !value.trim().isEmpty()) {
            set.add(value);
        }
    }

    private YearMonth toYearMonth(Date date) {
        return YearMonth.from(date.toInstant().atZone(ZoneId.systemDefault()));
    }

    private List<YearMonth> lastNMonths(int n) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = n - 1; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }
        return months;
    }

    @Getter
    @lombok.AllArgsConstructor
    public static class Summary {
        private final long totalRequests;
        private final long pending;
        private final long approved;
        private final long rejected;
        private final long verifyingPayments;
        private final long totalStickerRequests;
        private final long distinctRequestors;
        private final long cashInflowThisMonth;
        /**
         * The cash position, admin view only. Same three figures and the same
         * definitions as /admin/cash-expense — read through
         * {@link com.badaklng.cash.service.CashExpenseService}, not recomputed,
         * so the dashboard and that screen can never disagree.
         */
        private final long cashIn;
        private final long cashOut;
        private final long cashBalance;
        /** Total baris pekerja (SES_REQUEST_WORKER) di seluruh pengajuan dalam scope ini — tanpa dedupe. */
        private final long totalWorkers;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TrendChart {
        private final int width;
        private final int height;
        private final List<String> labels;
        private final List<SeriesPoint> requestSeries;
        private final List<SeriesPoint> stickerSeries;
        private final String requestPolyline;
        private final String stickerPolyline;
        private final String requestSmoothPath;
        private final String stickerSmoothPath;
        private final String requestAreaPath;
        private final String stickerAreaPath;
        private final List<Integer> gridLines;
    }

    @Getter
    @RequiredArgsConstructor
    public static class SeriesPoint {
        private final int x;
        private final int y;
        private final long value;
        private final String label;
    }

    @Getter
    @RequiredArgsConstructor
    public static class CompositionPoint {
        private final String label;
        private final long pending;
        private final long approved;
        private final long rejected;
        /** Segment heights as percent of the container (0-100) — sum to this month's total as a fraction of the window's busiest month. */
        private final double pendingPct;
        private final double approvedPct;
        private final double rejectedPct;
    }

    @Getter
    @RequiredArgsConstructor
    public static class CashChart {
        private final int width;
        private final int height;
        private final List<String> labels;
        private final List<SeriesPoint> series;
        private final String polyline;
        private final String smoothPath;
        private final String areaPath;
        private final List<Integer> gridLines;
    }
}
