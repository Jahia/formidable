package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The rule of the check: root and a member of the server group {@code administrators} pass; a site administrator,
 * whom the groups find only through a site key, and a request with no user do not.
 */
class ServerAdministratorsTest {

    private static final String REALM = "default";

    private final JahiaGroupManagerService groups = mock(JahiaGroupManagerService.class);

    @BeforeEach
    void setUp() {
        // the server group administrators holds "admin"; "siteAdmin" is a site administrator only
        when(groups.isAdminMember(eq("admin"), eq(REALM), isNull())).thenReturn(true);
        when(groups.isAdminMember(eq("siteAdmin"), eq(REALM), anyString())).thenReturn(true);
    }

    private static JahiaUser user(String name, boolean root) {
        JahiaUser user = mock(JahiaUser.class);
        when(user.getUsername()).thenReturn(name);
        when(user.getRealm()).thenReturn(REALM);
        when(user.isRoot()).thenReturn(root);
        return user;
    }

    @Test
    void rootIsOne() {
        assertTrue(ServerAdministrators.isOne(user("root", true), groups));
    }

    @Test
    void aMemberOfTheServerGroupAdministratorsWhoIsNotRootIsOne() {
        assertTrue(ServerAdministrators.isOne(user("admin", false), groups));
    }

    @Test
    void aSiteAdministratorIsNotOne() {
        assertFalse(ServerAdministrators.isOne(user("siteAdmin", false), groups));
    }

    @Test
    void aRequestWithNoUserIsNotOne() {
        assertFalse(ServerAdministrators.isOne(null, groups));
    }
}
