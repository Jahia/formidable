package org.jahia.modules.formidable.formsimport;

import org.jahia.services.content.JCRNodeWrapper;
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

        assertEquals(List.of(), new ImportSitePages(mock(JCRSessionWrapper.class), live, SITE).displaysOf("uf"));
    }
}
