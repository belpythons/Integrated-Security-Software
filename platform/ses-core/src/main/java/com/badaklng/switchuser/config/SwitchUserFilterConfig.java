package com.badaklng.switchuser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;

/**
 * Wires Spring Security's built-in impersonation filter around platform's
 * existing AppUserDetailsService bean. No platform/ changes needed — this
 * filter only needs a UserDetailsService, which platform already exposes.
 * Access to the switch/exit URLs is restricted in SecurityConfig, not here.
 */
@Configuration
public class SwitchUserFilterConfig {

    @Bean
    public SwitchUserFilter switchUserFilter(UserDetailsService userDetailsService) {
        SwitchUserFilter filter = new SwitchUserFilter();
        filter.setUserDetailsService(userDetailsService);
        filter.setSwitchUserUrl("/switchuser/impersonate");
        filter.setExitUserUrl("/switchuser/exit");
        filter.setTargetUrl("/dashboard");
        filter.setSwitchFailureUrl("/switchuser?error");
        return filter;
    }
}
