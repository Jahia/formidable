package org.jahia.test.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The records of the run against a mocked live session: an outcome written again keeps no ticket of the one before,
 * a decision replaces the one before, and a path that names no held submission is refused before any write.
 */
class JcrCheckStoreTest {

    private static final String HELD_PATH = "/sites/formidableTestSite/formidable-quarantine/"
            + "6a1f0c3e-0000-4000-8000-000000000001/2026/11/19/held-1";
    private static final String SHA256 = "f7c11b5f5115eb9d0f4f8619af8870daf212b37948739f8534e4088a078e7ffd";
    private static final Instant AT = Instant.parse("2026-11-19T18:31:00Z");

    private JCRSessionWrapper session;
    private JCRNodeWrapper held;
    private JCRNodeWrapper checks;
    private JcrCheckStore store;

    @BeforeEach
    void setUp() throws RepositoryException {
        session = mock(JCRSessionWrapper.class);
        held = mock(JCRNodeWrapper.class);
        checks = mock(JCRNodeWrapper.class);
        when(session.nodeExists(HELD_PATH)).thenReturn(true);
        when(session.getNode(HELD_PATH)).thenReturn(held);
        when(held.isNodeType(QuarantineTree.HELD_TYPE)).thenReturn(true);
        when(held.hasNode(QuarantineTree.CHECKS)).thenReturn(true);
        when(held.getNode(QuarantineTree.CHECKS)).thenReturn(checks);
        store = new JcrCheckStore() {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                return callback.doInJCR(session);
            }
        };
    }

    @Test
    void anOutcomeWrittenAgainKeepsNoTicketOfTheOneBefore() throws Exception {
        JCRNodeWrapper item = mock(JCRNodeWrapper.class);
        JCRNodeWrapper outcome = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper ticket = mock(JCRPropertyWrapper.class);
        when(checks.hasNode("item0")).thenReturn(true);
        when(checks.getNode("item0")).thenReturn(item);
        when(item.hasNode("remoteCheck")).thenReturn(true);
        when(item.getNode("remoteCheck")).thenReturn(outcome);
        when(outcome.hasProperty(JcrCheckStore.TICKET)).thenReturn(true);
        when(outcome.getProperty(JcrCheckStore.TICKET)).thenReturn(ticket);

        store.writeOutcome(HELD_PATH, new OutcomeRecord("item0", "remoteCheck", SHA256, OutcomeRecord.PASS,
                "stub.marker", false, null, 0, AT));

        verify(ticket).remove();
        verify(outcome).setProperty(JcrCheckStore.OUTCOME, OutcomeRecord.PASS);
        verify(outcome).setProperty(QuarantineTree.SHA256, SHA256);
        verify(item, never()).addNode(anyString(), anyString());
        verify(session).save();
    }

    @Test
    void aDecisionReplacesTheOneBefore() throws Exception {
        JCRNodeWrapper before = mock(JCRNodeWrapper.class);
        JCRNodeWrapper after = mock(JCRNodeWrapper.class);
        when(checks.hasNode(CheckStore.DECISION_NODE)).thenReturn(true);
        when(checks.getNode(CheckStore.DECISION_NODE)).thenReturn(before);
        when(checks.addNode(CheckStore.DECISION_NODE, JcrCheckStore.DECISION_TYPE)).thenReturn(after);

        store.writeDecision(HELD_PATH, new DecisionRecord(DecisionRecord.INTEGRITY_FAILED, "item0", null, SHA256, null,
                AT));

        verify(before).remove();
        verify(after).setProperty(JcrCheckStore.REASON, DecisionRecord.INTEGRITY_FAILED);
        verify(after).setProperty(JcrCheckStore.ITEM_NAME, "item0");
        verify(session).save();
    }

    @Test
    void aPathThatNamesNoHeldSubmissionIsRefusedBeforeAnyWrite() throws Exception {
        JCRNodeWrapper other = mock(JCRNodeWrapper.class);
        String otherPath = "/sites/formidableTestSite/files/held-1";
        when(session.nodeExists(otherPath)).thenReturn(true);
        when(session.getNode(otherPath)).thenReturn(other);
        DecisionRecord decision = new DecisionRecord(DecisionRecord.INTEGRITY_FAILED, null, null, null, null, AT);

        assertThrows(QuarantineStoreException.class, () -> store.writeDecision(otherPath, decision));
        assertThrows(QuarantineStoreException.class, () -> store.writeDecision("/users/root", decision));

        verify(other, never()).addNode(anyString(), anyString());
        verify(session, never()).save();
    }

    @Test
    void anItemWithNoBytesCannotBeOpened() throws Exception {
        JCRNodeWrapper item = mock(JCRNodeWrapper.class);
        when(held.hasNode("item0")).thenReturn(true);
        when(held.getNode("item0")).thenReturn(item);

        assertThrows(QuarantineStoreException.class, () -> store.openItem(HELD_PATH, "item0"));
    }
}
