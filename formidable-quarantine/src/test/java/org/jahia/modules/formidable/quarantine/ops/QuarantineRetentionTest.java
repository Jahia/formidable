package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The retention purges a rejected held submission older than the limit, and nothing else. */
class QuarantineRetentionTest {

    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private InMemoryQuarantine stores;
    private QuarantineRetention retention;

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        retention = new QuarantineRetention(Clock.fixed(NOW, ZoneOffset.UTC));
        retention.setStore(stores);
        retention.setCheckStore(stores);
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
        };
    }

    private Held held(HeldState state, int daysAgo) {
        Held held = stores.hold(FORM, "VERDICT:FAIL\nA note " + stores.held.size());
        held.state = state.value();
        held.changedAt = NOW.minus(Duration.ofDays(daysAgo));
        return held;
    }

    @Test
    void purgesOnlyTheRejectedSubmissionsOlderThanTheLimit() throws Exception {
        Held oldRejected = held(HeldState.REJECTED, 31);
        Held freshRejected = held(HeldState.REJECTED, 29);
        Held oldCompleted = held(HeldState.COMPLETED, 400);
        Held oldInReview = held(HeldState.NEEDS_REVIEW, 400);
        Held oldChecking = held(HeldState.CHECKING, 400);

        QuarantineRetention.Report report = retention.purge(InMemoryQuarantine.SITE, null);

        assertEquals(30, report.retentionDays());
        assertEquals(List.of(oldRejected.path), report.purged());
        assertFalse(stores.held.containsKey(oldRejected.path), "the old rejected submission, with its records");
        for (Held kept : List.of(freshRejected, oldCompleted, oldInReview, oldChecking)) {
            assertTrue(stores.held.containsKey(kept.path), kept.state + " changed " + kept.changedAt + " is kept");
        }
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
}
