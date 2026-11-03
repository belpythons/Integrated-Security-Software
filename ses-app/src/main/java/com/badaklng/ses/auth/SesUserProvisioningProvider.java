package com.badaklng.ses.auth;

import com.badaklng.platform.auth.AppUserDetailsService;
import com.badaklng.platform.system.entity.AppRole;
import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.entity.AppUserRole;
import com.badaklng.platform.system.entity.AppUserRoleId;
import com.badaklng.platform.system.repository.AppRoleRepository;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.platform.system.repository.AppUserRoleRepository;
import com.badaklng.request.entity.AppEmpInfoView;
import com.badaklng.request.repository.AppEmpInfoViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.stereotype.Component;

/**
 * Authenticates SES LDAP users (fallback o=users -> o=jkt) and auto-provisions
 * app_user with the default REQUESTOR role, mirroring
 * com.badaklng.kpi.service.KpiUserProvisioningProvider in the KPI project
 * (see docs/ldap-kpi-alignment-plan.md). LOCAL users are left to
 * com.badaklng.platform.auth.CustomAuthenticationProvider.
 */
@Component
public class SesUserProvisioningProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(SesUserProvisioningProvider.class);
    private static final String GENERIC_ERROR = "Username or password is not valid";

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final AppUserRoleRepository userRoleRepository;
    private final AppUserDetailsService userDetailsService;
    private final AppEmpInfoViewRepository employeeInfoRepository;

    @Autowired(required = false)
    @Qualifier("primaryLdapAuthenticator")
    private LdapAuthenticator primaryLdapAuthenticator;

    @Autowired(required = false)
    @Qualifier("secondaryLdapAuthenticator")
    private LdapAuthenticator secondaryLdapAuthenticator;

    @Value("${app.ldap.enabled:false}")
    private boolean ldapEnabled;

    @Value("${app.ldap.identity-attribute:}")
    private String identityAttribute;

    @Value("${app.ldap.name-attributes:displayName,cn}")
    private String nameAttributes;

    @Value("${app.ldap.email-attribute:mail}")
    private String emailAttribute;

    @Value("${app.ldap.auto-provision:false}")
    private boolean autoProvision;

    @Value("${app.ldap.default-role:REQUESTOR}")
    private String defaultRole;

    public SesUserProvisioningProvider(AppUserRepository userRepository,
                                         AppRoleRepository roleRepository,
                                         AppUserRoleRepository userRoleRepository,
                                         AppUserDetailsService userDetailsService,
                                         AppEmpInfoViewRepository employeeInfoRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userDetailsService = userDetailsService;
        this.employeeInfoRepository = employeeInfoRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!ldapEnabled || primaryLdapAuthenticator == null
                || identityAttribute == null || identityAttribute.trim().isEmpty()) {
            return null;
        }

        String typed = authentication.getName() == null ? null : authentication.getName().trim();
        Object credentials = authentication.getCredentials();
        if (typed == null || typed.isEmpty() || credentials == null
                || credentials.toString().isEmpty()) {
            return null;
        }

        AppUser typedUser = userRepository.findByUsername(typed).orElse(null);
        if (typedUser != null && !"LDAP".equalsIgnoreCase(typedUser.getAuthType())) {
            return null;
        }
        if (typedUser != null) {
            log.info("LDAP login must use the directory username, not badge {}", typed);
        }

        DirContextOperations ldapUser = authenticateLdap(typed, credentials.toString());
        String badge = attribute(ldapUser, identityAttribute);
        if (badge == null) {
            log.warn("LDAP user '{}' has no {} attribute", typed, identityAttribute);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        String email = attribute(ldapUser, emailAttribute);
        String employeeName = employeeInfoRepository.findByEmplid(badge)
                .map(AppEmpInfoView::getNameDisplay)
                .filter(SesUserProvisioningProvider::hasText)
                .orElse(null);
        String ldapDisplayName = attribute(ldapUser, "displayName");
        String fallbackName = firstNonNull(ldapUser, nameAttributes.split(","));
        String fullName = hasText(employeeName) ? employeeName
                : hasText(ldapDisplayName) ? ldapDisplayName : fallbackName;
        boolean preferredNameAvailable = hasText(employeeName) || hasText(ldapDisplayName);
        AppUser user = ensureUser(badge, fullName, preferredNameAvailable, email, typed);
        if (!"Y".equals(user.getActiveFlag())) {
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());
        log.info("LDAP user '{}' authenticated as badge {}", typed, badge);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private DirContextOperations authenticateLdap(String username, String password) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(username, password);
        RuntimeException primaryFailure;
        try {
            return primaryLdapAuthenticator.authenticate(token);
        } catch (RuntimeException e) {
            primaryFailure = e;
            log.warn("LDAP o=users failed for '{}': {}", username, rootMessage(e));
        }

        if (secondaryLdapAuthenticator != null) {
            try {
                return secondaryLdapAuthenticator.authenticate(token);
            } catch (RuntimeException e) {
                log.warn("LDAP o=jkt failed for '{}': {}", username, rootMessage(e));
                BadCredentialsException failure = new BadCredentialsException(GENERIC_ERROR, e);
                failure.addSuppressed(primaryFailure);
                throw failure;
            }
        }

        throw new BadCredentialsException(GENERIC_ERROR, primaryFailure);
    }

    private AppUser ensureUser(String badge, String fullName, boolean preferredNameAvailable,
                               String email, String typed) {
        AppUser user = userRepository.findByUsername(badge).orElse(null);
        boolean created = false;
        if (user == null && !autoProvision) {
            log.warn("LDAP user '{}' resolved to badge {}, but APP_USER is not provisioned",
                    typed, badge);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        if (user == null) {
            user = new AppUser();
            user.setUsername(badge);
            user.setFullName(fullName == null ? badge : fullName);
            user.setEmail(email);
            user.setAuthType("LDAP");
            user.setActiveFlag("Y");
            user = userRepository.save(user);
            created = true;
        } else if (!"LDAP".equalsIgnoreCase(user.getAuthType())) {
            log.warn("LDAP badge {} collides with a LOCAL account", badge);
            throw new BadCredentialsException(GENERIC_ERROR);
        } else if (preferredNameAvailable && hasText(fullName)
                && !fullName.equals(user.getFullName())) {
            user.setFullName(fullName);
            user = userRepository.save(user);
            log.info("Updated APP_USER full name for LDAP badge {}", badge);
        }

        AppRole role = null;
        for (AppRole candidate : roleRepository.findByActiveFlag("Y")) {
            if (defaultRole.equals(candidate.getRoleName())) {
                role = candidate;
                break;
            }
        }

        if (role == null) {
            log.warn("Role '{}' is missing; badge {} will have no default menu", defaultRole, badge);
        } else if (!userRoleRepository.existsById(new AppUserRoleId(user.getUserId(), role.getRoleId()))) {
            AppUserRole link = new AppUserRole();
            link.setUser(user);
            link.setRole(role);
            userRoleRepository.save(link);
            log.info("Role {} assigned to badge {}", defaultRole, badge);
        }

        if (created) {
            log.info("Provisioned app_user badge {} for LDAP username '{}'", badge, typed);
        }
        return user;
    }

    private static String firstNonNull(DirContextOperations user, String[] names) {
        for (String name : names) {
            String value = attribute(user, name.trim());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String attribute(DirContextOperations user, String name) {
        if (user == null || name == null || name.isEmpty()) {
            return null;
        }
        String value = user.getStringAttribute(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
