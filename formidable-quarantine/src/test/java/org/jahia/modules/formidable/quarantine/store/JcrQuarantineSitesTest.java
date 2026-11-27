package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The sites of the quarantine, read with a mocked system session. */
class JcrQuarantineSitesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final String ROOT_A = "/sites/siteA/formidable-quarantine";

    private JCRSessionWrapper session;
    private final List<String> workspaces = new ArrayList<>();
    private final List<String> statements = new ArrayList<>();
    private final Query query = mock(Query.class);
    private JcrQuarantineSites sites;

    @BeforeEach
    void setUp() {
        session = mock(JCRSessionWrapper.class);
        sites = new JcrQuarantineSites() {
            @Override
            <T> T inSystemSession(String workspace, JCRCallback<T> callback) throws RepositoryException {
                workspaces.add(workspace);
                return callback.doInJCR(session);
            }

            @Override
            Query createQuery(JCRSessionWrapper querySession, String statement) {
                statements.add(statement);
                return query;
            }
        };
    }

    private static JCRNodeWrapper site(String name, String rootType) throws RepositoryException {
        JCRNodeWrapper site = mock(JCRNodeWrapper.class);
        when(site.getName()).thenReturn(name);
        if (rootType != null) {
            JCRNodeWrapper root = mock(JCRNodeWrapper.class);
            when(root.isNodeType(QuarantineTree.ROOT_TYPE)).thenReturn(QuarantineTree.ROOT_TYPE.equals(rootType));
            when(site.hasNode(QuarantineTree.ROOT_NAME)).thenReturn(true);
            when(site.getNode(QuarantineTree.ROOT_NAME)).thenReturn(root);
        }
        return site;
    }

    private void sitesNode(JCRNodeWrapper... children) throws RepositoryException {
        JCRNodeWrapper sitesNode = mock(JCRNodeWrapper.class);
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.iterator()).thenReturn(List.of(children).iterator());
        when(sitesNode.getNodes()).thenReturn(iterator);
        when(session.nodeExists("/sites")).thenReturn(true);
        when(session.getNode("/sites")).thenReturn(sitesNode);
    }

    private JCRNodeWrapper siteSettings(boolean mixin, Long days) throws RepositoryException {
        JCRNodeWrapper site = mock(JCRNodeWrapper.class);
        when(session.nodeExists("/sites/siteA")).thenReturn(true);
        when(session.getNode("/sites/siteA")).thenReturn(site);
        when(site.isNodeType(JcrQuarantineSites.SITE_MIXIN)).thenReturn(mixin);
        when(site.hasProperty(JcrQuarantineSites.RETENTION_DAYS)).thenReturn(days != null);
        if (days != null) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getLong()).thenReturn(days);
            when(site.getProperty(JcrQuarantineSites.RETENTION_DAYS)).thenReturn(property);
        }
        return site;
    }

    @Test
    void noSitesNodeMeansNoSite() throws Exception {
        assertEquals(List.of(), sites.withTree());
        assertEquals(List.of("live"), workspaces);
    }

    @Test
    void onlyASiteWhoseRootIsAQuarantineRootIsListed() throws Exception {
        sitesNode(site("siteA", QuarantineTree.ROOT_TYPE), site("siteB", "jnt:contentFolder"), site("siteC", null));

        assertEquals(List.of("siteA"), sites.withTree());
    }

    @Test
    void theRetentionOfASiteIsReadFromTheDefaultWorkspaceWhenItsMixinSetsIt() throws Exception {
        siteSettings(true, 7L);

        assertEquals(OptionalLong.of(7), sites.retentionDays("siteA"));
        assertEquals(List.of("default"), workspaces);
    }

    @Test
    void aSiteWithNoSettingSetsNoRetention() throws Exception {
        assertEquals(OptionalLong.empty(), sites.retentionDays("siteA"));
        siteSettings(false, 7L);
        assertEquals(OptionalLong.empty(), sites.retentionDays("siteA"));
        siteSettings(true, null);
        assertEquals(OptionalLong.empty(), sites.retentionDays("siteA"));
    }

    @Test
    void aRetentionThatIsNotANumberIsAnErrorOfTheStore() throws Exception {
        JCRNodeWrapper site = siteSettings(true, 7L);
        JCRPropertyWrapper invalid = mock(JCRPropertyWrapper.class);
        when(invalid.getLong()).thenThrow(new ValueFormatException("not a long"));
        when(site.getProperty(JcrQuarantineSites.RETENTION_DAYS)).thenReturn(invalid);

        assertThrows(QuarantineStoreException.class, () -> sites.retentionDays("siteA"));
    }

    @Test
    void aSiteWithNoTreeHasNoDueSubmissionAndRunsNoQuery() throws Exception {
        assertFalse(sites.anyDue("siteA", NOW));

        assertEquals(List.of(), statements);
    }

    @Test
    void theDueQueryNamesTheRootOfTheSiteAndBindsTheStatesAndTheTime() throws Exception {
        when(session.nodeExists(ROOT_A)).thenReturn(true);
        QueryResult result = mock(QueryResult.class);
        NodeIterator found = mock(NodeIterator.class);
        ValueFactory values = mock(ValueFactory.class);
        Value held = mock(Value.class);
        Value checking = mock(Value.class);
        Value now = mock(Value.class);
        when(query.execute()).thenReturn(result);
        when(result.getNodes()).thenReturn(found);
        when(found.hasNext()).thenReturn(true);
        when(session.getValueFactory()).thenReturn(values);
        when(values.createValue(HeldStates.HELD)).thenReturn(held);
        when(values.createValue(HeldStates.CHECKING)).thenReturn(checking);
        ArgumentCaptor<Calendar> time = ArgumentCaptor.forClass(Calendar.class);
        when(values.createValue(time.capture())).thenReturn(now);

        assertTrue(sites.anyDue("siteA", NOW));

        assertEquals(List.of(String.format(JcrQuarantineSites.DUE_QUERY, ROOT_A)), statements);
        verify(query).bindValue("held", held);
        verify(query).bindValue("checking", checking);
        verify(query).bindValue("now", now);
        verify(query).setLimit(1);
        assertEquals(NOW, time.getValue().toInstant());
    }
}
