package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The check on the live site node: a site, then a server administrator or the permission on it. A site with no live
 * node, or one the user cannot read, gives no permission; any other failure of the repository is thrown.
 */
class PlatformManagersTest {

    private static final String SITE_PATH = "/sites/siteA";

    private static JCRNodeWrapper node(boolean site, boolean permission) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.isNodeType("jnt:virtualsite")).thenReturn(site);
        when(node.hasPermission(Managers.PERMISSION)).thenReturn(permission);
        return node;
    }

    private static JCRSessionWrapper sessionThatThrows(RepositoryException failure) throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode(SITE_PATH)).thenThrow(failure);
        return session;
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

    @Test
    void theNodeOfTheSiteIsReadInTheSessionOfTheUser() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper site = node(true, true);
        when(session.getNode(SITE_PATH)).thenReturn(site);

        assertTrue(PlatformManagers.mayManage(session, "siteA", false));
    }

    @Test
    void aSiteWithNoLiveNodeHasNoManager() throws Exception {
        assertFalse(PlatformManagers.mayManage(sessionThatThrows(new PathNotFoundException(SITE_PATH)), "siteA", true));
    }

    @Test
    void aSiteTheUserCannotReadHasTheUserForNoManager() throws Exception {
        assertFalse(PlatformManagers.mayManage(sessionThatThrows(new AccessDeniedException(SITE_PATH)), "siteA",
                false));
    }

    @Test
    void anyOtherFailureOfTheRepositoryIsThrownAndNeverReadAsNotAManager() throws Exception {
        JCRSessionWrapper session = sessionThatThrows(new RepositoryException("the repository is down"));

        assertThrows(IllegalStateException.class, () -> PlatformManagers.mayManage(session, "siteA", false));
    }
}
