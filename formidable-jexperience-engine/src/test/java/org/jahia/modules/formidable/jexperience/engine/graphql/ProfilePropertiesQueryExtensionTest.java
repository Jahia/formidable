package org.jahia.modules.formidable.jexperience.engine.graphql;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertiesUnavailableException;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertyCatalog;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertyDescriptor;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.Test;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfilePropertiesQueryExtensionTest {

    private static final String PATH = "/sites/site/contents/form/field";

    private static JCRSessionWrapper sessionWith(boolean canEdit) throws Exception {
        return sessionWith(PATH, "site", true, canEdit);
    }

    /** A session that reads {@code path}: a field whose parent is a form root when {@code inForm}, of the site {@code siteKey} (null: none). */
    private static JCRSessionWrapper sessionWith(String path, String siteKey, boolean inForm, boolean canEdit) throws Exception {
        JCRSiteNode site = siteKey == null ? null : mock(JCRSiteNode.class);
        if (site != null) {
            when(site.getSiteKey()).thenReturn(siteKey);
        }
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.isNodeType(FmdbMixin.FORM_ROOT)).thenReturn(inForm);
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getPath()).thenReturn(path);
        when(node.getResolveSite()).thenReturn(site);
        when(node.getDepth()).thenReturn(1);
        when(node.getParent()).thenReturn(form);
        when(node.hasPermission(ProfilePropertiesQueryExtension.REQUIRED_PERMISSION)).thenReturn(canEdit);
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode(path)).thenReturn(node);
        return session;
    }

    @Test
    void listsTheReadablePropertiesOfTheSiteOfTheNode() throws Exception {
        // Verifies that the field answers the catalog's read-side list for the site the node belongs to.
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.readableProfileProperties("site")).thenReturn(List.of(new ProfilePropertyDescriptor("country", "Country (country)", "string", false)));
        GqlProfileProperties answer = ProfilePropertiesQueryExtension.list(sessionWith(true), catalog, PATH);
        assertTrue(answer.isAvailable());
        assertEquals(List.of("country"), answer.getProperties().stream().map(GqlProfileProperty::getName).toList());
        assertEquals("Country (country)", answer.getProperties().get(0).getLabel());
    }

    @Test
    void anUnreachableJCustomerIsUnavailableNotAnEmptySchema() throws Exception {
        // Verifies that an outage tells the editor to keep its text input, rather than offering nothing.
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        when(catalog.readableProfileProperties("site")).thenThrow(new ProfilePropertiesUnavailableException("down"));
        assertFalse(ProfilePropertiesQueryExtension.list(sessionWith(true), catalog, PATH).isAvailable());
        assertSame(GqlProfileProperties.UNAVAILABLE, ProfilePropertiesQueryExtension.list(sessionWith(true), null, PATH));
    }

    @Test
    void refusesACallerWhoCannotEditTheNode() throws Exception {
        // Verifies that only an author of the node lists the schema, and that jCustomer is not asked for anyone else.
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        JCRSessionWrapper session = sessionWith(false);
        assertThrows(AccessDeniedException.class, () -> ProfilePropertiesQueryExtension.list(session, catalog, PATH));
        verify(catalog, never()).readableProfileProperties(any());
    }

    @Test
    void refusesANodeOutsideTheFormsOfASite() throws Exception {
        // Verifies that an editable node elsewhere — the caller's own user node, the system site, a node of a
        // site outside any form — lists nothing, and that jCustomer is not asked for it.
        ProfilePropertyCatalog catalog = mock(ProfilePropertyCatalog.class);
        JCRSessionWrapper user = sessionWith("/users/ab/cd/ef/jdoe", "systemsite", false, true);
        JCRSessionWrapper systemSite = sessionWith("/sites/systemsite/contents/form/field", "systemsite", true, true);
        JCRSessionWrapper noSite = sessionWith("/sites/gone/contents/form/field", null, true, true);
        JCRSessionWrapper notInForm = sessionWith("/sites/site/contents/text", "site", false, true);
        assertThrows(AccessDeniedException.class, () -> ProfilePropertiesQueryExtension.list(user, catalog, "/users/ab/cd/ef/jdoe"));
        assertThrows(AccessDeniedException.class, () -> ProfilePropertiesQueryExtension.list(systemSite, catalog, "/sites/systemsite/contents/form/field"));
        assertThrows(AccessDeniedException.class, () -> ProfilePropertiesQueryExtension.list(noSite, catalog, "/sites/gone/contents/form/field"));
        assertThrows(AccessDeniedException.class, () -> ProfilePropertiesQueryExtension.list(notInForm, catalog, "/sites/site/contents/text"));
        verify(catalog, never()).readableProfileProperties(any());
    }

    @Test
    void refusesAPathTheCallerCannotRead() throws Exception {
        // Verifies that a node the session cannot see fails the field, as the repository refuses it.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode(PATH)).thenThrow(new PathNotFoundException(PATH));
        assertThrows(PathNotFoundException.class, () -> ProfilePropertiesQueryExtension.list(session, mock(ProfilePropertyCatalog.class), PATH));
    }
}
