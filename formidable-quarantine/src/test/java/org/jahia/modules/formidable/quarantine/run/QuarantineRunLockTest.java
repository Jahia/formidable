package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunInProgressException;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunStoppedException;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.RunReport;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.HeldStates;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The run takes the lock of its site in the JVM and the lock of the run in the repository before it reads, checks its
 * lease before each submission and each call, extends it at half, and releases both, with a fake repository lock: a
 * site another run holds is skipped, and nothing of it is read.
 */
class QuarantineRunLockTest {

    private static final String SITE = InMemoryQuarantine.SITE;
    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private InMemoryQuarantine stores;
    private FakeRunLock lock;
    private ManualClock clock;
    private SiteLocks siteLocks;

    /** A clock that the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = NOW;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A remote check that passes every file, and takes this long to answer. */
    private final class SlowPass implements Qualifier {
        private final Duration takes;

        SlowPass(Duration takes) {
            this.takes = takes;
        }

        @Override
        public String getNodeType() {
            return MarkerQualifier.REMOTE_TYPE;
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
            lock.calls.add("call");
            clock.advance(takes);
            return new Submitted.Answered(Outcome.pass("slow.pass", null));
        }

        @Override
        public Outcome poll(String ticket) {
            throw new UnsupportedOperationException("never pending");
        }
    }

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        lock = new FakeRunLock();
        clock = new ManualClock();
        siteLocks = new SiteLocks();
    }

    /** A run whose forms list no check, each read of a list taking this long: each due submission goes to review. */
    private QuarantineRun newRun(long leaseSeconds, Duration perSubmission) {
        return newRun(leaseSeconds, formId -> {
            clock.advance(perSubmission);
            return List.of();
        });
    }

    private QuarantineRun newRun(long leaseSeconds, CheckList checkList) {
        QuarantineRun run = new QuarantineRun(checkList, clock);
        run.setStore(stores);
        run.setCheckStore(stores);
        run.setActions((submission, from, completed) -> lock.calls.add("actions"));
        run.setRunLock(lock);
        run.setSiteLocks(siteLocks);
        run.configure(config(leaseSeconds));
        return run;
    }

    static RunConfig config(long leaseSeconds) {
        return new RunConfig() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RunConfig.class;
            }

            @Override
            public boolean allowInProcessFirst() {
                return false;
            }

            @Override
            public long backoffInitialSeconds() {
                return 60;
            }

            @Override
            public double backoffFactor() {
                return 2;
            }

            @Override
            public long backoffCapSeconds() {
                return 3600;
            }

            @Override
            public int maxAttempts() {
                return 10;
            }

            @Override
            public long periodSeconds() {
                return 60;
            }

            @Override
            public long lockLeaseSeconds() {
                return leaseSeconds;
            }
        };
    }

    private void holdSubmissions(int count) {
        for (int i = 0; i < count; i++) {
            stores.hold(FORM, "VERDICT:PASS\nA note " + i);
        }
    }

    @Test
    void theRunTakesTheLocksOfTheSiteAndReleasesThemAfterThePass() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        QuarantineRun run = newRun(600, Duration.ZERO);

        Optional<RunReport> report = run.tryRun(SITE, null, false);

        assertTrue(report.isPresent());
        assertEquals(1, report.get().processed().size());
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(List.of("take " + SITE, "release " + SITE), lock.calls);
        assertEquals(List.of(Duration.ofMinutes(10)), lock.leases);
        assertFalse(siteLocks.of(SITE).isLocked());
    }

    @Test
    void aSiteWhoseLockAnotherRunHoldsIsSkippedAndNothingIsMoved() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        lock.heldElsewhere.add(SITE);
        QuarantineRun run = newRun(600, Duration.ZERO);

        Optional<RunReport> report = run.tryRun(SITE, null, false);

        assertFalse(report.isPresent());
        assertEquals(HeldStates.HELD, held.state);
        assertEquals(List.of(), held.states);
        // a lock that was not taken is not released: the other run keeps it
        assertEquals(List.of("take " + SITE), lock.calls);
        assertFalse(siteLocks.of(SITE).isLocked());
    }

    @Test
    void aSiteThatAnotherThreadOfThisServerHoldsIsSkippedWithNoRead() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        QuarantineRun run = newRun(600, Duration.ZERO);
        ReentrantLock site = siteLocks.of(SITE);
        Thread other = new Thread(site::lock);
        other.start();
        other.join();

        Optional<RunReport> report = run.tryRun(SITE, null, false);

        assertFalse(report.isPresent());
        assertEquals(HeldStates.HELD, held.state);
        assertEquals(List.of(), lock.calls);
    }

    @Test
    void runNowAnswersARunInProgressWhenAnotherRunHoldsTheSite() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        lock.heldElsewhere.add(SITE);
        QuarantineRun run = newRun(600, Duration.ZERO);

        assertThrows(QuarantineRunInProgressException.class, () -> run.runNow(SITE, null, true));

        assertEquals(HeldStates.HELD, held.state);
    }

    @Test
    void theLocksAreReleasedWhenTheTreeCannotBeRead() throws Exception {
        QuarantineRun run = newRun(600, Duration.ZERO);
        CheckStore unreadable = mock(CheckStore.class);
        when(unreadable.forms(SITE)).thenThrow(new QuarantineStoreException("the tree cannot be read"));
        run.setCheckStore(unreadable);

        assertThrows(QuarantineStoreException.class, () -> run.tryRun(SITE, null, false));

        assertEquals(List.of("take " + SITE, "release " + SITE), lock.calls);
        assertFalse(siteLocks.of(SITE).isLocked());
    }

    @Test
    void theLeaseIsTheSettingOfTheRunAndANonPositiveOneFallsBackToTheDefault() throws Exception {
        newRun(120, Duration.ZERO).tryRun(SITE, null, false);
        newRun(0, Duration.ZERO).tryRun(SITE, null, false);

        assertEquals(List.of(Duration.ofMinutes(2), QuarantineRun.DEFAULT_LEASE), lock.leases);
    }

    @Test
    void theLeaseIsNotExtendedBeforeHalfOfItWentBy() throws Exception {
        holdSubmissions(2);
        // submissions at 0 s and 70 s of a lease of 240 s: both before its half
        QuarantineRun run = newRun(240, Duration.ofSeconds(70));

        assertEquals(2, run.tryRun(SITE, null, false).orElseThrow().processed().size());

        assertEquals(List.of("take " + SITE, "release " + SITE), lock.calls);
    }

    @Test
    void theLeaseIsExtendedOnceItsHalfWentByAndNotAgainBeforeTheNextHalf() throws Exception {
        holdSubmissions(4);
        // submissions at 0, 70, 140 and 210 s of a lease of 240 s: the one at 140 s extends it to 380 s, whose half
        // is 260 s, so the one at 210 s does not
        QuarantineRun run = newRun(240, Duration.ofSeconds(70));

        assertEquals(4, run.tryRun(SITE, null, false).orElseThrow().processed().size());

        assertEquals(List.of("take " + SITE, "extend " + SITE, "release " + SITE), lock.calls);
        assertEquals(List.of(Duration.ofSeconds(240), Duration.ofSeconds(240)), lock.leases);
    }

    @Test
    void aRunThatLosesItsLockStopsBeforeTheNextSubmissionAndKeepsItsReport() {
        holdSubmissions(4);
        // the extension at 140 s finds the lock held by another owner
        lock.lostAfter = 1;
        QuarantineRun run = newRun(240, Duration.ofSeconds(70));

        RunReport report = assertThrows(PassStoppedException.class, () -> run.tryRun(SITE, null, false)).report();

        assertEquals(2, report.processed().size());
        long untouched = stores.held.values().stream().filter(one -> HeldStates.HELD.equals(one.state)).count();
        assertEquals(2, untouched);
        assertEquals("release " + SITE, lock.calls.get(lock.calls.size() - 1));
    }

    @Test
    void aDeactivationDuringThePassStopsItBeforeTheNextSubmission() {
        holdSubmissions(3);
        List<String> read = new ArrayList<>();
        QuarantineRun run = newRun(600, formId -> {
            read.add(formId);
            // the module stops while the first submission runs: the lease is no longer valid
            lock.valid = false;
            return List.of();
        });

        RunReport report = assertThrows(PassStoppedException.class, () -> run.tryRun(SITE, null, false)).report();

        assertEquals(1, report.processed().size());
        assertEquals(1, read.size());
        assertEquals(2, stores.held.values().stream().filter(one -> HeldStates.HELD.equals(one.state)).count());
    }

    @Test
    void aSlowCallDoesNotOutliveTheLeaseWithoutAnExtensionBeforeTheRelease() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        QuarantineRun run = newRun(240, formId -> List.of(CheckSettings.withDefaults(MarkerQualifier.REMOTE_TYPE,
                "remoteCheck")));
        // the check takes 200 s of a lease of 240 s: the lease is extended before the actions run
        run.addQualifier(new SlowPass(Duration.ofSeconds(200)));

        run.tryRun(SITE, null, false).orElseThrow();

        assertEquals(HeldStates.COMPLETED, held.state);
        assertEquals(List.of("take " + SITE, "call", "extend " + SITE, "actions", "release " + SITE), lock.calls);
    }

    @Test
    void aLeaseLostDuringASlowCallStopsThePassBeforeTheRelease() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        lock.lostAfter = 1;
        QuarantineRun run = newRun(240, formId -> List.of(CheckSettings.withDefaults(MarkerQualifier.REMOTE_TYPE,
                "remoteCheck")));
        run.addQualifier(new SlowPass(Duration.ofSeconds(200)));

        RunReport report = assertThrows(PassStoppedException.class, () -> run.tryRun(SITE, null, false)).report();

        // the actions never ran, and the submission is still checking: the next holder of the lock releases it
        assertEquals(List.of(), report.processed());
        assertEquals(HeldStates.CHECKING, held.state);
        assertFalse(lock.calls.contains("actions"));
    }

    @Test
    void aSubmissionThatAnotherRunMovedUnderThePassIsSkipped() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        stores.beforeStateWrite = () -> held.state = HeldStates.NEEDS_REVIEW;
        QuarantineRun run = newRun(600, Duration.ZERO);

        RunReport report = run.tryRun(SITE, null, false).orElseThrow();

        assertEquals(1, report.processed().size());
        assertEquals(QuarantineRun.MOVED, report.processed().get(0).error());
        assertEquals(HeldStates.NEEDS_REVIEW, report.processed().get(0).state());
        assertEquals(List.of(), held.audit);
    }

    @Test
    void theActionsDoNotRunWhenAnotherRunMovedTheSubmissionToReleasingFirst() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldStates.CHECKING;
        QuarantineRun run = newRun(600, formId -> List.of(CheckSettings.withDefaults(MarkerQualifier.REMOTE_TYPE,
                "remoteCheck")));
        run.addQualifier(new SlowPass(Duration.ZERO));
        // the first state write of the pass is the move to releasing: another run made it first
        stores.beforeStateWrite = () -> held.state = HeldStates.RELEASING;

        RunReport report = run.tryRun(SITE, null, false).orElseThrow();

        assertEquals(QuarantineRun.MOVED, report.processed().get(0).error());
        assertFalse(lock.calls.contains("actions"), "the actions after release ran twice");
        assertEquals(HeldStates.RELEASING, held.state);
    }

    @Test
    void runNowNamesAStoppedPassAndCarriesItsReport() {
        holdSubmissions(4);
        lock.lostAfter = 1;
        QuarantineRun run = newRun(240, Duration.ofSeconds(70));

        QuarantineRunStoppedException stopped = assertThrows(QuarantineRunStoppedException.class,
                () -> run.runNow(SITE, null, false));

        assertEquals(2, stopped.report().processed().size());
        assertEquals(SITE, stopped.report().siteKey());
    }
}
