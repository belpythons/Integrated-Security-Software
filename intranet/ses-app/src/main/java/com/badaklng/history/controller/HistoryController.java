package com.badaklng.history.controller;

import com.badaklng.history.service.HistoryDocumentService;
import com.badaklng.history.service.HistoryService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.ses.user.UserScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.badaklng.history.model.HistoryRow;
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Riwayat / History — with hierarchical data scoping.
 *
 * <ul>
 *   <li><b>Admin/officer:</b> every request that has come in across all modules.</li>
 *   <li><b>User:</b> Scoped to Section (if present), Department (if section null),
 *       or personal submissions.</li>
 * </ul>
 */
@Controller
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    public static final String LIST_URL = "/history";

    /**
     * Default ceiling on rows in the printable PDF.
     *
     * <p>500 rows is already a long document; the point is that there IS a
     * ceiling, not the exact number.
     */
    private static final int PRINT_ROW_CAP = 500;

    private final PageAccessGuard pageAccessGuard;
    private final HistoryService historyService;
    private final UserScopeService userScopeService;
    private final SesRequestTypeRepository requestTypeRepository;
    private final HistoryDocumentService historyDocumentService;
    private final com.badaklng.approval.service.ApprovalService approvalService;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String module,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", pageAccessGuard.require(LIST_URL));

        boolean adminView = pageAccessGuard.hasAction(LIST_URL, "SUBMIT");
        model.addAttribute("adminView", adminView);

        // Paged, filtered and ordered in SQL over SES_HISTORY_V. The scoping
        // rule is the same one scopedRowsSorted applied in memory, moved into
        // the query -- see SesHistoryViewSpecifications.scoped.
        Pageable pageable = PageRequests.of(page, size, "createdDate");
        String term = PageRequests.term(q);
        String statusFilter = PageRequests.term(status);
        String moduleFilter = PageRequests.term(module);
        Page<HistoryRow> paged;
        if (adminView) {
            paged = historyService.pagedRows(term, statusFilter, moduleFilter, null, null, true, pageable);
            model.addAttribute("scopeLabel", "Seluruh Organisasi");
        } else {
            String username = SecurityUtils.getCurrentUsername();
            UserScopeService.UserScopeResult scope = userScopeService.resolveScope(username);
            java.util.Set<Long> involved = approvalService.findInvolvedInstanceIds(username);
            model.addAttribute("scope", scope);
            model.addAttribute("scopeLabel", scope.getScopeLabel());
            paged = historyService.pagedRows(term, statusFilter, moduleFilter,
                    scope.getUsernames(), involved, false, pageable);
        }
        model.addAttribute("rows", paged.getContent());
        model.addAttribute("page", paged);


        model.addAttribute("requestTypeNames", requestTypeRepository.findByActiveFlagOrderByOrderByAsc("Y").stream()
                .map(SesRequestType::getTypeName).collect(Collectors.toList()));
        // Printing the full list only makes sense for a reader who can already
        // SEE the full list — the same SUBMIT grant that drives adminView above.
        // No separate "PRINT" action code: the admin Role screen's Pages &
        // Actions grid only offers READ/CREATE/UPDATE/DELETE/ADD/REMOVE/SUBMIT/
        // ALL, so a custom code could never be granted through it.
        model.addAttribute("canPrint", adminView);

        return "history/list";
    }

    /** Full (unscoped) list as a printable PDF — same grant as the full (adminView) list itself. */
    @GetMapping(value = "/print.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> printPdf(@RequestParam(required = false) Integer limit) {
        pageAccessGuard.requireAction(LIST_URL, "SUBMIT");
        // Capped. This renders one <tr> per submission in the whole system
        // into a single PDF, in the request thread, entirely in heap -- with no
        // ceiling it is one table growth away from being the slowest and
        // largest response the application can produce. Raise it with ?limit=
        // when a genuinely complete export is wanted and someone is waiting
        // for it on purpose.
        int cap = limit == null || limit < 1 ? PRINT_ROW_CAP : limit;
        List<HistoryRow> all = historyService.allRowsSorted();
        List<HistoryRow> rows = all.size() > cap ? all.subList(0, cap) : all;
        byte[] pdf = historyDocumentService.renderListPdf(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"riwayat-pengajuan.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
