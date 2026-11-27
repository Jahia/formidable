package org.jahia.modules.formidable.quarantine.run;

import org.osgi.service.component.annotations.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One lock per site in this JVM, an OSGi service that the run and the operations of a manager share: a pass of the run
 * over a site and an operation on a submission of that site never run at the same time in one server. A pass of
 * another site does not stop the operation. The service outlives a reactivation of the run alone, so a new activation
 * of the run cannot start a second pass on a site while the old one still runs it.
 */
@Component(service = SiteLocks.class, immediate = true)
public class SiteLocks {

    /** How long an operation waits for a pass of the run of its site before it answers a conflict. */
    public static final Duration OPERATION_WAIT = Duration.ofSeconds(2);

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** @return the lock of the site, created on first use */
    public ReentrantLock of(String siteKey) {
        return locks.computeIfAbsent(siteKey, key -> new ReentrantLock());
    }

    /**
     * Takes the lock of the site, waiting at most this long.
     *
     * @return true when the lock is taken: the caller unlocks it; false when a pass held it the whole time, or the
     *         thread was interrupted, which stays interrupted
     */
    public boolean tryLock(String siteKey, Duration wait) {
        try {
            return of(siteKey).tryLock(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
