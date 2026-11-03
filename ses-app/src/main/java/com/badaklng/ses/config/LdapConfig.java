package com.badaklng.ses.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides LdapTemplate when app.ldap.enabled=true.
 * Set this property to true and configure spring.ldap.* to activate LDAP authentication.
 */
@Configuration
@ConditionalOnProperty(name = "app.ldap.enabled", havingValue = "true", matchIfMissing = false)
public class LdapConfig {

    private static final Logger log = LoggerFactory.getLogger(LdapConfig.class);

    @Value("${spring.ldap.urls:ldap://localhost:389}")
    private String ldapUrls;

    @Value("${spring.ldap.base:}")
    private String ldapBase;

    @Value("${spring.ldap.username:}")
    private String ldapUsername;

    @Value("${spring.ldap.password:}")
    private String ldapPassword;

    @Value("${app.ldap.connect-timeout-ms:5000}")
    private String connectTimeoutMs;

    @Value("${app.ldap.read-timeout-ms:5000}")
    private String readTimeoutMs;

    @Value("${app.ldap.user-dn-pattern:cn={0},o=users}")
    private String primaryUserDnPattern;

    @Value("${app.ldap.secondary-user-dn-pattern:cn={0},o=jkt}")
    private String secondaryUserDnPattern;

    @Bean(name = { "ldapContextSource", "primaryContextSource" })
    @Primary
    public LdapContextSource ldapContextSource() {
        log.info("LDAP authentication enabled: URLs={}, primary DN={}, secondary DN={}",
                Arrays.toString(configuredUrls()), primaryUserDnPattern, secondaryUserDnPattern);
        return contextSource();
    }

    @Bean(name = "secondaryContextSource")
    public LdapContextSource secondaryContextSource() {
        return contextSource();
    }

    private LdapContextSource contextSource() {
        LdapContextSource ctx = new LdapContextSource();
        ctx.setUrls(configuredUrls());
        if (ldapBase != null && !ldapBase.trim().isEmpty()) {
            ctx.setBase(ldapBase.trim());
        }
        if (ldapUsername != null && !ldapUsername.trim().isEmpty()) {
            ctx.setUserDn(ldapUsername.trim());
            ctx.setPassword(ldapPassword);
        }
        Map<String, Object> env = new HashMap<>();
        env.put("com.sun.jndi.ldap.connect.timeout", connectTimeoutMs);
        env.put("com.sun.jndi.ldap.read.timeout", readTimeoutMs);
        ctx.setBaseEnvironmentProperties(env);
        return ctx;
    }

    private String[] configuredUrls() {
        return Arrays.stream(ldapUrls.split("[,\\s]+"))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toArray(String[]::new);
    }

    @Bean(name = "primaryLdapAuthenticator")
    public LdapAuthenticator primaryLdapAuthenticator(
            @Qualifier("primaryContextSource") LdapContextSource contextSource) {
        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserDnPatterns(new String[] { primaryUserDnPattern });
        return authenticator;
    }

    @Bean(name = "secondaryLdapAuthenticator")
    public LdapAuthenticator secondaryLdapAuthenticator(
            @Qualifier("secondaryContextSource") LdapContextSource contextSource) {
        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserDnPatterns(new String[] { secondaryUserDnPattern });
        return authenticator;
    }

    @Bean
    public LdapTemplate ldapTemplate() {
        return new LdapTemplate(ldapContextSource());
    }
}
