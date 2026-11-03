package org.jahia.modules.formidable.jexperience.engine.graphql;

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
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("site");
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getResolveSite()).thenReturn(site);
        when(node.hasPermission(ProfilePropertiesQueryExtension.REQUIRED_PERMISSION)).thenReturn(canEdit);
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode(PATH)).thenReturn(node);
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
    void refusesAPathTheCallerCannotRead() throws Exception {
        // Verifies that a node the session cannot see fails the field, as the repository refuses it.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode(PATH)).thenThrow(new PathNotFoundException(PATH));
        assertThrows(PathNotFoundException.class, () -> ProfilePropertiesQueryExtension.list(session, mock(ProfilePropertyCatalog.class), PATH));
    }
}
