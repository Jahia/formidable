package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.store.RunLock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A {@link RunLock} in memory: the sites another server holds refuse the lock, and every call is recorded. */
public final class FakeRunLock implements RunLock {

    /** The sites whose lock another server holds. */
    public final Set<String> heldElsewhere = new HashSet<>();
    /** The calls, in order: {@code take <site>} and {@code release <site>}. */
    public final List<String> calls = new ArrayList<>();
    /** The leases asked for by each take, in order. */
    public final List<Duration> leases = new ArrayList<>();
    /** The number of takes after which another server holds the lock; negative never. */
    public int lostAfterTakes = -1;

    @Override
    public boolean take(String siteKey, Duration lease) {
        calls.add("take " + siteKey);
        leases.add(lease);
        if (lostAfterTakes >= 0 && leases.size() > lostAfterTakes) {
            return false;
        }
        return !heldElsewhere.contains(siteKey);
    }

    @Override
    public void release(String siteKey) {
        calls.add("release " + siteKey);
    }

    /** Whether the lock of the site is held now: taken, and not released since. */
    public boolean held(String siteKey) {
        int taken = calls.lastIndexOf("take " + siteKey);
        return taken >= 0 && calls.lastIndexOf("release " + siteKey) < taken;
    }
}
