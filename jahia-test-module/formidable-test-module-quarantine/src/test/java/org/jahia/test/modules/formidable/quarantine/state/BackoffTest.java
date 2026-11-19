package org.jahia.test.modules.formidable.quarantine.state;

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
        assertThrows(IllegalArgumentException.class, () -> new Backoff(Duration.ZERO, Duration.ofMinutes(1), 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(Duration.ofMinutes(2), Duration.ofMinutes(1), 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(Duration.ofMinutes(1), Duration.ofMinutes(1), 0));
        assertThrows(IllegalArgumentException.class, () -> backoff.delay(0));
    }

    @Test
    void theDefaultsAreAMinuteDoublingToAnHourInTenAttempts() {
        assertEquals(Duration.ofMinutes(1), Backoff.DEFAULT.delay(1));
        assertEquals(Duration.ofHours(1), Backoff.DEFAULT.delay(10));
        assertEquals(10, Backoff.DEFAULT.maxAttempts());
    }
}
