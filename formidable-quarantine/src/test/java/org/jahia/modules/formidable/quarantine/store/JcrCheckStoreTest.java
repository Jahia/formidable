package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void theRecordsOfSeveralSubmissionsAreReadInOneSessionAndTheValuesOnlyWhenAsked() throws Exception {
        JCRNodeIteratorWrapper none = mock(JCRNodeIteratorWrapper.class);
        when(none.iterator()).thenReturn(List.<JCRNodeWrapper>of().iterator());
        when(checks.getNodes()).thenReturn(none);
        JCRNodeWrapper decision = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper reason = mock(JCRPropertyWrapper.class);
        when(reason.getString()).thenReturn(DecisionRecord.ACTION_FAILED);
        when(decision.hasProperty(JcrCheckStore.REASON)).thenReturn(true);
        when(decision.getProperty(JcrCheckStore.REASON)).thenReturn(reason);
        when(checks.hasNode(CheckStore.DECISION_NODE)).thenReturn(true);
        when(checks.getNode(CheckStore.DECISION_NODE)).thenReturn(decision);
        String missing = HELD_PATH.replace("held-1", "held-2");
        int[] sessions = {0};
        JcrCheckStore counting = new JcrCheckStore() {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                sessions[0]++;
                return callback.doInJCR(session);
            }
        };

        Map<String, HeldDetails> details = counting.details(List.of(HELD_PATH, missing), Set.of());

        assertEquals(1, sessions[0], "sessions");
        assertEquals(Set.of(HELD_PATH), details.keySet(), "a missing path is left out");
        assertEquals(DecisionRecord.ACTION_FAILED, details.get(HELD_PATH).decision().reason());
        assertEquals(Map.of(), details.get(HELD_PATH).publicValues(), "values not asked for");
        verify(held, never()).getNode(QuarantineTree.VALUES);
        verify(held, never()).getNode(QuarantineTree.SENSITIVE);
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

    private static JCRNodeIteratorWrapper children(JCRNodeWrapper... nodes) {
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.iterator()).thenReturn(List.of(nodes).iterator());
        return iterator;
    }

    private static JCRNodeWrapper outcome(String value) throws RepositoryException {
        JCRNodeWrapper outcome = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(outcome.isNodeType(JcrCheckStore.OUTCOME_TYPE)).thenReturn(true);
        when(outcome.hasProperty(JcrCheckStore.OUTCOME)).thenReturn(true);
        when(outcome.getProperty(JcrCheckStore.OUTCOME)).thenReturn(property);
        when(property.getString()).thenReturn(value);
        return outcome;
    }

    @Test
    void aRetryKeepsThePassOutcomesAloneAndRemovesTheOthersAfterTheWalk() throws Exception {
        JCRNodeWrapper pass = outcome(OutcomeRecord.PASS);
        JCRNodeWrapper failed = outcome(OutcomeRecord.FAILED);
        JCRNodeWrapper inconclusive = outcome(OutcomeRecord.INCONCLUSIVE);
        JCRNodeWrapper item = mock(JCRNodeWrapper.class);
        when(item.isNodeType(JcrCheckStore.ITEM_OUTCOMES_TYPE)).thenReturn(true);
        JCRNodeIteratorWrapper outcomes = children(pass, failed, inconclusive);
        when(item.getNodes()).thenReturn(outcomes);
        JCRNodeWrapper decision = mock(JCRNodeWrapper.class);
        JCRNodeIteratorWrapper items = children(item, decision);
        when(checks.getNodes()).thenReturn(items);

        store.keepPassedOutcomesOnly(HELD_PATH);

        verify(pass, never()).remove();
        verify(failed).remove();
        verify(inconclusive).remove();
        verify(decision, never()).remove();
        verify(session).save();
    }

    @Test
    void anAuditRecordTakesTheFirstFreeNameAndNamesNoValue() throws Exception {
        JCRNodeWrapper audit = mock(JCRNodeWrapper.class);
        JCRNodeWrapper entry = mock(JCRNodeWrapper.class);
        when(held.hasNode(QuarantineTree.AUDIT)).thenReturn(true);
        when(held.getNode(QuarantineTree.AUDIT)).thenReturn(audit);
        when(audit.hasNode("record1")).thenReturn(true);
        when(audit.hasNode("record2")).thenReturn(true);
        when(audit.addNode("record3", JcrCheckStore.AUDIT_TYPE)).thenReturn(entry);

        store.writeAudit(HELD_PATH, new AuditRecord(AuditRecord.RUN, null, "checking", "needsReview", AT));

        verify(entry).setProperty(JcrCheckStore.OPERATION, AuditRecord.RUN);
        verify(entry).setProperty(JcrCheckStore.FROM_STATE, "checking");
        verify(entry).setProperty(JcrCheckStore.TO_STATE, "needsReview");
        verify(entry, never()).setProperty(eq(JcrCheckStore.USER_NAME), anyString());
        verify(session).save();
    }

    private static JCRPropertyWrapper property(JCRNodeWrapper node, String name) throws RepositoryException {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
        return property;
    }

    @Test
    void theAuditIsReadInOrderAndSkipsANodeOfAnotherType() throws Exception {
        JCRNodeWrapper first = mock(JCRNodeWrapper.class);
        when(first.isNodeType(JcrCheckStore.AUDIT_TYPE)).thenReturn(true);
        when(property(first, JcrCheckStore.OPERATION).getString()).thenReturn("retry");
        when(property(first, JcrCheckStore.USER_NAME).getString()).thenReturn("quarManager");
        when(property(first, JcrCheckStore.FROM_STATE).getString()).thenReturn("needsReview");
        when(property(first, JcrCheckStore.TO_STATE).getString()).thenReturn("checking");
        Calendar at = GregorianCalendar.from(AT.atZone(ZoneOffset.UTC));
        when(property(first, JcrCheckStore.AT).getDate()).thenReturn(at);
        JCRNodeWrapper second = mock(JCRNodeWrapper.class);
        when(second.isNodeType(JcrCheckStore.AUDIT_TYPE)).thenReturn(true);
        when(property(second, JcrCheckStore.OPERATION).getString()).thenReturn(AuditRecord.RUN);
        when(property(second, JcrCheckStore.FROM_STATE).getString()).thenReturn("checking");
        when(property(second, JcrCheckStore.TO_STATE).getString()).thenReturn("completed");
        when(property(second, JcrCheckStore.AT).getDate()).thenReturn(at);
        JCRNodeWrapper other = mock(JCRNodeWrapper.class);
        JCRNodeWrapper audit = mock(JCRNodeWrapper.class);
        JCRNodeIteratorWrapper records = children(first, other, second);
        when(audit.getNodes()).thenReturn(records);
        when(held.hasNode(QuarantineTree.AUDIT)).thenReturn(true);
        when(held.getNode(QuarantineTree.AUDIT)).thenReturn(audit);

        assertEquals(List.of(new AuditRecord("retry", "quarManager", "needsReview", "checking", AT),
                new AuditRecord(AuditRecord.RUN, null, "checking", "completed", AT)), store.audit(HELD_PATH));
    }

    @Test
    void aHeldSubmissionWithNoAuditHasAnEmptyAudit() throws Exception {
        assertEquals(List.of(), store.audit(HELD_PATH));
    }

    @Test
    void anItemWithNoBytesCannotBeOpened() throws Exception {
        JCRNodeWrapper item = mock(JCRNodeWrapper.class);
        when(held.hasNode("item0")).thenReturn(true);
        when(held.getNode("item0")).thenReturn(item);

        assertThrows(QuarantineStoreException.class, () -> store.openItem(HELD_PATH, "item0"));
    }
}
