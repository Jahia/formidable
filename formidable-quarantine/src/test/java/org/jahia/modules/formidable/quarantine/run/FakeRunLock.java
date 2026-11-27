package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.store.RunLock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link RunLock} in memory: the sites another owner holds refuse the lock, and every call is recorded as
 * {@code take <site>}, {@code extend <site>} and {@code release <site>}.
 */
public final class FakeRunLock implements RunLock {

    /** The sites whose lock another owner holds. */
    public final Set<String> heldElsewhere = new HashSet<>();
    /** The calls, in order. */
    public final List<String> calls = new ArrayList<>();
    /** The lengths asked by each take and each extension, in order. */
    public final List<Duration> leases = new ArrayList<>();
    /** The number of takes and extensions after which another owner holds the lock; negative never. */
    public int lostAfter = -1;
    /** Whether the leases granted are still valid where they were granted, as a deactivation would end them. */
    public boolean valid = true;

    @Override
    public Optional<Lease> take(String siteKey, Duration length) {
        calls.add("take " + siteKey);
        if (!grant(length) || heldElsewhere.contains(siteKey)) {
            return Optional.empty();
        }
        return Optional.of(new FakeLease(siteKey));
    }

    @Override
    public boolean isHeld(String siteKey) {
        return heldElsewhere.contains(siteKey) || held(siteKey);
    }

    private boolean grant(Duration length) {
        leases.add(length);
        return lostAfter < 0 || leases.size() <= lostAfter;
    }

    /** Whether a lease of the site is held now: taken, and not released since. */
    public boolean held(String siteKey) {
        int taken = calls.lastIndexOf("take " + siteKey);
        return taken >= 0 && calls.lastIndexOf("release " + siteKey) < taken;
    }

    private final class FakeLease implements Lease {
        private final String siteKey;
        private boolean released;

        FakeLease(String siteKey) {
            this.siteKey = siteKey;
        }

        @Override
        public String siteKey() {
            return siteKey;
        }

        @Override
        public boolean valid() {
            return valid && !released;
        }

        @Override
        public boolean extend(Duration length) {
            calls.add("extend " + siteKey);
            return valid() && grant(length);
        }

        @Override
        public void release() {
            released = true;
            calls.add("release " + siteKey);
        }
    }
}
