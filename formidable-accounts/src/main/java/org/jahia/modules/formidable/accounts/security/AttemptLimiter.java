package org.jahia.modules.formidable.accounts.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * A sliding-window counter per key, held in memory and bounded: the brake of the sign-up and sign-in actions per
 * remote address and per account.
 *
 * <p>Threading: every method is {@code synchronized}; a request holds the lock for a map lookup and a few deque
 * operations, so contention is negligible next to a password hash. State is per JVM: a restart, or another node of
 * a cluster, starts from zero. That is a stated limit of the protection, not a promise it fails to keep.</p>
 *
 * <p>Memory: at most {@code maxKeys} keys (the least recently used is forgotten first) and at most
 * {@link #MAX_HITS_PER_KEY} timestamps per key, so a flood of random keys or of hits on one key cannot grow it.</p>
 */
public final class AttemptLimiter {

    /** Timestamps kept per key; a limit above this is capped at it. */
    static final int MAX_HITS_PER_KEY = 1000;

    private final LongSupplier clockMillis;
    private final Map<String, Deque<Long>> hits;

    /**
     * @param maxKeys     bound on the keys remembered at once, at least 1
     * @param clockMillis the time source in milliseconds, a seam for the tests
     */
    public AttemptLimiter(int maxKeys, LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
        int bound = Math.max(1, maxKeys);
        this.hits = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Long>> eldest) {
                return size() > bound;
            }
        };
    }

    /**
     * Whether the key already holds {@code limit} hits inside the window: it asks, it does not record.
     *
     * @param key          what is counted, for instance {@code addr:signin:203.0.113.7}
     * @param limit        the hits allowed in the window, at least 1
     * @param windowMillis the window, in milliseconds
     * @return {@code true} when one more attempt must be refused
     */
    public synchronized boolean isBlocked(String key, int limit, long windowMillis) {
        Deque<Long> deque = hits.get(key);
        if (deque == null) {
            return false;
        }
        prune(deque, clockMillis.getAsLong() - windowMillis);
        return deque.size() >= Math.min(Math.max(1, limit), MAX_HITS_PER_KEY);
    }

    /**
     * Records one hit now.
     *
     * @param key          what is counted
     * @param windowMillis the window the hit will be counted over, used to drop the hits that left it
     */
    public synchronized void record(String key, long windowMillis) {
        long now = clockMillis.getAsLong();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(deque, now - windowMillis);
        if (deque.size() >= MAX_HITS_PER_KEY) {
            deque.pollFirst();
        }
        deque.addLast(now);
    }

    /** Forgets a key: a success clears the failures counted against an account. */
    public synchronized void reset(String key) {
        hits.remove(key);
    }

    /** @return how many keys are remembered, for the tests and the diagnostics */
    public synchronized int size() {
        return hits.size();
    }

    private static void prune(Deque<Long> deque, long oldestKept) {
        while (!deque.isEmpty() && deque.peekFirst() <= oldestKept) {
            deque.pollFirst();
        }
    }
}
