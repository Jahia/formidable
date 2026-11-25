package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.quarantine.check.AllowListQualifier;
import org.jahia.modules.formidable.quarantine.chain.ChainCheck;
import org.jahia.modules.formidable.quarantine.chain.OutcomeKey;
import org.jahia.modules.formidable.quarantine.chain.RecordedOutcome;
import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.OnInconclusive;
import org.jahia.modules.formidable.quarantine.api.OnUnavailable;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.RunReport;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.modules.formidable.quarantine.state.Backoff;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.state.IllegalTransitionException;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.HeldStates;
import org.jahia.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;
import org.jahia.modules.formidable.quarantine.store.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run of the checks over held submissions, with the two stores in memory, the marker checks of
 * {@link MarkerQualifier}, and a recording double of the engine's {@link DeferredActions}: each decision of the
 * chain leads to its state, the release re-hashes the bytes, checkpoints each action, keeps the index of a failure,
 * and leaves no value and no byte behind.
 */
class QuarantineRunTest {

    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final String OTHER_FORM = "6a1f0c3e-0000-4000-8000-000000000002";
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private InMemoryQuarantine stores;
    private MarkerQualifier.Calls calls;
    private MarkerQualifier.Outage outage;
    private final Map<String, List<CheckSettings>> lists = new HashMap<>();
    private final List<String> broken = new ArrayList<>();
    /** The forms deleted since their submissions were held. */
    private final List<String> gone = new ArrayList<>();
    /** What the double of the engine received: the files of each run, and the index each run started from. */
    private final List<HeldSubmission> released = new ArrayList<>();
    private final List<Integer> releasedFrom = new ArrayList<>();
    private DeferredActions actions;
    private QuarantineRun run;

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        calls = new MarkerQualifier.Calls();
        outage = new MarkerQualifier.Outage();
        actions = twoActions();
        lists.put(FORM, List.of(remote("remoteCheck")));
        run = newRun(RunSettings.DEFAULT);
    }

    private QuarantineRun newRun(RunSettings settings) {
        QuarantineRun created = new QuarantineRun(formId -> {
            if (broken.contains(formId)) {
                throw new RepositoryException("The list of form " + formId + " cannot be read");
            }
            if (gone.contains(formId)) {
                throw new ItemNotFoundException(formId);
            }
            return lists.getOrDefault(formId, List.of());
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        created.setStore(stores);
        created.setCheckStore(stores);
        created.setActions((submission, from, completed) -> actions.run(submission, from, completed));
        created.addQualifier(MarkerQualifier.remote(calls, outage));
        created.addQualifier(MarkerQualifier.inProcess(calls, outage));
        created.configure(config(settings));
        return created;
    }

    private static RunConfig config(RunSettings settings) {
        return new RunConfig() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RunConfig.class;
            }

            @Override
            public boolean allowInProcessFirst() {
                return settings.allowInProcessFirst();
            }

            @Override
            public long backoffInitialSeconds() {
                return settings.backoff().delay(1).toSeconds();
            }

            @Override
            public long backoffCapSeconds() {
                return settings.backoff().delay(Integer.MAX_VALUE).toSeconds();
            }

            @Override
            public int maxAttempts() {
                return settings.backoff().maxAttempts();
            }
        };
    }

    /** Two actions after release, each checkpointed. */
    private DeferredActions twoActions() {
        return (submission, from, completed) -> {
            released.add(submission);
            releasedFrom.add(from);
            for (int index = from; index < 2; index++) {
                completed.accept(index);
            }
        };
    }

    private static CheckSettings remote(String name) {
        return CheckSettings.withDefaults(MarkerQualifier.REMOTE_TYPE, name);
    }

    private static CheckSettings inProcess(String name) {
        return CheckSettings.withDefaults(MarkerQualifier.IN_PROCESS_TYPE, name);
    }

    private RunReport runNow() throws Exception {
        return run.run(InMemoryQuarantine.SITE, null, false);
    }

    private RunReport runIgnoringSchedule() throws Exception {
        return run.run(InMemoryQuarantine.SITE, null, true);
    }

    @Test
    void everyCheckPassesAndTheSubmissionIsReleasedThenCompleted() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");

        RunReport report = runNow();

        assertEquals(HeldStates.COMPLETED, held.state);
        assertEquals(List.of(HeldStates.CHECKING, HeldStates.RELEASING, HeldStates.COMPLETED), held.states);
        assertEquals(1, released.size());
        assertEquals("VERDICT:PASS\nA note",
                new String(released.get(0).files().get(0).data(), StandardCharsets.UTF_8));
        assertEquals(OutcomeRecord.PASS, held.outcomes.get("item0/remoteCheck").outcome());
        assertEquals(held.sha256s.get(0), held.outcomes.get("item0/remoteCheck").sha256());
        assertEquals(List.of(new RunReport.Processed(held.path, FORM, HeldStates.HELD, HeldStates.COMPLETED, null)),
                report.processed());
    }

    @Test
    void aCompletedSubmissionKeepsNoValueAndNoByteButKeepsItsOutcomes() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertFalse(held.valuesPresent);
        assertFalse(held.anyBytes());
        assertEquals(1, held.outcomes.size());
        assertEquals(64, held.sha256s.get(0).length());
    }

    @Test
    void aFailRejectsTheSubmissionRunsNoActionAndDeletesTheValues() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:FAIL");

        runNow();

        assertEquals(HeldStates.REJECTED, held.state);
        assertTrue(released.isEmpty());
        assertEquals("CHECK_FAILED", held.decision.reason());
        assertEquals("item0", held.decision.itemName());
        assertEquals("remoteCheck", held.decision.checkName());
        assertEquals(OutcomeRecord.FAIL, held.outcomes.get("item0/remoteCheck").outcome());
        assertEquals("stub.marker", held.outcomes.get("item0/remoteCheck").reason());
        assertFalse(held.valuesPresent);
        assertFalse(held.anyBytes());
    }

    @Test
    void aReleaseWhoseDeletionFailsStaysReleasingWithItsData() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS");
        stores.failingDeletion.add(held.path);

        runNow();

        // never completed with its data: a manager resumes or rejects a release that stopped in releasing
        assertEquals(HeldStates.RELEASING, held.state);
        assertFalse(held.states.contains(HeldStates.COMPLETED), held.states.toString());
        assertTrue(held.valuesPresent);
        assertTrue(held.anyBytes());
    }

    @Test
    void aRejectWhoseDeletionFailsStaysCheckingWithItsData() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:FAIL");
        stores.failingDeletion.add(held.path);

        runNow();

        // never rejected with its data: the next pass of the run decides again
        assertEquals(HeldStates.CHECKING, held.state);
        assertFalse(held.states.contains(HeldStates.REJECTED), held.states.toString());
        assertTrue(held.valuesPresent);
        assertTrue(held.anyBytes());
    }

    @Test
    void anInconclusiveAnswerGoesToReviewByDefaultAndKeepsTheValues() throws Exception {
        Held held = stores.hold(FORM, "INCONCLUSIVE");

        runNow();

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals("INCONCLUSIVE", held.decision.reason());
        assertTrue(held.valuesPresent);
        assertTrue(released.isEmpty());
    }

    @Test
    void anInconclusiveAnswerRejectsWhenTheCheckSaysSo() throws Exception {
        lists.put(FORM, List.of(new CheckSettings(MarkerQualifier.REMOTE_TYPE, "remoteCheck",
                OnInconclusive.REJECT, OnUnavailable.RETRY)));
        Held held = stores.hold(FORM, "INCONCLUSIVE");

        runNow();

        assertEquals(HeldStates.REJECTED, held.state);
    }

    @Test
    void anOutageKeepsTheSubmissionCheckingAndSchedulesTheNextAttempt() throws Exception {
        Held held = stores.hold(FORM, "DOWN");

        runNow();

        assertEquals(HeldStates.CHECKING, held.state);
        assertEquals(1, held.attempts);
        assertEquals(NOW.plus(Duration.ofMinutes(1)), held.nextAttempt);
        OutcomeRecord outcome = held.outcomes.get("item0/remoteCheck");
        assertEquals(OutcomeRecord.FAILED, outcome.outcome());
        assertTrue(outcome.retryable());
        assertEquals(1, outcome.attempts());
        assertTrue(released.isEmpty());
    }

    @Test
    void aSubmissionWhoseNextAttemptIsAheadIsNotDueUnlessTheScheduleIsIgnored() throws Exception {
        Held held = stores.hold(FORM, "DOWN");
        runNow();

        assertTrue(runNow().processed().isEmpty());
        assertEquals(1, held.outcomes.get("item0/remoteCheck").attempts());

        runIgnoringSchedule();
        assertEquals(2, held.outcomes.get("item0/remoteCheck").attempts());
        assertEquals(2, held.attempts);
        assertEquals(HeldStates.CHECKING, held.state);
    }

    @Test
    void theAttemptsExhaustedSendTheSubmissionToReview() throws Exception {
        run = newRun(new RunSettings(false, new Backoff(Duration.ofMinutes(1), Duration.ofHours(1), 2)));
        Held held = stores.hold(FORM, "DOWN");

        runIgnoringSchedule();
        assertEquals(HeldStates.CHECKING, held.state);
        runIgnoringSchedule();

        // the second retryable failure since the last result reaches maxAttempts
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals("ATTEMPTS_EXHAUSTED", held.decision.reason());
        assertNull(held.nextAttempt);
    }

    @Test
    void anOutageLiftedReleasesTheSubmission() throws Exception {
        Held held = stores.hold(FORM, "DOWN");
        runNow();

        outage.lift();
        runIgnoringSchedule();

        assertEquals(HeldStates.COMPLETED, held.state);
    }

    @Test
    void aPendingAnswerIsPolledAtTheNextRun() throws Exception {
        Held held = stores.hold(FORM, "PENDING:1");

        runNow();
        assertEquals(HeldStates.CHECKING, held.state);
        assertNotNull(held.outcomes.get("item0/remoteCheck").ticket());

        runIgnoringSchedule();
        assertEquals(HeldStates.CHECKING, held.state);
        runIgnoringSchedule();
        assertEquals(HeldStates.COMPLETED, held.state);
        assertEquals(List.of(MarkerQualifier.Kind.SUBMIT, MarkerQualifier.Kind.POLL, MarkerQualifier.Kind.POLL),
                calls.calls().stream().map(MarkerQualifier.Call::kind).toList());
    }

    @Test
    void aCheckThatStaysPendingSendsTheSubmissionToReviewOnceTheAttemptsAreExhausted() throws Exception {
        run = newRun(new RunSettings(false, new Backoff(Duration.ofMinutes(1), Duration.ofHours(1), 2)));
        Held held = stores.hold(FORM, "PENDING:1000");

        runIgnoringSchedule();
        assertEquals(HeldStates.CHECKING, held.state);
        runIgnoringSchedule();

        // two runs in a row brought no new answer: the run gives up, as it does on failures
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals("ATTEMPTS_EXHAUSTED", held.decision.reason());
        assertEquals("item0", held.decision.itemName());
        assertEquals("remoteCheck", held.decision.checkName());
        assertNull(held.nextAttempt);
        assertTrue(held.valuesPresent);
    }

    @Test
    void aRunThatBringsANewPassStartsTheAttemptsOver() throws Exception {
        run = newRun(new RunSettings(false, new Backoff(Duration.ofMinutes(1), Duration.ofHours(1), 3)));
        lists.put(FORM, List.of(remote("firstCheck"), remote("secondCheck")));
        // each check answers with a ticket, pending at its first poll, and passes at its second poll
        Held held = stores.hold(FORM, "PENDING:1");

        runIgnoringSchedule();
        runIgnoringSchedule();
        assertEquals(2, held.attempts);
        // the first check passes, then the second answers with a ticket: a new PASS, so the count starts over
        runIgnoringSchedule();
        assertEquals(1, held.attempts);
        assertEquals(HeldStates.CHECKING, held.state);
        runIgnoringSchedule();
        runIgnoringSchedule();

        assertEquals(HeldStates.COMPLETED, held.state);
    }

    @Test
    void aCheckThatThrowsCountsAsARetryableFailure() throws Exception {
        ScriptedQualifier scripted = new ScriptedQualifier();
        scripted.answers.add(new IllegalStateException("the engine of the check broke"));
        run.addQualifier(scripted);
        lists.put(FORM, List.of(CheckSettings.withDefaults(ScriptedQualifier.NODE_TYPE, "scriptedCheck")));
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.CHECKING, held.state);
        OutcomeRecord outcome = held.outcomes.get("item0/scriptedCheck");
        assertEquals(OutcomeRecord.FAILED, outcome.outcome());
        assertEquals(SubmissionRun.CHECK_ERROR, outcome.reason());
        assertTrue(outcome.retryable());
        assertEquals(1, outcome.attempts());
        assertTrue(released.isEmpty());
    }

    @Test
    void aMetadataCheckReadsTheMetadataAndNoByte() throws Exception {
        MetadataProbe probe = new MetadataProbe();
        run.addQualifier(probe);
        // the remote check applies to the text file alone: its submit of the text shares a batch, and so the open
        // session of the bytes, with the submit of the metadata check for the image
        lists.put(FORM, List.of(remote("remoteCheck"),
                CheckSettings.withDefaults(MetadataProbe.NODE_TYPE, "metadataCheck")));
        Held held = stores.hold(FORM, "VERDICT:PASS");
        stores.addItem(held, "scan.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        runNow();

        assertEquals(HeldStates.COMPLETED, held.state);
        assertEquals(List.of("scan.png image/png 10", "item0.txt text/plain 10"), probe.seen);
        assertEquals(2, probe.refusedOpens, "a metadata check read a byte while the session of the batch was open");
        assertEquals(OutcomeRecord.PASS, held.outcomes.get("item0/metadataCheck").outcome());
        assertEquals(OutcomeRecord.PASS, held.outcomes.get("item1/metadataCheck").outcome());
    }

    @Test
    void aListOfMetadataChecksOpensNoSessionOfTheBytes() throws Exception {
        MetadataProbe probe = new MetadataProbe();
        run.addQualifier(probe);
        lists.put(FORM, List.of(CheckSettings.withDefaults(MetadataProbe.NODE_TYPE, "metadataCheck")));
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.COMPLETED, held.state);
        assertEquals(0, stores.itemSessions, "a session opened for a metadata check");
    }

    @Test
    void aLargeFileStreamsToTheCheck() throws Exception {
        StreamingQualifier streaming = new StreamingQualifier();
        run.addQualifier(streaming);
        lists.put(FORM, List.of(CheckSettings.withDefaults(StreamingQualifier.NODE_TYPE, "streamingCheck")));
        // its stream refuses readAllBytes, through the scope of the store too: a check or a store that reads the
        // stream whole fails the test. The release reads the item into memory for the engine, which this does not
        // cover: the hash of the release is computed over the bytes it releases.
        Held held = stores.holdLarge(FORM, new InMemoryQuarantine.LargeBytes(8L * 1024 * 1024, (byte) 'x'));

        runNow();

        assertEquals(HeldStates.COMPLETED, held.state);
        assertEquals(List.of(held.sha256s.get(0)), streaming.hashes);
        assertEquals(1, stores.itemSessions, "one session for the check, none for the release");
    }

    @Test
    void aStreamOrAFileKeptByACheckCannotBeReadOnceTheBatchOfCallsEnded() throws Exception {
        StreamingQualifier streaming = new StreamingQualifier();
        streaming.keepStream = true;
        run.addQualifier(streaming);
        lists.put(FORM, List.of(CheckSettings.withDefaults(StreamingQualifier.NODE_TYPE, "streamingCheck")));
        stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertFalse(stores.itemSessionOpen);
        InputStream kept = streaming.kept;
        assertNotNull(kept);
        assertThrows(IOException.class, kept::read);
        QuarantinedFile file = streaming.file;
        assertThrows(IOException.class, file::open);
    }

    @Test
    void aMetadataCheckIsHandedNoStreamEvenWhileTheBytesAreOpen() throws Exception {
        ItemAccess access = new ItemAccess();
        access.begin(itemName -> new ByteArrayInputStream("VERDICT:PASS".getBytes(StandardCharsets.UTF_8)));
        ItemFile file = new ItemFile("/sites/site/held-0",
                new ItemRecord("item0", "document", "item0.txt", "text/plain", 12, "aaa", true), access);

        try (InputStream in = SubmissionRun.fileFor(MarkerQualifier.remote(calls, outage), file).open()) {
            assertEquals('V', in.read());
        }
        QuarantinedFile metadataOnly = SubmissionRun.fileFor(new MetadataProbe(), file);
        assertEquals("item0.txt", metadataOnly.fileName());
        assertThrows(IOException.class, metadataOnly::open);
    }

    @Test
    void anAllowListJudgesEachFileOfTheSameBytesByItsOwnNameAndType() throws Exception {
        run.addQualifier(new AllowListQualifier());
        Map<String, List<String>> allowed = Map.of("extensions", List.of("pdf"), "mimeTypes", List.of("application/pdf"));
        lists.put(FORM, List.of(new CheckSettings(AllowListQualifier.NODE_TYPE, "allowList", OnInconclusive.REVIEW,
                OnUnavailable.RETRY, allowed)));
        Held held = stores.hold(FORM);
        byte[] same = "%PDF-1.7 the same bytes".getBytes(StandardCharsets.UTF_8);
        stores.addItem(held, "doc.pdf", "application/pdf", same);
        stores.addItem(held, "doc.exe", "application/x-msdownload", same);

        runNow();

        assertEquals(HeldStates.REJECTED, held.state);
        assertEquals("CHECK_FAILED", held.decision.reason());
        assertEquals("item1", held.decision.itemName());
        assertEquals(OutcomeRecord.PASS, held.outcomes.get("item0/allowList").outcome());
        assertEquals(OutcomeRecord.FAIL, held.outcomes.get("item1/allowList").outcome());
        assertTrue(released.isEmpty());
    }

    @Test
    void aStoredMetadataVerdictCountsOnlyForTheItemItWasRecordedUnder() throws Exception {
        Held held = stores.hold(FORM);
        byte[] same = "the same bytes".getBytes(StandardCharsets.UTF_8);
        stores.addItem(held, "doc.pdf", "application/pdf", same);
        stores.addItem(held, "doc.exe", "application/x-msdownload", same);
        ItemAccess access = new ItemAccess();
        List<ItemFile> files = new ArrayList<>();
        for (ItemRecord item : stores.heldRecord(held.path).orElseThrow().items()) {
            files.add(new ItemFile(held.path, item, access));
        }
        ChainCheck allowList = new ChainCheck(CheckSettings.withDefaults(AllowListQualifier.NODE_TYPE, "allowList"),
                new AllowListQualifier());
        ChainCheck scan = new ChainCheck(remote("remoteCheck"), MarkerQualifier.remote(calls, outage));
        String sha256 = held.sha256s.get(0);
        List<OutcomeRecord> stored = List.of(
                new OutcomeRecord("item0", "allowList", sha256, OutcomeRecord.PASS, "allowList.allowed", false, null, 0,
                        NOW),
                new OutcomeRecord("item0", "remoteCheck", sha256, OutcomeRecord.PASS, "stub.marker", false, null, 0,
                        NOW));

        Map<OutcomeKey, RecordedOutcome> recorded = OutcomeCodec.recorded(stored, files, List.of(allowList, scan));

        assertNotNull(recorded.get(OutcomeKey.of(files.get(0), allowList)));
        assertNull(recorded.get(OutcomeKey.of(files.get(1), allowList)), "doc.exe took the verdict of doc.pdf");
        assertNotNull(recorded.get(OutcomeKey.of(files.get(1), scan)), "the same bytes share a verdict on the bytes");
    }

    @Test
    void anAllowListWithNoListSendsTheSubmissionToReviewAndKeepsItsData() throws Exception {
        run.addQualifier(new AllowListQualifier());
        lists.put(FORM, List.of(CheckSettings.withDefaults(AllowListQualifier.NODE_TYPE, "allowList")));
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals("UNAVAILABLE", held.decision.reason());
        assertEquals("allowList", held.decision.checkName());
        assertTrue(held.valuesPresent);
        assertTrue(held.anyBytes());
        assertTrue(released.isEmpty());
    }

    @Test
    void aFileThatTheReadOfTheReleaseMissesRefusesTheRelease() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS", "VERDICT:PASS\nA second file");
        // the record still says the bytes are there, and the read for the engine finds no binary
        stores.lostOnRead.add(held.path + "/item1");

        runNow();

        assertTrue(released.isEmpty(), "the actions after release ran without the second file");
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(DecisionRecord.INTEGRITY_FAILED, held.decision.reason());
        assertEquals(Release.ITEM_COUNT, held.decision.itemName());
        assertTrue(held.valuesPresent);
    }

    @Test
    void theReleaseComparesTheCountAndTheHashOfTheBytesItReleases() {
        byte[] bytes = "VERDICT:PASS".getBytes(StandardCharsets.UTF_8);
        ItemRecord item = new ItemRecord("item0", "document", "a.txt", "text/plain", bytes.length,
                Sha256.hex(bytes), true);
        HeldRecord record = new HeldRecord("/p", FORM, InMemoryQuarantine.SITE, HeldStates.CHECKING, NOW, 0, null, 0,
                List.of(), List.of(), true, List.of(item), NOW);

        assertNull(Release.firstChanged(record, submissionOf(new SubmittedFile("document", "a.txt", "text/plain",
                bytes))));
        assertEquals(Release.ITEM_COUNT, Release.firstChanged(record, submissionOf()).name());
        assertEquals("item0", Release.firstChanged(record, submissionOf(new SubmittedFile("document", "a.txt",
                "text/plain", "VERDICT:PASS but changed".getBytes(StandardCharsets.UTF_8)))).name());
    }

    private static HeldSubmission submissionOf(SubmittedFile... files) {
        return new HeldSubmission(FORM, InMemoryQuarantine.SITE, Locale.ENGLISH, Map.of(), Map.of(), List.of(files),
                new SubmissionOrigin(null, null, null));
    }

    @Test
    void aPendingAnswerWithNoTicketIsAFailureThatNoRetryCanMend() {
        RecordedOutcome recorded = SubmissionRun.recordOf(Outcome.PENDING, null, 3);

        assertEquals(new Outcome.Failed(false, SubmissionRun.CHECK_ERROR), recorded.outcome());
        assertNull(recorded.ticket());
        assertEquals(4, recorded.attempts());
    }

    @Test
    void anItemWhoseBytesAreGoneRefusesTheRelease() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS", "VERDICT:PASS");
        // the bytes of the second file go after intake: the checks see the first file only
        held.bytes.set(1, null);

        runNow();

        assertTrue(released.isEmpty(), "the actions after release ran without the second file");
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(DecisionRecord.INTEGRITY_FAILED, held.decision.reason());
        assertEquals("item1", held.decision.itemName());
        assertTrue(held.valuesPresent);
    }

    @Test
    void aReleaseFromAStateItCannotLeaveRunsNoAction() {
        Held held = stores.hold(FORM, "VERDICT:PASS");
        held.state = HeldStates.NEEDS_REVIEW;
        Release release = run.release();

        assertThrows(IllegalTransitionException.class, () -> release.release(held.path, HeldState.NEEDS_REVIEW));

        assertTrue(released.isEmpty(), "the actions after release ran from state needsReview");
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
    }

    /**
     * A remote check that hashes the stream of each file a buffer at a time, and passes the file when the hash is the
     * SHA-256 of intake. It may keep the stream and the file it was handed.
     */
    private static final class StreamingQualifier implements Qualifier {

        static final String NODE_TYPE = "test:streamingCheck";

        final List<String> hashes = new ArrayList<>();
        boolean keepStream;
        InputStream kept;
        QuarantinedFile file;

        @Override
        public String getNodeType() {
            return NODE_TYPE;
        }

        @Override
        public Scope scope() {
            return Scope.REMOTE;
        }

        @Override
        public boolean appliesTo(QuarantinedFile file, CheckSettings settings) {
            return true;
        }

        @Override
        public Submitted submit(QuarantinedFile handed, CheckSettings settings) {
            file = handed;
            try (InputStream in = handed.open()) {
                String hash = Sha256.hex(in);
                if (keepStream) {
                    // closed here, then ended with the session: the read after the call must still throw
                    kept = in;
                }
                hashes.add(hash);
                return Submitted.answered(hash.equals(handed.sha256()) ? Outcome.pass("stream.ok", null)
                        : Outcome.fail("stream.differs", null));
            } catch (IOException e) {
                return Submitted.answered(new Outcome.Failed(false, "stream.unreadable"));
            }
        }

        @Override
        public Outcome poll(String ticket) {
            throw new AssertionError("A streaming check answers at once");
        }
    }

    /** A metadata check that records what it saw of each file, tries to open it, and passes it. */
    private static final class MetadataProbe implements Qualifier {

        static final String NODE_TYPE = "test:metadataCheck";

        final List<String> seen = new ArrayList<>();
        int refusedOpens;

        @Override
        public String getNodeType() {
            return NODE_TYPE;
        }

        @Override
        public Scope scope() {
            return Scope.METADATA;
        }

        @Override
        public boolean appliesTo(QuarantinedFile file, CheckSettings settings) {
            return true;
        }

        @Override
        public Submitted submit(QuarantinedFile file, CheckSettings settings) {
            seen.add(file.fileName() + " " + file.mimeType() + " " + file.size());
            try (InputStream in = file.open()) {
                in.read();
            } catch (IOException expected) {
                refusedOpens++;
            }
            return Submitted.answered(Outcome.pass("metadata.ok", null));
        }

        @Override
        public Outcome poll(String ticket) {
            throw new AssertionError("A metadata check answers at once");
        }
    }

    /** A remote check that answers what the test scripts, one answer per call; a scripted exception is thrown. */
    private static final class ScriptedQualifier implements Qualifier {

        static final String NODE_TYPE = "test:scriptedCheck";

        final Deque<Object> answers = new ArrayDeque<>();

        @Override
        public String getNodeType() {
            return NODE_TYPE;
        }

        @Override
        public Scope scope() {
            return Scope.REMOTE;
        }

        @Override
        public boolean appliesTo(QuarantinedFile file, CheckSettings settings) {
            return true;
        }

        @Override
        public Submitted submit(QuarantinedFile file, CheckSettings settings) {
            return (Submitted) next();
        }

        @Override
        public Outcome poll(String ticket) {
            return (Outcome) next();
        }

        private Object next() {
            Object answer = answers.removeFirst();
            if (answer instanceof RuntimeException e) {
                throw e;
            }
            return answer;
        }
    }

    @Test
    void aFileNoCheckAppliesToIsWithheldAndNoCheckIsCalled() throws Exception {
        Held held = stores.hold(FORM);
        stores.addItem(held, "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        runNow();

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals("FILE_WITHHELD", held.decision.reason());
        assertTrue(calls.calls().isEmpty());
        assertTrue(held.anyBytes());
    }

    @Test
    void twoChecksPassInTheOrderOfTheListAndTheActionsRunOnce() throws Exception {
        lists.put(FORM, List.of(remote("firstCheck"), remote("secondCheck")));
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();
        runIgnoringSchedule();

        assertEquals(List.of("firstCheck", "secondCheck"),
                calls.calls().stream().map(MarkerQualifier.Call::checkName).toList());
        assertEquals(1, released.size());
        assertEquals(HeldStates.COMPLETED, held.state);
    }

    @Test
    void aFailOfTheFirstCheckNeverReachesTheSecond() throws Exception {
        lists.put(FORM, List.of(remote("firstCheck"), remote("secondCheck")));
        Held held = stores.hold(FORM, "VERDICT:FAIL");

        runNow();

        assertEquals(HeldStates.REJECTED, held.state);
        assertEquals(1, calls.callsOf("firstCheck").size());
        assertTrue(calls.callsOf("secondCheck").isEmpty());
    }

    @Test
    void anInProcessCheckPlacedFirstSendsToReviewAndNoCheckIsCalled() throws Exception {
        lists.put(FORM, List.of(inProcess("inProcessCheck"), remote("remoteCheck")));
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals("IN_PROCESS_FIRST", held.decision.reason());
        assertEquals("inProcessCheck", held.decision.checkName());
        assertTrue(calls.calls().isEmpty());
    }

    @Test
    void anInProcessCheckPlacedFirstRunsWhenTheOperatorAllowsIt() throws Exception {
        run = newRun(new RunSettings(true, Backoff.DEFAULT));
        lists.put(FORM, List.of(inProcess("inProcessCheck"), remote("remoteCheck")));
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.COMPLETED, held.state);
    }

    @Test
    void aCheckThatNoQualifierServesSendsToReview() throws Exception {
        lists.put(FORM, List.of(CheckSettings.withDefaults("other:check", "unknownCheck")));
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals("QUALIFIER_MISSING", held.decision.reason());
    }

    @Test
    void aHashDifferenceRefusesTheReleaseAndNeverCallsTheActions() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        // the bytes change after intake, and still carry the marker: every check passes them
        held.bytes.set(0, "VERDICT:PASS\nchanged after intake".getBytes(StandardCharsets.UTF_8));

        runNow();

        assertTrue(released.isEmpty(), "the actions after release ran on bytes that changed after intake");
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(DecisionRecord.INTEGRITY_FAILED, held.decision.reason());
        assertEquals("item0", held.decision.itemName());
        assertTrue(held.valuesPresent);
        assertTrue(held.anyBytes());
    }

    @Test
    void aCheckpointIsWrittenAfterEachAction() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(List.of(1, 2), held.checkpoints);
        assertEquals(2, held.completedActions);
        assertEquals(List.of(0), releasedFrom);
    }

    @Test
    void aFailedActionStopsAtReviewWithTheIndexToResumeFrom() throws Exception {
        actions = (submission, from, completed) -> {
            completed.accept(0);
            throw new DeferredActionException("Action 1 failed", 1, 2, null);
        };
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(1, held.completedActions);
        assertEquals(DecisionRecord.ACTION_FAILED, held.decision.reason());
        assertEquals(1, held.decision.failedAction());
        assertTrue(held.valuesPresent, "the values wait for the resume");
        assertTrue(held.anyBytes());
    }

    @Test
    void aReleaseOfAReleasingSubmissionResumesFromTheRecordedIndex() throws Exception {
        actions = twoActions();
        Held held = stores.hold(FORM, "VERDICT:PASS");
        held.state = HeldStates.RELEASING;
        held.completedActions = 1;

        run.release().release(held.path, HeldState.RELEASING);

        assertEquals(List.of(1), releasedFrom);
        assertEquals(HeldStates.COMPLETED, held.state);
    }

    @Test
    void aRefusedListKeepsTheIndexOfTheCheckpoint() throws Exception {
        actions = (submission, from, completed) -> {
            throw new DeferredActionException("The list was refused", DeferredActionException.NO_ACTION,
                    DeferredActionException.NO_ACTION, null);
        };
        Held held = stores.hold(FORM, "VERDICT:PASS");

        runNow();

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(0, held.completedActions);
        assertEquals(0, held.decision.failedAction());
    }

    @Test
    void aFailingSubmissionDoesNotStopThePass() throws Exception {
        lists.put(OTHER_FORM, List.of(remote("remoteCheck")));
        Held first = stores.hold(FORM, "VERDICT:PASS");
        Held second = stores.hold(FORM, "VERDICT:PASS");
        Held third = stores.hold(OTHER_FORM, "VERDICT:PASS");
        stores.broken.add(first.path);
        broken.add(OTHER_FORM);

        RunReport report = runNow();

        assertEquals(3, report.processed().size());
        assertNotNull(report.processed().get(0).error());
        assertEquals(HeldStates.COMPLETED, second.state);
        assertNull(report.processed().get(1).error());
        assertNotNull(report.processed().get(2).error());
        assertEquals(HeldStates.CHECKING, third.state);
    }

    @Test
    void anUnexpectedFailureIsAnsweredByItsClassAloneAndNeverQuotesItsMessage() throws Exception {
        stores.hold(FORM, "VERDICT:PASS");
        actions = (submission, from, completed) -> {
            throw new IllegalStateException("the action read the value Ada");
        };

        RunReport report = runNow();

        assertEquals("IllegalStateException", report.processed().get(0).error());
    }

    @Test
    void aSubmissionWhoseFormIsGoneGoesToReviewAndCallsNoCheck() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS");
        gone.add(FORM);

        RunReport report = runNow();

        assertNull(report.processed().get(0).error());
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(DecisionRecord.FORM_GONE, held.decision.reason());
        assertTrue(calls.calls().isEmpty());
        assertTrue(released.isEmpty());
    }

    @Test
    void aReleaseWhoseRunAuditCannotBeWrittenIsStillReportedCompleted() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS");
        stores.failingAudit.add(held.path);

        RunReport report = runNow();

        assertNull(report.processed().get(0).error());
        assertEquals(HeldStates.COMPLETED, report.processed().get(0).state());
        assertEquals(HeldStates.COMPLETED, held.state);
    }

    @Test
    void aRunRestrictedToOneFormLeavesTheOthers() throws Exception {
        lists.put(OTHER_FORM, List.of(remote("remoteCheck")));
        Held mine = stores.hold(FORM, "VERDICT:PASS");
        Held other = stores.hold(OTHER_FORM, "VERDICT:PASS");

        run.run(InMemoryQuarantine.SITE, FORM, false);

        assertEquals(HeldStates.COMPLETED, mine.state);
        assertEquals(HeldStates.HELD, other.state);
    }

    @Test
    void finalAndReviewStatesAreNeverDue() {
        for (String state : List.of(HeldStates.RELEASING, HeldStates.COMPLETED, HeldStates.REJECTED,
                HeldStates.NEEDS_REVIEW)) {
            Held held = stores.hold(FORM, "VERDICT:PASS");
            held.state = state;
            assertFalse(QuarantineRun.isDue(recordOf(held), NOW, true), state);
        }
    }

    @Test
    void theRunWritesOneAuditRecordWithNoUserWhenItReleasesRejectsOrSendsToReview() throws Exception {
        Held completed = stores.hold(FORM, "VERDICT:PASS\nOne");
        Held rejected = stores.hold(FORM, "VERDICT:FAIL\nTwo");
        Held inReview = stores.hold(FORM, "INCONCLUSIVE\nThree");

        runNow();

        assertEquals(List.of(new AuditRecord(AuditRecord.RUN, null, "held", "completed", NOW)), completed.audit);
        assertEquals(List.of(new AuditRecord(AuditRecord.RUN, null, "held", "rejected", NOW)), rejected.audit);
        assertEquals(List.of(new AuditRecord(AuditRecord.RUN, null, "held", "needsReview", NOW)), inReview.audit);
    }

    @Test
    void aSubmissionThatKeepsCheckingGetsNoAuditRecord() throws Exception {
        Held held = stores.hold(FORM, "DOWN");

        runNow();

        assertEquals(HeldStates.CHECKING, held.state);
        assertTrue(held.audit.isEmpty());
    }

    @Test
    void aReleaseThatStopsHalfWayGetsAnAuditRecordOfTheRun() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS");
        actions = (submission, from, completed) -> {
            throw new IllegalStateException("The action broke");
        };

        RunReport report = runNow();

        assertEquals("releasing", report.processed().get(0).state(), "a release that stopped half way");
        assertEquals(List.of(new AuditRecord(AuditRecord.RUN, null, "held", "releasing", NOW)), held.audit);
    }

    private HeldRecord recordOf(Held held) {
        try {
            return stores.heldRecord(held.path).orElseThrow();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
