package com.badaklng.goods.controller;

import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.Collections;

/**
 * Controller for the Goods Entry Permit (Izin Masuk Barang) portal.
 * Integrates as a first-class module in SES with Strict Role Binding.
 */
@Slf4j
@Controller
@RequestMapping("/goods-permit")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSADM', 'SECURITY', 'REQUESTOR', 'VIEWER')")
@RequiredArgsConstructor
public class GoodsPermitController {

    public static final String LIST_URL = "/goods-permit";

    private final PageAccessGuard pageAccessGuard;
    private final UserDisplayNameService userDisplayNameService;

    @GetMapping({"", "/", "/dashboard"})
    public String index(Model model, @RequestParam(value = "view", required = false) String initialView) {
        model.addAttribute("pageTitle", pageAccessGuard.require(LIST_URL));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        Collection<? extends GrantedAuthority> authorities = auth != null ? auth.getAuthorities() : Collections.emptyList();

        boolean isAdmin = authorities.stream().anyMatch(a -> {
            String role = a.getAuthority().toUpperCase();
            return role.contains("ADMIN") || role.contains("SECURITY");
        }) || "SES2026".equalsIgnoreCase(username) || "sysadm".equalsIgnoreCase(username);

        boolean isApprover = authorities.stream().anyMatch(a -> {
            String role = a.getAuthority().toUpperCase();
            return role.contains("APPROVER") || role.contains("EVALUATOR") || role.contains("MANAGER") || role.contains("SUPERVISOR");
        });

        String activeRole;
        String roleLabel;
        if (isAdmin) {
            activeRole = "admin";
            roleLabel = "Security Administrator";
        } else if (isApprover) {
            activeRole = "appr:PRC-MGR-01";
            roleLabel = "Approver · Procurement Manager";
        } else {
            activeRole = "vendor";
            roleLabel = "Vendor / Requestor";
        }

        String displayName = userDisplayNameService.displayName(username);
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = username;
        }

        model.addAttribute("parentPageTitle", "Security Suite");
        model.addAttribute("parentPageUrl", "/dashboard");
        model.addAttribute("activeRole", activeRole);
        model.addAttribute("roleLabel", roleLabel);
        model.addAttribute("currentUsername", username);
        model.addAttribute("currentDisplayName", displayName);
        model.addAttribute("initialView", initialView != null ? initialView : "home");

        log.debug("User {} entering GoodsPermit with role={}, label={}", username, activeRole, roleLabel);
        return "goods/index";
    }
}
