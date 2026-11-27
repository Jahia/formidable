package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.formidable.quarantine.store.RunLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Keeps the lease of a holder of the lock of the run of a site: a pass of the run, or a release of a manager. Before a
 * submission or a call, it checks that the lease is still valid where it was granted, and extends it once half of it
 * has gone by.
 */
public final class LeaseKeeper {

    private static final Logger log = LoggerFactory.getLogger(LeaseKeeper.class);

    private final RunLock.Lease lease;
    private final Duration length;
    private final Clock clock;
    private Instant extendAt;

    public LeaseKeeper(RunLock.Lease lease, Duration length, Clock clock) {
        this.lease = lease;
        this.length = length;
        this.clock = clock;
        this.extendAt = clock.instant().plus(length.dividedBy(2));
    }

    /**
     * @return false when the lease is lost: released, its service stopped, its end came, another owner took the lock,
     *         or the repository failed to extend it
     */
    public boolean kept() {
        if (!lease.valid()) {
            return false;
        }
        Instant now = clock.instant();
        if (now.isBefore(extendAt)) {
            return true;
        }
        try {
            if (!lease.extend(length)) {
                return false;
            }
        } catch (QuarantineStoreException e) {
            if (log.isWarnEnabled()) {
                log.warn("The lease of the run of site {} could not be extended: {}", lease.siteKey(), e.getMessage());
            }
            return false;
        }
        extendAt = now.plus(length.dividedBy(2));
        return true;
    }

    /** @throws RunStoppedException when the lease is lost */
    void keptOrStop() throws RunStoppedException {
        if (!kept()) {
            throw new RunStoppedException("the lock of the run of site " + lease.siteKey() + " is lost");
        }
    }
}
