package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.quarantine.run.FormPolicy;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine;
import org.jahia.modules.formidable.quarantine.run.OnFail;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.modules.formidable.quarantine.run.QuarantineRun;
import org.jahia.modules.formidable.quarantine.run.Release;
import org.jahia.modules.formidable.quarantine.run.Transitions;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private InMemoryQuarantine stores;
    private FakeManagers managers;
    private QuarantineOperations operations;
    private final List<Integer> releasedFrom = new ArrayList<>();
    /** What the actions after release received. */
    private final List<HeldSubmission> released = new ArrayList<>();
    /** The policy of the form, as the operations read it. */
    private FormPolicy policy = FormPolicy.DEFAULT;
    /** The index of the action after release that fails, or -1 when every action runs. */
    private int failingAction = -1;

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        managers = new FakeManagers();
        operations = new QuarantineOperations(managers, clock);
        operations.setStore(stores);
        operations.setCheckStore(stores);
        operations.setRelease(() -> new Release(stores, stores, twoActions(), new Transitions(stores), clock,
                formId -> policy));
    }

    /** Two actions after release, each checkpointed; the one at {@link #failingAction} fails. */
    private DeferredActions twoActions() {
        return (submission, from, completed) -> {
            releasedFrom.add(from);
            released.add(submission);
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

    /** A run that releases a submission holds the lock of the run: a resume of that submission waits for it. */
    @Test
    void anOperationWaitsForTheRunThatHoldsItsLock() throws Exception {
        QuarantineRun run = mock(QuarantineRun.class);
        operations.setRun(run);
        operations.setRelease(() -> new Release(stores, stores, twoActions(), new Transitions(stores), clock));
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldState.RELEASING.value();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<OperationResult> resumed;
            synchronized (run) {
                resumed = executor.submit(() -> operations.apply(held.path, Operation.RESUME));
                assertThrows(TimeoutException.class, () -> resumed.get(300, TimeUnit.MILLISECONDS),
                        "the resume ran while the run held its lock");
                assertTrue(releasedFrom.isEmpty(), "no action after release ran while the run held its lock");
            }
            assertEquals(HeldState.COMPLETED, resumed.get(5, TimeUnit.SECONDS).to());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aReleaseThatFailsPartWayStillNamesTheManagerInTheAudit() {
        Held held = inReview("INCONCLUSIVE");
        DeferredActions broken = (submission, from, completed) -> {
            throw new IllegalStateException("The action broke");
        };
        operations.setRelease(() -> new Release(stores, stores, broken, new Transitions(stores), clock));

        assertThrows(IllegalStateException.class, () -> operations.apply(held.path, Operation.RELEASE));

        assertEquals("releasing", held.state, "a release that stopped half way");
        assertEquals(List.of(new AuditRecord("release", MANAGER, "needsReview", "releasing", NOW)), held.audit);
    }

    @Test
    void aResumeOfAStoppedReleaseThatFailsAgainStillNamesTheManagerInTheAudit() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldState.RELEASING.value();
        DeferredActions broken = (submission, from, completed) -> {
            throw new IllegalStateException("The action broke");
        };
        operations.setRelease(() -> new Release(stores, stores, broken, new Transitions(stores), clock));

        assertThrows(IllegalStateException.class, () -> operations.apply(held.path, Operation.RESUME));

        assertEquals("releasing", held.state);
        assertEquals(List.of(new AuditRecord("resume", MANAGER, "releasing", "releasing", NOW)), held.audit);
    }

    /** The stores in memory, but a move of the state fails, as a store that is down: to any state, final or not. */
    private QuarantineStore storeThatCannotMove() {
        return (QuarantineStore) Proxy.newProxyInstance(QuarantineStore.class.getClassLoader(),
                new Class<?>[]{QuarantineStore.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("setState") || method.getName().equals("finish")) {
                        throw new QuarantineStoreException("The store is down");
                    }
                    try {
                        return method.invoke(stores, arguments);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Test
    void aRejectOfAStoppedReleaseThatCannotMoveTheStateWritesNoAuditRecord() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldState.RELEASING.value();
        operations.setStore(storeThatCannotMove());

        assertThrows(QuarantineStoreException.class, () -> operations.apply(held.path, Operation.REJECT));

        assertEquals("releasing", held.state);
        assertTrue(held.valuesPresent, "values kept: the reject did not happen");
        assertTrue(held.audit.isEmpty(), "audit of a reject that changed nothing");
    }

    @Test
    void aRejectWhoseDeletionFailsKeepsTheStateAndTheData() {
        Held held = inReview("INCONCLUSIVE");
        stores.failingDeletion.add(held.path);

        assertThrows(QuarantineStoreException.class, () -> operations.apply(held.path, Operation.REJECT));

        // never rejected with its data: the manager rejects it again
        assertEquals("needsReview", held.state);
        assertFalse(held.states.contains("rejected"), held.states.toString());
        assertTrue(held.valuesPresent, "values kept: the reject did not happen");
        assertTrue(held.anyBytes(), "bytes kept: the reject did not happen");
        assertTrue(held.audit.isEmpty(), "audit of a reject that changed nothing");
    }

    @Test
    void aRejectOfAStoppedReleaseWhoseDeletionFailsStaysReleasingWithItsData() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldState.RELEASING.value();
        stores.failingDeletion.add(held.path);

        assertThrows(QuarantineStoreException.class, () -> operations.apply(held.path, Operation.REJECT));

        assertEquals("releasing", held.state);
        assertTrue(held.valuesPresent, "values kept: the reject did not happen");
        assertTrue(held.anyBytes(), "bytes kept: the reject did not happen");
    }

    @Test
    void anOperationThatRanAnswersItsResultWhenItsAuditRecordCannotBeWritten() throws Exception {
        Held held = inReview("INCONCLUSIVE");
        stores.failingAudit.add(held.path);

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to());
        assertEquals("rejected", held.state);
        assertFalse(held.valuesPresent);
    }

    @Test
    void releaseAndResumeAreRefusedWhenTheFormIsGone() {
        Held held = inReview(DecisionRecord.FORM_GONE);

        assertEquals(OperationRefusedException.Reason.CONFLICT,
                refusal(() -> operations.apply(held.path, Operation.RELEASE)));
        assertEquals(OperationRefusedException.Reason.CONFLICT,
                refusal(() -> operations.apply(held.path, Operation.RESUME)));
        assertEquals("needsReview", held.state);
        assertEquals(DecisionRecord.FORM_GONE, held.decision.reason());
        assertTrue(releasedFrom.isEmpty());
    }

    @Test
    void aSubmissionWhoseFormIsGoneIsRejected() throws Exception {
        Held held = inReview(DecisionRecord.FORM_GONE);

        assertEquals(HeldState.REJECTED, operations.apply(held.path, Operation.REJECT).to());
        assertFalse(held.valuesPresent);
        assertFalse(held.anyBytes());
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

    // --- the policy of the form: a reject that releases the values alone ---

    private static final String SCAN_STATUS = "documentScanStatus";

    private static FormPolicy policy(OnFail onFail, boolean scanStatusTaken) {
        return new FormPolicy(onFail, SCAN_STATUS, "clean", "failed", false, scanStatusTaken);
    }

    /** A values release that stopped at a failed action: the bytes are gone, the values wait for a resume. */
    private Held valuesReleaseAfterAFailedAction() {
        Held held = afterAFailedAction();
        held.valuesOnly = true;
        held.bytes.set(0, null);
        return held;
    }

    @Test
    void aRejectUnderReleaseValuesDeletesTheBytesThenReleasesTheValuesAlone() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, false);
        Held held = inReview("INCONCLUSIVE");

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.COMPLETED, result.to());
        assertEquals(List.of("releasing", "completed"), held.states);
        assertEquals(List.of(0), releasedFrom);
        assertTrue(released.get(0).files().isEmpty(), "a file reached the actions after release");
        assertEquals(List.of("failed"), released.get(0).parameters().get(SCAN_STATUS));
        assertTrue(held.valuesOnly);
        assertFalse(held.anyBytes());
        assertEquals(new DecisionRecord("INCONCLUSIVE", "item0", "remoteCheck", SHA, null, NOW), held.decision,
                "the decision keeps what sent the submission to review");
        assertEquals(List.of(new AuditRecord("reject", MANAGER, "needsReview", "completed", NOW)), held.audit);
    }

    @Test
    void aRejectWhoseValuesReleaseFailsToSaveChangesNothingAndASecondRejectReleasesTheValues() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, false);
        Held held = inReview("INCONCLUSIVE");
        stores.failingValuesRelease.add(held.path);
        String path = held.path;

        assertThrows(QuarantineStoreException.class, () -> operations.apply(path, Operation.REJECT));

        assertEquals("needsReview", held.state);
        assertTrue(held.anyBytes(), "bytes deleted by a save that failed");
        assertFalse(held.valuesOnly);
        assertTrue(held.valuesPresent);
        assertEquals("INCONCLUSIVE", held.decision.reason());
        assertTrue(released.isEmpty());
        assertTrue(held.audit.isEmpty(), "an audit record of a reject that changed nothing");

        stores.failingValuesRelease.clear();
        OperationResult result = operations.apply(path, Operation.REJECT);

        assertEquals(HeldState.COMPLETED, result.to());
        assertTrue(released.get(0).files().isEmpty());
        assertEquals(List.of("failed"), released.get(0).parameters().get(SCAN_STATUS));
    }

    @Test
    void aRejectAfterAPartialReleaseOfTheFilesIsAPlainReject() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, false);
        Held held = afterAFailedAction();

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to(), "an action already received the files marked clean");
        assertTrue(released.isEmpty(), "the reject ran the remaining actions with no file");
        assertFalse(held.valuesOnly);
        assertFalse(held.valuesPresent);
    }

    @Test
    void aRejectAfterAValuesReleaseFailedAtItsFirstActionRejects() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, false);
        Held held = valuesReleaseAfterAFailedAction();
        held.completedActions = 0;

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to(), "the values already went out alone once");
        assertTrue(released.isEmpty());
    }

    @Test
    void aRejectUnderTheDefaultPolicyRejects() throws Exception {
        Held held = inReview("INCONCLUSIVE");

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to());
        assertTrue(released.isEmpty());
        assertFalse(held.valuesOnly);
    }

    @Test
    void aRejectOfAStoppedReleaseUnderReleaseValuesRejects() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, false);
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldState.RELEASING.value();

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to());
        assertTrue(released.isEmpty());
    }

    @Test
    void aManagerReleaseAfterAFailedValuesReleaseHasNoFileToRelease() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, false);
        Held held = valuesReleaseAfterAFailedAction();

        assertEquals(OperationRefusedException.Reason.CONFLICT,
                refusal(() -> operations.apply(held.path, Operation.RELEASE)));
        OperationResult result = operations.apply(held.path, Operation.RESUME);

        assertEquals(HeldState.COMPLETED, result.to());
        assertEquals(List.of(1), releasedFrom, "the resume starts at the failed action");
        assertTrue(released.get(0).files().isEmpty(), "a resume released a file");
        assertEquals(List.of("failed"), released.get(0).parameters().get(SCAN_STATUS));
    }

    @Test
    void aResumeAfterAFailedValuesReleaseHasNoFileEvenUnderTheDefaultPolicy() throws Exception {
        Held held = valuesReleaseAfterAFailedAction();

        OperationResult result = operations.apply(held.path, Operation.RESUME);

        assertEquals(HeldState.COMPLETED, result.to());
        assertTrue(released.get(0).files().isEmpty());
    }

    @Test
    void aRejectAfterAFailedValuesReleaseRejects() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, false);
        Held held = valuesReleaseAfterAFailedAction();

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to());
        assertTrue(released.isEmpty(), "the reject ran the actions again");
        assertFalse(held.valuesPresent);
    }

    @Test
    void aFieldNamedLikeTheScanStatusRefusesAReleaseAndAResume() {
        policy = policy(OnFail.RELEASE_VALUES, true);
        Held held = inReview("INCONCLUSIVE");
        Held failed = afterAFailedAction();

        assertEquals(OperationRefusedException.Reason.CONFLICT,
                refusal(() -> operations.apply(held.path, Operation.RELEASE)));
        assertEquals(OperationRefusedException.Reason.CONFLICT,
                refusal(() -> operations.apply(failed.path, Operation.RESUME)));
        assertEquals("needsReview", held.state);
        assertEquals("needsReview", failed.state);
        assertTrue(held.anyBytes());
        assertTrue(released.isEmpty());
        assertTrue(held.audit.isEmpty());
    }

    @Test
    void aFieldNamedLikeTheScanStatusMakesARejectUnderReleaseValuesAPlainReject() throws Exception {
        policy = policy(OnFail.RELEASE_VALUES, true);
        Held held = inReview("INCONCLUSIVE");

        OperationResult result = operations.apply(held.path, Operation.REJECT);

        assertEquals(HeldState.REJECTED, result.to());
        assertTrue(released.isEmpty(), "the status was written over a field");
        assertFalse(held.valuesPresent);
    }

    @Test
    void aFieldNamedLikeTheScanStatusLetsAPlainRejectThrough() throws Exception {
        policy = policy(OnFail.REJECT, true);
        Held held = inReview("INCONCLUSIVE");

        assertEquals(HeldState.REJECTED, operations.apply(held.path, Operation.REJECT).to());
    }
}
