package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The role of a manager at activation: a site role created with the permission, or given it and made a site role,
 * keeping its other permissions.
 */
class ManagerRoleInitializerTest {

    private JCRSessionWrapper session;

    @BeforeEach
    void setUp() {
        session = mock(JCRSessionWrapper.class);
    }

    private JCRNodeWrapper existingRole(String roleGroup, String... permissions) throws RepositoryException {
        JCRNodeWrapper role = mock(JCRNodeWrapper.class);
        stubStrings(role, ManagerRoleInitializer.PERMISSION_NAMES, permissions);
        stubStrings(role, ManagerRoleInitializer.NODE_TYPES, ManagerRoleInitializer.SITE_ROLE.equals(roleGroup)
                ? ManagerRoleInitializer.SITE_TYPE : "jnt:content");
        JCRPropertyWrapper group = mock(JCRPropertyWrapper.class);
        when(group.getString()).thenReturn(roleGroup);
        when(role.hasProperty(ManagerRoleInitializer.ROLE_GROUP)).thenReturn(true);
        when(role.getProperty(ManagerRoleInitializer.ROLE_GROUP)).thenReturn(group);
        JCRPropertyWrapper privileged = mock(JCRPropertyWrapper.class);
        when(privileged.getBoolean()).thenReturn(ManagerRoleInitializer.SITE_ROLE.equals(roleGroup));
        when(role.hasProperty(ManagerRoleInitializer.PRIVILEGED_ACCESS)).thenReturn(true);
        when(role.getProperty(ManagerRoleInitializer.PRIVILEGED_ACCESS)).thenReturn(privileged);
        when(session.nodeExists(ManagerRoleInitializer.ROLE_PATH)).thenReturn(true);
        when(session.getNode(ManagerRoleInitializer.ROLE_PATH)).thenReturn(role);
        return role;
    }

    private static void stubStrings(JCRNodeWrapper node, String name, String... strings) throws RepositoryException {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        JCRValueWrapper[] values = new JCRValueWrapper[strings.length];
        for (int i = 0; i < strings.length; i++) {
            values[i] = mock(JCRValueWrapper.class);
            when(values[i].getString()).thenReturn(strings[i]);
        }
        when(property.getValues()).thenReturn(values);
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
    }

    @Test
    void aRoleThatLacksThePermissionGetsItAndKeepsTheOthers() throws Exception {
        JCRNodeWrapper role = existingRole(ManagerRoleInitializer.SITE_ROLE, "jcr:read_live");

        ManagerRoleInitializer.ensureRole(session);

        verify(session).checkout(role);
        verify(role).setProperty(ManagerRoleInitializer.PERMISSION_NAMES,
                new String[]{"jcr:read_live", Managers.PERMISSION});
        verify(session).save();
    }

    @Test
    void aSiteRoleThatHoldsThePermissionIsLeftAsItIs() throws Exception {
        JCRNodeWrapper role = existingRole(ManagerRoleInitializer.SITE_ROLE, "jcr:read_live", Managers.PERMISSION);

        ManagerRoleInitializer.ensureRole(session);

        verify(role, never()).setProperty(eq(ManagerRoleInitializer.PERMISSION_NAMES), any(String[].class));
        verify(role, never()).setProperty(eq(ManagerRoleInitializer.ROLE_GROUP), anyString());
        verify(session, never()).save();
    }

    @Test
    void theRoleOfAFormOfAnEarlierVersionBecomesASiteRole() throws Exception {
        JCRNodeWrapper role = existingRole("edit-role", Managers.PERMISSION);

        ManagerRoleInitializer.ensureRole(session);

        verify(role).setProperty(ManagerRoleInitializer.ROLE_GROUP, ManagerRoleInitializer.SITE_ROLE);
        verify(role).setProperty(ManagerRoleInitializer.NODE_TYPES, new String[]{ManagerRoleInitializer.SITE_TYPE});
        verify(role).setProperty(ManagerRoleInitializer.PRIVILEGED_ACCESS, true);
        verify(role, never()).setProperty(eq(ManagerRoleInitializer.PERMISSION_NAMES), any(String[].class));
        verify(session).save();
    }

    @Test
    void aMissingRoleIsCreatedAsASiteRoleWithThePermission() throws Exception {
        JCRNodeWrapper roles = mock(JCRNodeWrapper.class);
        JCRNodeWrapper role = mock(JCRNodeWrapper.class);
        JCRNodeWrapper translation = mock(JCRNodeWrapper.class);
        when(session.getNode("/roles")).thenReturn(roles);
        when(roles.addNode(Managers.ROLE, "jnt:role")).thenReturn(role);
        when(role.getOrCreateI18N(any(Locale.class))).thenReturn(translation);

        ManagerRoleInitializer.ensureRole(session);

        verify(session).checkout(roles);
        verify(role).setProperty(ManagerRoleInitializer.PERMISSION_NAMES, new String[]{Managers.PERMISSION});
        verify(role).setProperty(ManagerRoleInitializer.ROLE_GROUP, ManagerRoleInitializer.SITE_ROLE);
        verify(role).setProperty(ManagerRoleInitializer.NODE_TYPES, new String[]{ManagerRoleInitializer.SITE_TYPE});
        verify(role).setProperty(eq(ManagerRoleInitializer.PRIVILEGED_ACCESS), anyBoolean());
        // en, fr, de, es
        verify(translation, times(4)).setProperty(eq("jcr:title"), anyString());
        verify(session).save();
    }
}
