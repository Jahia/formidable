package org.jahia.test.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.test.modules.formidable.quarantine.run.InMemoryQuarantine;
import org.jahia.test.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.test.modules.formidable.quarantine.run.Release;
import org.jahia.test.modules.formidable.quarantine.run.Transitions;
import org.jahia.test.modules.formidable.quarantine.state.HeldState;
import org.jahia.test.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.test.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.test.modules.formidable.quarantine.store.OutcomeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operations of a manager, with the two stores in memory and the release of the module over a recording double of
 * the engine's {@link DeferredActions}: each operation from each state, the permission of a manager, the audit record
 * of each operation, and what retry, resume and reject change.
 */
class QuarantineOperationsTest {

    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final String MANAGER = "quarManager";
    private static final String SHA = "0".repeat(64);

    /** A double of {@link Managers}: who the user is, and the forms the user manages. */
    private static final class FakeManagers implements Managers {
        String user = MANAGER;
        boolean administrator;
        final List<String> managedForms = new ArrayList<>(List.of(FORM));

        @Override
        public String currentUserName() {
            return user;
        }

        @Override
        public boolean isServerAdministrator() {
            return administrator;
        }

        @Override
        public boolean mayManage(String formId) {
            return administrator || managedForms.contains(formId);
        }
    }

    private InMemoryQuarantine stores;
    private FakeManagers managers;
    private QuarantineOperations operations;
    private final List<Integer> releasedFrom = new ArrayList<>();
    /** The index of the action after release that fails, or -1 when every action runs. */
    private int failingAction = -1;

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        managers = new FakeManagers();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        operations = new QuarantineOperations(managers, clock);
        operations.setStore(stores);
        operations.setCheckStore(stores);
        operations.setRelease(() -> new Release(stores, stores, twoActions(), new Transitions(stores), clock));
    }

    /** Two actions after release, each checkpointed; the one at {@link #failingAction} fails. */
    private DeferredActions twoActions() {
        return (submission, from, completed) -> {
            releasedFrom.add(from);
            for (int index = from; index < 2; index++) {
                if (index == failingAction) {
                    throw new DeferredActionException("The target refused", index, 2, null);
                }
                completed.accept(index);
            }
        };
    }

    /** A held submission in review, for the reason of the decision. */
    private Held inReview(String reason) {
        Held held = stores.hold(FORM, "INCONCLUSIVE\nA note");
        held.state = HeldState.NEEDS_REVIEW.value();
        held.decision = new DecisionRecord(reason, "item0", "remoteCheck", SHA, null, NOW);
        return held;
    }

    private Held afterAFailedAction() {
        Held held = inReview(DecisionRecord.ACTION_FAILED);
        held.decision = new DecisionRecord(DecisionRecord.ACTION_FAILED, null, null, null, 1, NOW);
        held.completedActions = 1;
        return held;
    }

    private static OperationRefusedException.Reason refusal(ThrowingOperation operation) {
        return assertThrows(OperationRefusedException.class, operation::run).reason();
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    /** needsReview allows each operation, and releasing a resume or a reject of a release that stopped half way. */
    private static boolean allows(HeldState state, Operation operation) {
        return state == HeldState.NEEDS_REVIEW
                || state == HeldState.RELEASING && (operation == Operation.RESUME || operation == Operation.REJECT);
    }

    @Test
    void eachOperationIsRefusedFromEveryStateThatDoesNotAllowIt() {
        for (Operation operation : Operation.values()) {
            for (HeldState state : HeldState.values()) {
                if (allows(state, operation)) {
                    continue;
                }
                Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
                held.state = state.value();

                assertEquals(OperationRefusedException.Reason.CONFLICT,
                        refusal(() -> operations.apply(held.path, operation)), operation + " from " + state);
                assertEquals(state.value(), held.state, "state after a refused " + operation);
                assertTrue(held.audit.isEmpty(), "audit after a refused " + operation);
                assertTrue(held.valuesPresent, "values after a refused " + operation);
            }
        }
        assertTrue(releasedFrom.isEmpty(), "no action after release ran");
    }

    @Test
    void retryResetsTheAttemptsAndTheFailuresOfEachCheck() throws Exception {
        Held held = inReview("ATTEMPTS_EXHAUSTED");
        held.attempts = 2;
        held.nextAttempt = NOW.plusSeconds(60);
        held.outcomes.put("item0/remoteCheck", new OutcomeRecord("item0", "remoteCheck", SHA, OutcomeRecord.FAILED,
                "stub.down", true, null, 2, NOW));
        held.outcomes.put("item0/otherCheck", new OutcomeRecord("item0", "otherCheck", SHA, OutcomeRecord.PASS,
                "stub.marker", false, null, 0, NOW));

        OperationResult result = operations.apply(held.path, Operation.RETRY);

        assertEquals(HeldState.CHECKING, result.to());
        assertEquals(HeldState.CHECKING.value(), held.state);
        assertEquals(0, held.attempts, "attempts of the submission");
        assertNull(held.nextAttempt, "next attempt");
        assertEquals(List.of("item0/otherCheck"), List.copyOf(held.outcomes.keySet()),
                "outcomes kept: the PASS alone, so no failure of a check counts any more");
        assertTrue(held.valuesPresent, "values kept for the next run");
        assertEquals(List.of(new AuditRecord("retry", MANAGER, "needsReview", "checking", NOW)), held.audit);
    }

    @Test
    void retryAlsoDropsAnInconclusiveOutcomeSoTheCheckIsCalledAgain() throws Exception {
        Held held = inReview("INCONCLUSIVE");
        held.outcomes.put("item0/remoteCheck", new OutcomeRecord("item0", "remoteCheck", SHA,
                OutcomeRecord.INCONCLUSIVE, "stub.marker", false, null, 0, NOW));

        operations.apply(held.path, Operation.RETRY);

        assertTrue(held.outcomes.isEmpty(), "outcomes after the retry");
    }

    @Test
    void releaseRunsEveryActionAfterReleaseAndCompletes() throws Exception {
        Held held = inReview("INCONCLUSIVE");

        OperationResult result = operations.apply(held.path, Operation.RELEASE);

        assertEquals(HeldState.NEEDS_REVIEW, result.from());
        assertEquals(HeldState.COMPLETED, result.to());
        assertEquals(List.of("releasing", "completed"), held.states);
        assertEquals(List.of(0), releasedFrom, "the actions ran once, from the first");
        assertEquals(2, held.completedActions);
        assertFalse(held.valuesPresent, "values deleted once completed");
        assertFalse(held.anyBytes(), "bytes deleted once completed");
        assertEquals(List.of(new AuditRecord("release", MANAGER, "needsReview", "completed", NOW)), held.audit);
    }

    @Test
    void releaseIsRefusedAfterAnActionFailed() {
        Held held = afterAFailedAction();

        assertEquals(OperationRefusedException.Reason.CONFLICT,
                refusal(() -> operations.apply(held.path, Operation.RELEASE)));
        assertEquals("needsReview", held.state);
        assertTrue(releasedFrom.isEmpty());
        assertTrue(held.audit.isEmpty());
    }

    @Test
    void resumeStartsAtTheActionThatFailed() throws Exception {
        Held held = afterAFailedAction();

        OperationResult result = operations.apply(held.path, Operation.RESUME);

        assertEquals(HeldState.COMPLETED, result.to());
        assertEquals(List.of(1), releasedFrom, "the resumed run starts at the failed action, the first is not run again");
        assertEquals(2, held.completedActions);
        assertFalse(held.valuesPresent);
        assertEquals(List.of(new AuditRecord("resume", MANAGER, "needsReview", "completed", NOW)), held.audit);
    }

    @Test
    void aResumeThatFailsAgainGoesBackToReviewAndKeepsTheValues() throws Exception {
        Held held = afterAFailedAction();
        failingAction = 1;

        OperationResult result = operations.apply(held.path, Operation.RESUME);

        assertEquals(HeldState.NEEDS_REVIEW, result.to());
        assertEquals(1, held.completedActions, "index to resume from");
        assertTrue(held.valuesPresent, "values kept for the next resume");
        assertTrue(held.anyBytes(), "bytes kept for the next resume");
        assertEquals(List.of(new AuditRecord("resume", MANAGER, "needsReview", "needsReview", NOW)), held.audit);
    }

    @Test
    void resumeOfAReleaseThatStoppedHalfWayStartsAtTheCompletedActions() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldState.RELEASING.value();
        held.completedActions = 1;

        OperationResult result = assertDoesNotThrow(() -> operations.apply(held.path, Operation.RESUME));

        assertEquals(HeldState.RELEASING, result.from());
        assertEquals(HeldState.COMPLETED, result.to());
        assertEquals(List.of(1), releasedFrom, "the resumed run starts after the last action that ran");
        assertEquals(2, held.completedActions);
        assertFalse(held.valuesPresent, "values deleted once completed");
        assertEquals(List.of(new AuditRecord("resume", MANAGER, "releasing", "completed", NOW)), held.audit);
    }

    @Test
    void rejectOfAReleaseThatStoppedHalfWayDeletesTheValuesAndRunsNoAction() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldState.RELEASING.value();
        held.completedActions = 1;

        OperationResult result = assertDoesNotThrow(() -> operations.apply(held.path, Operation.REJECT));

        assertEquals(HeldState.REJECTED, result.to());
        assertEquals("rejected", held.state);
        assertFalse(held.valuesPresent, "values deleted");
        assertFalse(held.anyBytes(), "bytes deleted");
        assertTrue(releasedFrom.isEmpty(), "no action after release ran");
        assertEquals(List.of(new AuditRecord("reject", MANAGER, "releasing", "rejected", NOW)), held.audit);
    }

    @Test
    void resumeIsRefusedWhenNoActionFailed() {
        Held held = inReview("INCONCLUSIVE");

        assertEquals(OperationRefusedException.Reason.CONFLICT,
                refusal(() -> operations.apply(held.path, Operation.RESUME)));
        assertEquals("needsReview", held.state);
        assertTrue(releasedFrom.isEmpty());
    }

    @Test
    void rejectDeletesTheValuesAndTheBytesAndKeepsTheOutcomesAndTheDecision() throws Exception {
        Held held = inReview("INCONCLUSIVE");
        held.outcomes.put("item0/remoteCheck", new OutcomeRecord("item0", "remoteCheck", SHA,
                OutcomeRecord.INCONCLUSIVE, "stub.marker", false, null, 0, NOW));
        DecisionRecord decision = held.decision;

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to());
        assertEquals("rejected", held.state);
        assertFalse(held.valuesPresent, "values deleted");
        assertFalse(held.anyBytes(), "bytes deleted");
        assertEquals(1, held.outcomes.size(), "outcomes kept");
        assertEquals(decision, held.decision, "decision kept");
        assertTrue(releasedFrom.isEmpty(), "no action after release ran");
        assertEquals(List.of(new AuditRecord("reject", MANAGER, "needsReview", "rejected", NOW)), held.audit);
    }

    /** Q20, control: without the check of the permission, the reader runs each operation and this test fails. */
    @Test
    void aUserWhoDoesNotManageTheFormIsRefusedEachOperationAndNothingChanges() {
        managers.user = "quarResultsReader";
        managers.managedForms.clear();
        Held held = inReview("INCONCLUSIVE");
        held.attempts = 3;
        held.outcomes.put("item0/remoteCheck", new OutcomeRecord("item0", "remoteCheck", SHA,
                OutcomeRecord.INCONCLUSIVE, "stub.marker", false, null, 0, NOW));
        Map<String, OutcomeRecord> outcomes = Map.copyOf(held.outcomes);

        for (Operation operation : Operation.values()) {
            assertEquals(OperationRefusedException.Reason.FORBIDDEN,
                    refusal(() -> operations.apply(held.path, operation)), operation.value() + " by a reader");
        }

        assertEquals("needsReview", held.state, "state");
        assertTrue(held.states.isEmpty(), "no state was written");
        assertEquals(3, held.attempts, "attempts");
        assertEquals(outcomes, held.outcomes, "outcomes");
        assertTrue(held.valuesPresent, "values");
        assertTrue(held.anyBytes(), "bytes");
        assertTrue(held.audit.isEmpty(), "audit");
        assertTrue(releasedFrom.isEmpty(), "no action after release ran");
    }

    @Test
    void aServerAdministratorManagesEveryForm() throws Exception {
        managers.user = "root";
        managers.administrator = true;
        managers.managedForms.clear();
        Held held = inReview("INCONCLUSIVE");

        operations.apply(held.path, Operation.REJECT);

        assertEquals("rejected", held.state);
        assertEquals("root", held.audit.get(0).userName());
    }

    @Test
    void aPathThatNamesNoHeldSubmissionIsForbiddenButToAServerAdministrator() {
        String path = "/sites/site/formidable-quarantine/" + FORM + "/2026/11/19/held-none";

        assertEquals(OperationRefusedException.Reason.FORBIDDEN,
                refusal(() -> operations.apply(path, Operation.REJECT)));
        managers.administrator = true;
        assertEquals(OperationRefusedException.Reason.NOT_FOUND,
                refusal(() -> operations.apply(path, Operation.REJECT)));
    }

    @Test
    void anAuditRecordNamesTheUserAndHoldsNoValue() throws Exception {
        Held held = inReview("INCONCLUSIVE");

        operations.apply(held.path, Operation.RETRY);

        AuditRecord audit = held.audit.get(0);
        assertEquals(MANAGER, audit.userName());
        String text = audit.toString();
        assertFalse(text.contains("Ada"), "a public value in the audit record");
        assertFalse(text.contains(InMemoryQuarantine.SECRET), "a sensitive value in the audit record");
        assertFalse(text.contains("INCONCLUSIVE"), "the content of the file in the audit record");
    }
}
