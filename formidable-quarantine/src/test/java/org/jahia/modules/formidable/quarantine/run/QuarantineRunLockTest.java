package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.api.QuarantineRunException;
import org.jahia.modules.formidable.quarantine.api.RunReport;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The run takes the lock of the run of the site before it reads, extends its lease while it works, and releases it,
 * with a fake lock: a site whose lock another server holds is skipped, and nothing of it is read.
 */
class QuarantineRunLockTest {

    private static final String SITE = InMemoryQuarantine.SITE;
    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private InMemoryQuarantine stores;
    private FakeRunLock lock;

    /** A clock that moves on by a step at each read. */
    private static final class SteppingClock extends Clock {
        private Instant now = NOW;
        private final Duration step;

        SteppingClock(Duration step) {
            this.step = step;
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
            Instant read = now;
            now = now.plus(step);
            return read;
        }
    }

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        lock = new FakeRunLock();
    }

    /** A run whose forms list no check: each due submission goes to review. */
    private QuarantineRun newRun(Clock clock, long leaseSeconds) {
        QuarantineRun run = new QuarantineRun(formId -> List.of(), clock);
        run.setStore(stores);
        run.setCheckStore(stores);
        run.setActions((submission, from, completed) -> {
        });
        run.setRunLock(lock);
        run.configure(config(leaseSeconds));
        return run;
    }

    private static RunConfig config(long leaseSeconds) {
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

    @Test
    void theRunTakesTheLockOfTheSiteAndReleasesItAfterThePass() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        QuarantineRun run = newRun(Clock.fixed(NOW, ZoneOffset.UTC), 600);

        Optional<RunReport> report = run.tryRun(SITE, null, false);

        assertTrue(report.isPresent());
        assertEquals(1, report.get().processed().size());
        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertEquals(List.of("take " + SITE, "release " + SITE), lock.calls);
        assertEquals(List.of(Duration.ofMinutes(10)), lock.leases);
    }

    @Test
    void aSiteWhoseLockAnotherServerHoldsIsSkippedAndNothingIsMoved() throws Exception {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        lock.heldElsewhere.add(SITE);
        QuarantineRun run = newRun(Clock.fixed(NOW, ZoneOffset.UTC), 600);

        Optional<RunReport> report = run.tryRun(SITE, null, false);

        assertFalse(report.isPresent());
        assertEquals(HeldStates.HELD, held.state);
        assertEquals(List.of(), held.states);
        // a lock that was not taken is not released: the other server keeps it
        assertEquals(List.of("take " + SITE), lock.calls);
    }

    @Test
    void runNowFailsWhenAnotherServerHoldsTheLockOfTheSite() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        lock.heldElsewhere.add(SITE);
        QuarantineRun run = newRun(Clock.fixed(NOW, ZoneOffset.UTC), 600);

        QuarantineRunException failed = assertThrows(QuarantineRunException.class, () -> run.runNow(SITE, null, true));

        assertTrue(failed.getMessage().contains("lock"), failed.getMessage());
        assertEquals(HeldStates.HELD, held.state);
    }

    @Test
    void theLockIsReleasedWhenTheTreeCannotBeRead() throws Exception {
        QuarantineRun run = newRun(Clock.fixed(NOW, ZoneOffset.UTC), 600);
        CheckStore unreadable = mock(CheckStore.class);
        when(unreadable.forms(SITE)).thenThrow(new QuarantineStoreException("the tree cannot be read"));
        run.setCheckStore(unreadable);

        assertThrows(QuarantineStoreException.class, () -> run.tryRun(SITE, null, false));

        assertFalse(lock.held(SITE));
        assertEquals(List.of("take " + SITE, "release " + SITE), lock.calls);
    }

    @Test
    void theLeaseIsTheSettingOfTheRunAndANonPositiveOneFallsBackToTheDefault() throws Exception {
        newRun(Clock.fixed(NOW, ZoneOffset.UTC), 120).tryRun(SITE, null, false);
        newRun(Clock.fixed(NOW, ZoneOffset.UTC), 0).tryRun(SITE, null, false);

        assertEquals(List.of(Duration.ofMinutes(2), QuarantineRun.DEFAULT_LEASE), lock.leases);
    }

    @Test
    void aLongRunExtendsItsLeaseOnceHalfOfItWentBy() throws Exception {
        for (int i = 0; i < 4; i++) {
            stores.hold(FORM, "VERDICT:PASS\nA note " + i);
        }
        // each read of the clock is one minute later: a lease of four minutes is extended every two minutes
        QuarantineRun run = newRun(new SteppingClock(Duration.ofMinutes(1)), 240);

        RunReport report = run.tryRun(SITE, null, false).orElseThrow();

        assertEquals(4, report.processed().size());
        assertTrue(lock.leases.size() > 1, "extensions of the lease: " + lock.leases);
        assertTrue(lock.leases.stream().allMatch(Duration.ofMinutes(4)::equals), "leases: " + lock.leases);
        assertFalse(lock.held(SITE));
    }

    @Test
    void aRunThatLosesItsLockStopsBeforeTheNextSubmission() throws Exception {
        for (int i = 0; i < 4; i++) {
            stores.hold(FORM, "VERDICT:PASS\nA note " + i);
        }
        // the first extension of the lease finds the lock held by another server
        lock.lostAfterTakes = 1;
        QuarantineRun run = newRun(new SteppingClock(Duration.ofMinutes(1)), 240);

        RunReport report = run.tryRun(SITE, null, false).orElseThrow();

        assertTrue(report.processed().size() < 4, "processed: " + report.processed().size());
        long untouched = stores.held.values().stream().filter(one -> HeldStates.HELD.equals(one.state)).count();
        assertEquals(4 - report.processed().size(), untouched);
        assertEquals("release " + SITE, lock.calls.get(lock.calls.size() - 1));
    }
}
