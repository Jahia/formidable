package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.QuarantineSites;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The retention purges a completed or rejected held submission older than the limit, and nothing else. */
class QuarantineRetentionTest {

    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private InMemoryQuarantine stores;
    private QuarantineRetention retention;
    /** The retention that the settings of each site set, in days; a site absent sets none. */
    private final Map<String, Long> siteDays = new HashMap<>();
    private final Set<String> unreadableSites = new HashSet<>();
    private final QuarantineSites sites = new QuarantineSites() {
        @Override
        public List<String> withTree() {
            return List.of(InMemoryQuarantine.SITE);
        }

        @Override
        public boolean anyDue(String siteKey, Instant now) {
            return false;
        }

        @Override
        public OptionalLong retentionDays(String siteKey) throws QuarantineStoreException {
            if (unreadableSites.contains(siteKey)) {
                throw new QuarantineStoreException("The site " + siteKey + " cannot be read");
            }
            Long days = siteDays.get(siteKey);
            return days == null ? OptionalLong.empty() : OptionalLong.of(days);
        }
    };

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        retention = new QuarantineRetention(Clock.fixed(NOW, ZoneOffset.UTC));
        retention.setStore(stores);
        retention.setCheckStore(stores);
        retention.setSites(sites);
        retention.configure(config(30));
    }

    private static RetentionConfig config(int days) {
        return new RetentionConfig() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RetentionConfig.class;
            }

            @Override
            public int retentionDays() {
                return days;
            }

            @Override
            public long periodSeconds() {
                return QuarantineRetention.DEFAULT_PERIOD_SECONDS;
            }
        };
    }

    private Held held(HeldState state, int daysAgo) {
        Held held = stores.hold(FORM, "VERDICT:FAIL\nA note " + stores.held.size());
        held.state = state.value();
        held.changedAt = NOW.minus(Duration.ofDays(daysAgo));
        return held;
    }

    @Test
    void purgesTheFinishedSubmissionsOlderThanTheLimit() throws Exception {
        Held oldRejected = held(HeldState.REJECTED, 31);
        Held freshRejected = held(HeldState.REJECTED, 29);
        Held oldCompleted = held(HeldState.COMPLETED, 31);
        Held freshCompleted = held(HeldState.COMPLETED, 29);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, null);

        assertEquals(30, report.retentionDays());
        assertEquals(List.of(oldRejected.path, oldCompleted.path), report.purged());
        assertFalse(stores.held.containsKey(oldRejected.path), "the old rejected submission, with its records");
        assertFalse(stores.held.containsKey(oldCompleted.path), "the old completed submission, with its records");
        assertTrue(stores.held.containsKey(freshRejected.path), "a rejected submission within the limit is kept");
        assertTrue(stores.held.containsKey(freshCompleted.path), "a completed submission within the limit is kept");
    }

    @Test
    void neverPurgesASubmissionThatIsNotFinishedWhateverItsAge() throws Exception {
        List<Held> waiting = List.of(held(HeldState.HELD, 4000), held(HeldState.CHECKING, 4000),
                held(HeldState.RELEASING, 4000), held(HeldState.NEEDS_REVIEW, 4000));

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, 0);

        assertEquals(List.of(), report.purged());
        for (Held kept : waiting) {
            assertTrue(stores.held.containsKey(kept.path), kept.state + " changed " + kept.changedAt + " is kept");
        }
    }

    @Test
    void aCompletedSubmissionFollowsTheLowerLimitOfItsSite() throws Exception {
        Held tenDaysOld = held(HeldState.COMPLETED, 10);
        Held twoDaysOld = held(HeldState.COMPLETED, 2);
        siteDays.put(InMemoryQuarantine.SITE, 7L);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, null);

        assertEquals(List.of(tenDaysOld.path), report.purged());
        assertTrue(stores.held.containsKey(twoDaysOld.path));
    }

    @Test
    void theLimitOfACallReplacesTheSetting() throws Exception {
        Held rejected = held(HeldState.REJECTED, 1);

        assertEquals(List.of(), retention.purge(InMemoryQuarantine.SITE, null).purged());
        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, 0);

        assertEquals(0, report.retentionDays());
        assertEquals(List.of(rejected.path), report.purged());
    }

    @Test
    void aSubmissionThatCannotBePurgedIsReportedAndThePassGoesOn() throws Exception {
        Held broken = held(HeldState.REJECTED, 60);
        Held other = held(HeldState.REJECTED, 60);
        stores.broken.add(broken.path);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, null);

        assertEquals(List.of(other.path), report.purged());
        assertEquals(List.of(broken.path), report.failed());
    }

    @Test
    void aNegativeSettingFallsBackToTheDefault() {
        retention.configure(config(-1));

        assertEquals(QuarantineRetention.DEFAULT_RETENTION_DAYS, retention.retentionDays());
    }

    @Test
    void aSiteThatSetsALowerRetentionPurgesSooner() throws Exception {
        Held tenDaysOld = held(HeldState.REJECTED, 10);
        Held twoDaysOld = held(HeldState.REJECTED, 2);
        siteDays.put(InMemoryQuarantine.SITE, 7L);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, null);

        assertEquals(7, report.retentionDays());
        assertEquals(QuarantineRetention.Limit.SITE, report.limit());
        assertEquals(List.of(tenDaysOld.path), report.purged());
        assertTrue(stores.held.containsKey(twoDaysOld.path));
    }

    @Test
    void aSiteThatSetsAHigherRetentionKeepsThePlatformOne() throws Exception {
        Held fortyDaysOld = held(HeldState.REJECTED, 40);
        siteDays.put(InMemoryQuarantine.SITE, 365L);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, null);

        assertEquals(30, report.retentionDays());
        assertEquals(QuarantineRetention.Limit.PLATFORM, report.limit());
        assertEquals(List.of(fortyDaysOld.path), report.purged());
    }

    @Test
    void theLimitOfASiteIsTheLowerOfItsSettingAndThePlatformOne() {
        assertEquals(30, retention.retentionDaysOf(InMemoryQuarantine.SITE));
        siteDays.put(InMemoryQuarantine.SITE, 0L);
        assertEquals(0, retention.retentionDaysOf(InMemoryQuarantine.SITE));
        siteDays.put(InMemoryQuarantine.SITE, 30L);
        assertEquals(30, retention.retentionDaysOf(InMemoryQuarantine.SITE));
        siteDays.put(InMemoryQuarantine.SITE, -5L);
        assertEquals(30, retention.retentionDaysOf(InMemoryQuarantine.SITE));
    }

    @Test
    void aSiteThatCannotBeReadKeepsThePlatformRetentionAndReportsItAsThePlatformOne() throws Exception {
        unreadableSites.add(InMemoryQuarantine.SITE);
        Held fortyDaysOld = held(HeldState.REJECTED, 40);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, null);

        assertEquals(30, report.retentionDays());
        assertEquals(QuarantineRetention.Limit.PLATFORM, report.limit());
        assertEquals(List.of(fortyDaysOld.path), report.purged());
    }

    @Test
    void aLimitForOneCallReplacesTheLimitOfTheSite() throws Exception {
        Held fiveDaysOld = held(HeldState.REJECTED, 5);
        siteDays.put(InMemoryQuarantine.SITE, 7L);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, 1);

        assertEquals(1, report.retentionDays());
        assertEquals(QuarantineRetention.Limit.CALL, report.limit());
        assertEquals(List.of(fiveDaysOld.path), report.purged());
    }
}
