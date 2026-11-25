package org.jahia.modules.formidable.quarantine.state;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackoffTest {

    private final Backoff backoff = new Backoff(Duration.ofSeconds(30), Duration.ofMinutes(5), 4);

    @Test
    void theDelayDoublesAtEachAttempt() {
        assertEquals(Duration.ofSeconds(30), backoff.delay(1));
        assertEquals(Duration.ofSeconds(60), backoff.delay(2));
        assertEquals(Duration.ofSeconds(120), backoff.delay(3));
        assertEquals(Duration.ofSeconds(240), backoff.delay(4));
    }

    @Test
    void theDelayStopsAtTheCap() {
        assertEquals(Duration.ofMinutes(5), backoff.delay(5));
        assertEquals(Duration.ofMinutes(5), backoff.delay(Integer.MAX_VALUE));
    }

    @Test
    void theNextAttemptIsTheLastOnePlusTheDelay() {
        Instant now = Instant.parse("2026-11-19T10:00:00Z");
        assertEquals(Instant.parse("2026-11-19T10:01:00Z"), backoff.nextAttempt(2, now));
    }

    @Test
    void theAttemptsAreExhaustedAtTheMaximum() {
        assertFalse(backoff.exhausted(0));
        assertFalse(backoff.exhausted(3));
        assertTrue(backoff.exhausted(4));
        assertTrue(backoff.exhausted(5));
        assertEquals(4, backoff.maxAttempts());
    }

    @Test
    void aBadSettingIsRefused() {
        Duration oneMinute = Duration.ofMinutes(1);
        Duration twoMinutes = Duration.ofMinutes(2);
        assertThrows(IllegalArgumentException.class, () -> new Backoff(Duration.ZERO, oneMinute, 3));
        assertThrows(IllegalArgumentException.class, () -> new Backoff(twoMinutes, oneMinute, 3));
        assertThrows(IllegalArgumentException.class, () -> new Backoff(oneMinute, oneMinute, 0));
        assertThrows(IllegalArgumentException.class, () -> backoff.delay(0));
    }

    @Test
    void theDefaultsAreAMinuteDoublingToAnHourInTenAttempts() {
        assertEquals(Duration.ofMinutes(1), Backoff.DEFAULT.delay(1));
        assertEquals(Duration.ofHours(1), Backoff.DEFAULT.delay(10));
        assertEquals(10, Backoff.DEFAULT.maxAttempts());
        assertEquals(2, Backoff.DEFAULT.factor());
    }

    @Test
    void theDelayGrowsByTheFactorOfTheSettings() {
        Backoff tripling = new Backoff(Duration.ofSeconds(10), 3, Duration.ofMinutes(10), 5);

        assertEquals(Duration.ofSeconds(10), tripling.delay(1));
        assertEquals(Duration.ofSeconds(30), tripling.delay(2));
        assertEquals(Duration.ofSeconds(90), tripling.delay(3));
        assertEquals(Duration.ofSeconds(270), tripling.delay(4));
        assertEquals(Duration.ofMinutes(10), tripling.delay(5));
    }

    @Test
    void aFractionalFactorRoundsToTheMillisecond() {
        Backoff slow = new Backoff(Duration.ofSeconds(1), 1.5, Duration.ofMinutes(1), 5);

        assertEquals(Duration.ofMillis(1500), slow.delay(2));
        assertEquals(Duration.ofMillis(2250), slow.delay(3));
    }

    @Test
    void aFactorOfOneKeepsTheDelayAndNeverOverflows() {
        Backoff constant = new Backoff(Duration.ofSeconds(45), 1, Duration.ofHours(1), 3);

        assertEquals(Duration.ofSeconds(45), constant.delay(1));
        assertEquals(Duration.ofSeconds(45), constant.delay(Integer.MAX_VALUE));
    }

    @Test
    void aFactorBelowOneIsRefused() {
        Duration oneMinute = Duration.ofMinutes(1);
        assertThrows(IllegalArgumentException.class, () -> new Backoff(oneMinute, 0.5, oneMinute, 3));
        assertThrows(IllegalArgumentException.class, () -> new Backoff(oneMinute, Double.NaN, oneMinute, 3));
    }
}
