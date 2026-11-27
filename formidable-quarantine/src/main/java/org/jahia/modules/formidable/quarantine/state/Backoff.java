package org.jahia.modules.formidable.quarantine.state;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When the run tries a held submission again, as a pure class. The delay grows by {@code factor} at each attempt from
 * {@code initial}, and never exceeds {@code cap}: after attempt {@code n} (from 1), the delay is
 * {@code min(initial * factor^(n-1), cap)}. The attempts are exhausted once their count reaches {@code maxAttempts}.
 * A manual retry starts the count over at 0.
 */
public final class Backoff {

    /** The factor of {@link #DEFAULT}: the delay doubles at each attempt. */
    public static final double DEFAULT_FACTOR = 2;

    /** The defaults: one minute, doubling, capped at one hour, ten attempts. */
    public static final Backoff DEFAULT = new Backoff(Duration.ofMinutes(1), DEFAULT_FACTOR, Duration.ofHours(1), 10);

    private final Duration initial;
    private final double factor;
    private final Duration cap;
    private final int maxAttempts;

    /** A back-off whose delay doubles at each attempt. */
    public Backoff(Duration initial, Duration cap, int maxAttempts) {
        this(initial, DEFAULT_FACTOR, cap, maxAttempts);
    }

    /**
     * @param initial     the delay after the first attempt; positive
     * @param factor      what the delay is multiplied by at each attempt; at least 1, where 1 keeps the delay constant
     * @param cap         the longest delay; at least {@code initial}
     * @param maxAttempts the count of attempts after which the run gives up; at least 1
     */
    public Backoff(Duration initial, double factor, Duration cap, int maxAttempts) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(cap, "cap");
        if (initial.isZero() || initial.isNegative()) {
            throw new IllegalArgumentException("initial must be positive");
        }
        if (Double.isNaN(factor) || Double.isInfinite(factor) || factor < 1) {
            throw new IllegalArgumentException("factor < 1");
        }
        if (cap.compareTo(initial) < 0) {
            throw new IllegalArgumentException("cap < initial");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts < 1");
        }
        this.initial = initial;
        this.factor = factor;
        this.cap = cap;
        this.maxAttempts = maxAttempts;
    }

    /** @return the count of attempts after which the run gives up */
    public int maxAttempts() {
        return maxAttempts;
    }

    /** @return what the delay is multiplied by at each attempt */
    public double factor() {
        return factor;
    }

    /**
     * @param attempts the count of attempts made so far, at least 1
     * @return the delay before the next attempt, to the millisecond
     */
    public Duration delay(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts < 1");
        }
        // a power that overflows the double is infinite, and the cap applies
        double millis = initial.toMillis() * Math.pow(factor, attempts - 1.0);
        return millis < cap.toMillis() ? Duration.ofMillis(Math.round(millis)) : cap;
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
}
