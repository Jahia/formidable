package org.jahia.modules.formidable.formsimport;

import org.jahia.modules.formidable.formsimport.jahiaforms.FormsDisplay;
import org.jahia.modules.formidable.formsimport.jahiaforms.ImportEnvironment;
import org.jahia.services.content.JCRItemWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRWorkspaceWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeTypeManager;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportSitePagesTest {

    private static final String SITE = "target";

    private static JCRNodeWrapper node(JCRSessionWrapper edit, String path, String identifier, boolean page) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getPath()).thenReturn(path);
        when(node.getIdentifier()).thenReturn(identifier);
        when(node.isNodeType(ImportSitePages.PAGE_TYPE)).thenReturn(page);
        // doReturn: the session already throws for any other identifier
        doReturn(true).when(edit).nodeExists(path);
        doReturn(node).when(edit).getNode(path);
        doReturn(node).when(edit).getNodeByIdentifier(identifier);
        return node;
    }

    @Test
    void aPageIsFoundByItsPathByTheSamePathInThisSiteOrByItsIdentifier() throws Exception {
        JCRSessionWrapper edit = mock(JCRSessionWrapper.class);
        when(edit.nodeExists(anyString())).thenReturn(false);
        when(edit.getNodeByIdentifier(anyString())).thenThrow(new ItemNotFoundException());
        node(edit, "/sites/target/home", "uuid-home", true);
        node(edit, "/sites/target/thanks", "uuid-thanks", true);
        node(edit, "/sites/target/contents/news", "uuid-news", false);
        node(edit, "/sites/other/home", "uuid-other", true);
        ImportSitePages pages = new ImportSitePages(edit, mock(JCRSessionWrapper.class), SITE);

        assertEquals(Optional.of("uuid-home"), pages.pageOf("/sites/target/home"));
        // the page of the source site, its copy in this one
        assertEquals(Optional.of("uuid-thanks"), pages.pageOf("/sites/source/thanks"));
        assertEquals(Optional.of("uuid-thanks"), pages.pageOf("uuid-thanks"));
        // a page of another site is not a target: its copy here is, when there is one
        assertEquals(Optional.of("uuid-home"), pages.pageOf("/sites/other/home"));
        assertEquals(Optional.empty(), pages.pageOf("uuid-other"));
        // not a page, not found, nothing
        assertEquals(Optional.empty(), pages.pageOf("/sites/target/contents/news"));
        assertEquals(Optional.empty(), pages.pageOf("/sites/source/gone"));
        assertEquals(Optional.empty(), pages.pageOf("uuid-gone"));
        assertEquals(Optional.empty(), pages.pageOf(" "));
    }

    @Test
    void withoutFormsNoDisplayIsFound() throws Exception {
        JCRSessionWrapper live = mock(JCRSessionWrapper.class);
        JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class);
        NodeTypeManager types = mock(NodeTypeManager.class);
        when(live.getWorkspace()).thenReturn(workspace);
        when(workspace.getNodeTypeManager()).thenReturn(types);
        when(types.hasNodeType(ImportSitePages.DISPLAY_TYPE)).thenReturn(false);

        ImportSitePages pages = new ImportSitePages(mock(JCRSessionWrapper.class), live, SITE);
        assertEquals(Optional.empty(), pages.displaysUnread());

        assertEquals(List.of(), pages.displaysOf("uf"));
        // the redirects of the displays are lost: the run names it once
        assertEquals(Optional.of(ImportEnvironment.DisplaysUnread.NOT_ON_THIS_INSTANCE), pages.displaysUnread());
    }

    /** A live session that registers the display type, or not. */
    private static JCRSessionWrapper liveRegistering(boolean displayType) throws RepositoryException {
        JCRSessionWrapper live = mock(JCRSessionWrapper.class);
        JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class);
        NodeTypeManager types = mock(NodeTypeManager.class);
        when(live.getWorkspace()).thenReturn(workspace);
        when(workspace.getNodeTypeManager()).thenReturn(types);
        when(types.hasNodeType(ImportSitePages.DISPLAY_TYPE)).thenReturn(displayType);
        return live;
    }

    /** A display at this path, under these ancestors (root first), with or without a redirect URL. */
    private static JCRNodeWrapper display(String path, List<JCRItemWrapper> ancestors, String redirectUrl) throws RepositoryException {
        JCRNodeWrapper display = mock(JCRNodeWrapper.class);
        when(display.getPath()).thenReturn(path);
        when(display.getAncestors()).thenReturn(ancestors);
        when(display.hasProperty(ImportSitePages.REDIRECT_URL)).thenReturn(redirectUrl != null);
        if (redirectUrl != null) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getString()).thenReturn(redirectUrl);
            when(display.getProperty(ImportSitePages.REDIRECT_URL)).thenReturn(property);
        }
        return display;
    }

    /** A node above a display: a page, or another node. */
    private static JCRNodeWrapper ancestor(String path, boolean page) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getPath()).thenReturn(path);
        when(node.isNodeType(ImportSitePages.PAGE_TYPE)).thenReturn(page);
        return node;
    }

    @Test
    void theDisplaysAreNamedByTheirPageAndOrderedByIt() throws Exception {
        // Verifies the displays of a form: each is named by the page holding it, not by its own path, a display in no page
        // by its own path, and they come ordered by that name, whatever order the query returned them in.
        JCRNodeWrapper sites = ancestor("/sites/source", false);
        JCRNodeWrapper home = ancestor("/sites/source/home", true);
        JCRNodeWrapper zebra = ancestor("/sites/source/home/zebra", true);
        JCRNodeWrapper area = ancestor("/sites/source/home/zebra/main", false);
        JCRNodeWrapper contents = ancestor("/sites/source/contents", false);
        // in the reverse order of their pages, as a query may return them; the nearest page wins over the home above it
        List<JCRNodeWrapper> found = List.of(
                display("/sites/source/home/zebra/main/display", List.of(sites, home, zebra, area), "https://www.example.com/thanks"),
                display("/sites/source/contents/display", List.of(sites, contents), null));
        ImportSitePages pages = new ImportSitePages(mock(JCRSessionWrapper.class), liveRegistering(true), SITE,
                formUuid -> "uf".equals(formUuid) ? found : List.of());

        assertEquals(List.of(
                new FormsDisplay("/sites/source/contents/display", null),
                new FormsDisplay("/sites/source/home/zebra", "https://www.example.com/thanks")), pages.displaysOf("uf"));
        assertEquals(Optional.empty(), pages.displaysUnread());
    }

    @Test
    void aQueryThatFailsFindsNoDisplayAndIsNamedOnce() throws Exception {
        // Verifies a failed query: no display, and the run is told the displays could not be read; it outranks an
        // absent type, which a later call cannot turn it back into.
        JCRSessionWrapper live = liveRegistering(true);
        ImportSitePages pages = new ImportSitePages(mock(JCRSessionWrapper.class), live, SITE, formUuid -> {
            throw new RepositoryException("index down");
        });

        assertEquals(List.of(), pages.displaysOf("uf"));
        assertEquals(Optional.of(ImportEnvironment.DisplaysUnread.QUERY_FAILED), pages.displaysUnread());
        when(live.getWorkspace().getNodeTypeManager().hasNodeType(ImportSitePages.DISPLAY_TYPE)).thenReturn(false);
        pages.displaysOf("other");
        assertEquals(Optional.of(ImportEnvironment.DisplaysUnread.QUERY_FAILED), pages.displaysUnread());
    }

    @Test
    void formsInstalledButNoPageShowingTheFormsIsNamedAsPagesElsewhere() throws Exception {
        // Verifies the instance that registers the display type yet holds none of the source's pages: every query finds
        // nothing, and the run is told the displays are not on this instance, as when Forms is not installed.
        ImportSitePages pages = new ImportSitePages(mock(JCRSessionWrapper.class), liveRegistering(true), SITE, formUuid -> List.of());

        assertEquals(Optional.empty(), pages.displaysUnread());
        pages.displaysOf("uf");
        pages.displaysOf("other");

        assertEquals(Optional.of(ImportEnvironment.DisplaysUnread.NOT_ON_THIS_INSTANCE), pages.displaysUnread());
    }

    @Test
    void oneDisplayFoundIsNoReason() throws Exception {
        // Verifies the limit of the note: one form shown on a page of this instance, another shown nowhere, no reason.
        List<JCRNodeWrapper> found = List.of(display("/sites/source/home/display", List.of(ancestor("/sites/source/home", true)), null));
        ImportSitePages pages = new ImportSitePages(mock(JCRSessionWrapper.class), liveRegistering(true), SITE,
                formUuid -> "uf".equals(formUuid) ? found : List.of());

        pages.displaysOf("uf");
        pages.displaysOf("other");

        assertEquals(Optional.empty(), pages.displaysUnread());
    }

    @Test
    void theQueryNamesTheFormAsAStringLiteral() {
        // Verifies the statement of the displays: the form's identifier is quoted, a quote in it doubled.
        assertEquals("SELECT * FROM [fcnt:formDisplay] AS d WHERE d.[referenceForm] = 'u''f'", ImportSitePages.displaysStatement("u'f"));
    }
}
