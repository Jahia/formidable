package org.jahia.modules.formidable.accounts.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttemptLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AttemptLimiter limiter = new AttemptLimiter(100, now::get);

    @Test
    void aKeyIsBlockedOnceItHoldsTheLimitInsideTheWindow() {
        // Verifies the sliding window: three hits inside one minute block a limit of three, and not before.
        for (int i = 0; i < 3; i++) {
            assertFalse(limiter.isBlocked("a", 3, 60_000), "hit " + i);
            limiter.record("a", 60_000);
            now.addAndGet(1_000);
        }
        assertTrue(limiter.isBlocked("a", 3, 60_000));
    }

    @Test
    void hitsLeaveTheWindowAndTheKeyIsFreeAgain() {
        // Verifies the expiry: once the oldest hits are older than the window, the key is admitted again.
        limiter.record("a", 60_000);
        limiter.record("a", 60_000);
        assertTrue(limiter.isBlocked("a", 2, 60_000));

        now.addAndGet(60_001);

        assertFalse(limiter.isBlocked("a", 2, 60_000));
    }

    @Test
    void keysAreIndependentAndAResetForgetsOne() {
        // Verifies the counts are per key, and that a success clears the failures of its account only.
        limiter.record("a", 60_000);
        limiter.record("b", 60_000);
        assertTrue(limiter.isBlocked("a", 1, 60_000));
        limiter.reset("a");
        assertFalse(limiter.isBlocked("a", 1, 60_000));
        assertTrue(limiter.isBlocked("b", 1, 60_000));
        assertFalse(limiter.isBlocked("never-seen", 1, 60_000));
    }

    @Test
    void theNumberOfRememberedKeysIsBoundedAndTheLeastRecentlyUsedIsForgotten() {
        // Verifies the memory bound: a flood of distinct keys cannot grow the map past maxKeys; the oldest goes first.
        AttemptLimiter small = new AttemptLimiter(3, now::get);
        small.record("k1", 60_000);
        small.record("k2", 60_000);
        small.record("k3", 60_000);
        small.record("k1", 60_000);
        small.record("k4", 60_000);

        assertEquals(3, small.size());
        assertTrue(small.isBlocked("k1", 1, 60_000), "k1 was used again, so it stays");
        assertFalse(small.isBlocked("k2", 1, 60_000), "k2 was the least recently used");
    }

    @Test
    void theHitsKeptPerKeyAreBounded() {
        // Verifies a flood on one key cannot grow its deque: the limit is capped at the bound, and the key stays blocked.
        for (int i = 0; i < AttemptLimiter.MAX_HITS_PER_KEY * 2; i++) {
            limiter.record("flood", 3_600_000);
        }
        assertTrue(limiter.isBlocked("flood", Integer.MAX_VALUE, 3_600_000));
        assertEquals(1, limiter.size());
    }
}
