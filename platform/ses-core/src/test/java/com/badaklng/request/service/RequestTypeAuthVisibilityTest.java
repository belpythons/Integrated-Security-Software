package com.badaklng.request.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RequestTypeService#visibleTo} and {@link RequestTypeService#normalizeAuthType}.
 *
 * <p>Plain JUnit, no Spring: both are {@code static} and dependency-free
 * precisely so this is possible. The rule decides which request types an
 * account can even see, so it is access control, and access control that can
 * only be exercised against a live ELLDEV does not get exercised.</p>
 */
class RequestTypeAuthVisibilityTest {

    @Test
    @DisplayName("ALL is visible to every account, whatever their auth type")
    void allIsVisibleToEveryone() {
        assertTrue(RequestTypeService.visibleTo("ALL", "LOCAL"));
        assertTrue(RequestTypeService.visibleTo("ALL", "LDAP"));
        assertTrue(RequestTypeService.visibleTo("ALL", null));
        assertTrue(RequestTypeService.visibleTo("ALL", ""));
        assertTrue(RequestTypeService.visibleTo("ALL", "SOMETHING_ELSE"));
    }

    @Test
    @DisplayName("A row written before auth_type existed reads as ALL, not as hidden")
    void nullOrBlankTypeAuthMeansAll() {
        // The column defaults to 'ALL', but a row loaded from a schema that
        // predates the ALTER can carry null. Treating that as "restricted"
        // would make every existing request type vanish from every dropdown.
        assertTrue(RequestTypeService.visibleTo(null, "LOCAL"));
        assertTrue(RequestTypeService.visibleTo("", "LDAP"));
        assertTrue(RequestTypeService.visibleTo("   ", "LDAP"));
    }

    @Test
    @DisplayName("LOCAL and LDAP each see only their own, case-insensitively")
    void exactMatchWins() {
        assertTrue(RequestTypeService.visibleTo("LOCAL", "LOCAL"));
        assertTrue(RequestTypeService.visibleTo("LOCAL", "local"));
        assertTrue(RequestTypeService.visibleTo("LDAP", " ldap "));
        assertFalse(RequestTypeService.visibleTo("LOCAL", "LDAP"));
        assertFalse(RequestTypeService.visibleTo("LDAP", "LOCAL"));
    }

    @Test
    @DisplayName("An account with no auth type sees nothing restricted — fail CLOSED")
    void unknownUserAuthTypeFailsClosed() {
        // Every real account has an auth type: SesUserProvisioningProvider
        // writes LDAP on JIT provisioning, the platform's local path writes
        // LOCAL, and sql/14 forces LOCAL in dev. A null is therefore an
        // anomaly, and the safe reading of an anomaly is to grant the
        // UNRESTRICTED set, never the restricted one.
        assertFalse(RequestTypeService.visibleTo("LOCAL", null));
        assertFalse(RequestTypeService.visibleTo("LDAP", ""));
        assertFalse(RequestTypeService.visibleTo("LOCAL", "   "));
        assertFalse(RequestTypeService.visibleTo("LDAP", "GARBAGE"));

        // ...but they keep every ALL type, which before this feature shipped
        // was every type that existed.
        assertTrue(RequestTypeService.visibleTo("ALL", null));
    }

    @Test
    @DisplayName("normalizeAuthType collapses anything unrecognised to ALL")
    void normalizeCollapsesToAll() {
        assertEquals("ALL", RequestTypeService.normalizeAuthType(null));
        assertEquals("ALL", RequestTypeService.normalizeAuthType(""));
        assertEquals("ALL", RequestTypeService.normalizeAuthType("nonsense"));
        assertEquals("ALL", RequestTypeService.normalizeAuthType("ALL"));
        assertEquals("LOCAL", RequestTypeService.normalizeAuthType(" local "));
        assertEquals("LDAP", RequestTypeService.normalizeAuthType("ldap"));
    }

    @Test
    @DisplayName("A normalised value is always one visibleTo understands")
    void normalizeAndVisibleToAgree() {
        // The two are used together — toEntity() normalises on the way in,
        // visibleTo reads on the way out — so a value that survives the first
        // must be meaningful to the second.
        for (String raw : new String[]{null, "", "ALL", "local", "LDAP", "rubbish"}) {
            String stored = RequestTypeService.normalizeAuthType(raw);
            assertTrue(RequestTypeService.visibleTo(stored, stored),
                    "a type should always be visible to an account of its own auth type: " + stored);
        }
    }
}
