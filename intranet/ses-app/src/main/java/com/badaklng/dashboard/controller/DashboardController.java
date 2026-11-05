package com.badaklng.dashboard.controller;

import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.model.ApprovalTaskForm;
import com.badaklng.approval.service.ApprovalService;
import com.badaklng.dashboard.service.DashboardService;
import com.badaklng.history.model.HistoryRow;
import com.badaklng.history.service.HistoryService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.ses.user.UserScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Cookie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dashboard — read-only, with data scoping and role-based cash isolation.
 *
 * <ul>
 *   <li><b>Admin/officer:</b> org-wide summary cards, trend charts, and newest submissions.</li>
 *   <li><b>User:</b> Scoped to their Section (if non-blank) or Department (if section blank)
 *       or personal data, with 2 interactive Chart.js graphs (Trend & Status Donut).</li>
 *   <li><b>Cash metrics:</b> Isolated exclusively to {@code ADMIN_KAS} role (and admin/sysadm).</li>
 * </ul>
 */
@Slf4j
@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    public static final String LIST_URL = "/dashboard";
    private static final int RECENT_COUNT = 5;

    private final PageAccessGuard pageAccessGuard;
    private final DashboardService dashboardService;
    private final HistoryService historyService;
    private final ApprovalService approvalService;
    private final UserScopeService userScopeService;
    private final SesRequestTypeRepository requestTypeRepository;

    @GetMapping
    public String index(Model model, HttpServletRequest request,
                        @RequestParam(value = "stay", required = false) Boolean stay) {
        if (!Boolean.TRUE.equals(stay) && request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("ses_active_module".equals(cookie.getName())) {
                    String activeMod = cookie.getValue();
                    if ("sticker".equalsIgnoreCase(activeMod)) {
                        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                        boolean canDashboard = auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream().anyMatch(a -> {
                            String r = a.getAuthority().toUpperCase();
                            return r.contains("ADMIN") || r.contains("SECURITY") || r.contains("SYSADM");
                        });
                        return canDashboard ? "redirect:/sticker/dashboard" : "redirect:/sticker";
                    } else if ("goods".equalsIgnoreCase(activeMod)) {
                        return "redirect:/goods-permit/dashboard";
                    }
                }
            }
        }
        model.addAttribute("pageTitle", pageAccessGuard.require(LIST_URL));

        boolean adminView = pageAccessGuard.hasAction(LIST_URL, "SUBMIT");
        model.addAttribute("adminView", adminView);

        boolean isCashAdmin = pageAccessGuard.hasAction("/admin/cash-expense", "READ");
        if (!isCashAdmin) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                // Set.of is Java 9+; this module compiles at 1.8
                Set<String> cashRoles = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                        "ADMIN_KAS", "ROLE_ADMIN_KAS",
                        "ADMIN", "ROLE_ADMIN",
                        "SYSADM", "ROLE_SYSADM"
                )));
                isCashAdmin = auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(cashRoles::contains);
            }
        }
        model.addAttribute("isCashAdmin", isCashAdmin);

        String username = SecurityUtils.getCurrentUsername();

        if (adminView) {
            // One read of the two business tables for all four cards/charts.
            // Each of these used to begin with its own findAll() pair.
            DashboardService.AdminAggregates agg = dashboardService.adminAggregates();
            model.addAttribute("summary", agg.getSummary());
            model.addAttribute("trend", agg.getTrend());
            model.addAttribute("composition", agg.getComposition());
            model.addAttribute("cashInflow", agg.getCashInflow());

            List<HistoryRow> recent;
            try {
                recent = historyService.pagedRows(null, null, null, null, null, true,
                        PageRequest.of(0, RECENT_COUNT, Sort.by(Sort.Direction.DESC, "createdDate"))).getContent();
            } catch (Exception ex) {
                log.warn("Dashboard recent history query failed gracefully (unscoped): {}", ex.getMessage());
                recent = Collections.emptyList();
            }
            model.addAttribute("recentRows", recent);
            model.addAttribute("scopeLabel", "Seluruh Organisasi");
        } else {
            UserScopeService.UserScopeResult scope = userScopeService.resolveScope(username);
            model.addAttribute("scope", scope);
            model.addAttribute("scopeLabel", scope.getScopeLabel());
            DashboardService.Snapshot snapshot = dashboardService.snapshot(scope.getUsernames());
            model.addAttribute("summary",
                    dashboardService.scopedSummary(snapshot, scope.getUsernames().size()));
            model.addAttribute("trend", dashboardService.monthlyTrend(snapshot));
            List<HistoryRow> recent;
            try {
                recent = historyService.pagedRows(null, null, null, scope.getUsernames(), null, false,
                        PageRequest.of(0, RECENT_COUNT, Sort.by(Sort.Direction.DESC, "createdDate"))).getContent();
            } catch (Exception ex) {
                log.warn("Dashboard recent history query failed gracefully for user {}: {}", username, ex.getMessage());
                recent = Collections.emptyList();
            }
            model.addAttribute("recentRows", recent);
        }

        String approver = SecurityUtils.getCurrentUsername();
        model.addAttribute("myApprovals",
                approvalService.toTaskForms(approvalService.findMyPendingApprovalsFromView(approver)));

        model.addAttribute("requestTypeNames", requestTypeRepository.findByActiveFlagOrderByOrderByAsc("Y").stream()
                .map(SesRequestType::getTypeName).collect(Collectors.toList()));

        return "dashboard/index";
    }
}
