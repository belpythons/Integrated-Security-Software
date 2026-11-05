package com.badaklng.ses.config;

import com.badaklng.platform.auth.CustomAuthenticationProvider;
import com.badaklng.ses.auth.SesUserProvisioningProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;

import java.util.Optional;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private SesUserProvisioningProvider sesUserProvisioningProvider;

    @Autowired
    private CustomAuthenticationProvider authenticationProvider;

    // Optional: com.badaklng.switchuser is a pluggable module, may not be on the
    // classpath/scan path at all — Spring injects Optional.empty() rather than
    // failing context startup when no SwitchUserFilter bean exists.
    @Autowired
    private Optional<SwitchUserFilter> switchUserFilter;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(sesUserProvisioningProvider);
        auth.authenticationProvider(authenticationProvider);
    }

    @Override
    public void configure(org.springframework.security.config.annotation.web.builders.WebSecurity web) {
        web.ignoring().antMatchers(
                "/css/**",
                "/js/**",
                "/image/**",
                "/fonts/**",
                "/vendor/**",
                "/webjars/**",
                "/favicon.ico"
        );
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/login", "/css/**", "/js/**", "/image/**", "/fonts/**", "/vendor/**", "/webjars/**", "/favicon.ico")
                .permitAll()
                .antMatchers("/actuator/health", "/actuator/info").permitAll()
                // Note: /request/upload-docs/** and /verify/** live exclusively in the ses-ext module.
                .antMatchers("/actuator/**").hasRole("SYSADM")
                .antMatchers("/switchuser/exit").hasRole("PREVIOUS_ADMINISTRATOR")
                .antMatchers("/switchuser/**").access("@pageAccessGuard.hasAction('/switchuser', 'READ')")
                .antMatchers("/admin/**").hasAnyRole("ADMIN", "SYSADM")
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
                .and()
                .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
                .and()
                .sessionManagement()
                // TEMP: bumped from 1 to 10 (2026-08-21) — revert to 1 later, see project memory
                .maximumSessions(10)
                .maxSessionsPreventsLogin(false);

        switchUserFilter.ifPresent(filter -> http.addFilterAfter(filter, FilterSecurityInterceptor.class));
    }
}
