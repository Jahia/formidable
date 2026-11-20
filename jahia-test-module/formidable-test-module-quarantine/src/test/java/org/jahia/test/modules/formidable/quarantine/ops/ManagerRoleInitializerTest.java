package org.jahia.test.modules.formidable.quarantine.ops;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The role of a manager at activation: created with the permission, or given it, keeping its other permissions. */
class ManagerRoleInitializerTest {

    private JCRSessionWrapper session;

    @BeforeEach
    void setUp() {
        session = mock(JCRSessionWrapper.class);
    }

    private JCRNodeWrapper existingRole(String... permissions) throws RepositoryException {
        JCRNodeWrapper role = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper names = mock(JCRPropertyWrapper.class);
        JCRValueWrapper[] values = new JCRValueWrapper[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            values[i] = mock(JCRValueWrapper.class);
            when(values[i].getString()).thenReturn(permissions[i]);
        }
        when(names.getValues()).thenReturn(values);
        when(role.hasProperty(ManagerRoleInitializer.PERMISSION_NAMES)).thenReturn(true);
        when(role.getProperty(ManagerRoleInitializer.PERMISSION_NAMES)).thenReturn(names);
        when(session.nodeExists(ManagerRoleInitializer.ROLE_PATH)).thenReturn(true);
        when(session.getNode(ManagerRoleInitializer.ROLE_PATH)).thenReturn(role);
        return role;
    }

    @Test
    void aRoleThatLacksThePermissionGetsItAndKeepsTheOthers() throws Exception {
        JCRNodeWrapper role = existingRole("jcr:read_live");

        ManagerRoleInitializer.ensureRole(session);

        verify(session).checkout(role);
        verify(role).setProperty(ManagerRoleInitializer.PERMISSION_NAMES,
                new String[]{"jcr:read_live", Managers.PERMISSION});
        verify(session).save();
    }

    @Test
    void aRoleThatHoldsThePermissionIsLeftAsItIs() throws Exception {
        JCRNodeWrapper role = existingRole("jcr:read_live", Managers.PERMISSION);

        ManagerRoleInitializer.ensureRole(session);

        verify(role, never()).setProperty(eq(ManagerRoleInitializer.PERMISSION_NAMES), any(String[].class));
        verify(session, never()).save();
    }

    @Test
    void aMissingRoleIsCreatedWithThePermission() throws Exception {
        JCRNodeWrapper roles = mock(JCRNodeWrapper.class);
        JCRNodeWrapper role = mock(JCRNodeWrapper.class);
        JCRNodeWrapper translation = mock(JCRNodeWrapper.class);
        when(session.getNode("/roles")).thenReturn(roles);
        when(roles.addNode(Managers.ROLE, "jnt:role")).thenReturn(role);
        when(role.getOrCreateI18N(any(Locale.class))).thenReturn(translation);

        ManagerRoleInitializer.ensureRole(session);

        verify(session).checkout(roles);
        verify(role).setProperty(ManagerRoleInitializer.PERMISSION_NAMES, new String[]{Managers.PERMISSION});
        verify(translation, times(2)).setProperty(eq("jcr:title"), anyString());
        verify(session).save();
    }
}
