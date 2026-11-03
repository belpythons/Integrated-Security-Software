package com.badaklng.ses.ext.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Public DMZ Gateway Security Configuration.
 *
 * <p>Exposes only contractor document uploads and public QR verification.
 * No LDAP authentication, no login form, no session management attack vectors.</p>
 */
@Configuration
@EnableWebSecurity
public class SesExtSecurityConfig extends WebSecurityConfigurerAdapter {

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
                .antMatchers(
                        "/request/upload-docs",
                        "/request/upload-docs/**",
                        "/verify",
                        "/verify/**",
                        "/css/**",
                        "/js/**",
                        "/image/**",
                        "/fonts/**",
                        "/vendor/**",
                        "/webjars/**",
                        "/favicon.ico"
                ).permitAll()
                .antMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().denyAll();
    }
}
