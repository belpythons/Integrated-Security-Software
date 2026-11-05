package com.badaklng.switchuser.controller;

import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.switchuser.model.SwitchUserDto;
import com.badaklng.switchuser.service.SwitchUserService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Impersonation target picker. The actual switch/exit is handled by Spring
 * Security's SwitchUserFilter (see SwitchUserFilterConfig) intercepting
 * POST /switchuser/impersonate and /switchuser/exit directly — this
 * controller only renders the user list.
 *
 * Access is gated by the "/switchuser" app_page's READ action grant
 * (app_role_page), same as SecurityConfig's antMatcher for
 * "/switchuser/**" — both read the same grant so a role can be added or
 * removed without a redeploy. See ses/sql/06_admin_rbac_seed.sql.
 */
@Controller
@RequestMapping("/switchuser")
@RequiredArgsConstructor
public class SwitchUserController {

    private final SwitchUserService switchUserService;
    private final PageAccessGuard pageAccessGuard;

    @GetMapping
    public String picker(@RequestParam(required = false) String error, Model model) {
        pageAccessGuard.requireAction("/switchuser", "READ");
        String currentUsername = SecurityUtils.getCurrentUsername();
        List<SwitchUserDto> users = switchUserService.getActiveSwitchableUsers().stream()
                .filter(u -> !u.getUsername().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
        model.addAttribute("pageTitle", "Switch User");
        model.addAttribute("users", users);
        if (error != null) {
            model.addAttribute("errorMsg", "Could not switch to that user.");
        }
        return "switchuser/picker";
    }
}
