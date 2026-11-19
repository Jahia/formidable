package org.jahia.test.modules.formidable.quarantine.state;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When the run tries a held submission again, as a pure class. The delay doubles at each attempt from
 * {@code initial}, and never exceeds {@code cap}: after attempt {@code n} (from 1), the delay is
 * {@code min(initial * 2^(n-1), cap)}. The attempts are exhausted once their count reaches {@code maxAttempts}. A
 * manual retry starts the count over at 0.
 */
public final class Backoff {

    /** The defaults: one minute, doubling, capped at one hour, ten attempts. */
    public static final Backoff DEFAULT = new Backoff(Duration.ofMinutes(1), Duration.ofHours(1), 10);

    private final Duration initial;
    private final Duration cap;
    private final int maxAttempts;

    /**
     * @param initial     the delay after the first attempt; positive
     * @param cap         the longest delay; at least {@code initial}
     * @param maxAttempts the count of attempts after which the run gives up; at least 1
     */
    public Backoff(Duration initial, Duration cap, int maxAttempts) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(cap, "cap");
        if (initial.isZero() || initial.isNegative()) {
            throw new IllegalArgumentException("initial must be positive");
        }
        if (cap.compareTo(initial) < 0) {
            throw new IllegalArgumentException("cap < initial");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts < 1");
        }
        this.initial = initial;
        this.cap = cap;
        this.maxAttempts = maxAttempts;
    }

    /** @return the count of attempts after which the run gives up */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * @param attempts the count of attempts made so far, at least 1
     * @return the delay before the next attempt
     */
    public Duration delay(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts < 1");
        }
        Duration delay = initial;
        for (int i = 1; i < attempts && delay.compareTo(cap) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(cap) < 0 ? delay : cap;
    }

    /**
     * @param attempts the count of attempts made so far, at least 1
     * @param now      the time of the last attempt
     * @return the time of the next attempt
     */
    public Instant nextAttempt(int attempts, Instant now) {
        return now.plus(delay(attempts));
    }

    /** @return true when no attempt remains */
    public boolean exhausted(int attempts) {
        return attempts >= maxAttempts;
    }

    /** @return the count of attempts after a manual retry */
    public int reset() {
        return 0;
    }
}
