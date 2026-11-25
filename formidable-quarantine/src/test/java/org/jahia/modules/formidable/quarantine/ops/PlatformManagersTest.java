package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The rule of the check on the live site node: a site, then a server administrator or the permission on it. */
class PlatformManagersTest {

    private static JCRNodeWrapper node(boolean site, boolean permission) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.isNodeType("jnt:virtualsite")).thenReturn(site);
        when(node.hasPermission(Managers.PERMISSION)).thenReturn(permission);
        return node;
    }

    @Test
    void aHolderOfThePermissionOnTheSiteManagesIt() throws Exception {
        assertTrue(PlatformManagers.mayManage(node(true, true), false));
        assertFalse(PlatformManagers.mayManage(node(true, false), false), "no permission");
    }

    @Test
    void aServerAdministratorManagesASiteThatExistsAndNothingElse() throws Exception {
        assertTrue(PlatformManagers.mayManage(node(true, false), true));
        assertFalse(PlatformManagers.mayManage(node(false, true), true), "a node that is not a site");
    }
}
