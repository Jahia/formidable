package org.jahia.modules.formidable.quarantine.store;

import java.time.Duration;
import java.util.Optional;

/**
 * The lock of the run of the checks of a site, across the servers of a cluster, an OSGi service: one holder at a time
 * releases the held submissions of a site. The lock is a lease: a holder that stops without releasing it keeps it
 * only until the lease ends. The rule of the lease is {@link RunLease#mayTake}.
 *
 * <p>The owner of a lease is the server, the activation of this service and the holder: each {@link #take} is a new
 * owner, so two passes never share a lease, even in one server.</p>
 */
public interface RunLock {

    /**
     * Takes the lock of the run of the site for this long, as a new owner. A site with no quarantine tree has nothing
     * to run, and the lock is granted with nothing written.
     *
     * @return the lease, or empty when another owner holds the lock and its lease has not ended, took it at the same
     *         moment, or the service stopped
     * @throws QuarantineStoreException when the repository cannot be read or written
     */
    Optional<Lease> take(String siteKey, Duration length) throws QuarantineStoreException;

    /**
     * Whether an owner holds the lock of the run of the site now: a lease that has not ended. It writes nothing.
     *
     * @throws QuarantineStoreException when the repository cannot be read
     */
    boolean isHeld(String siteKey) throws QuarantineStoreException;

    /** The lease of one holder of the lock of a site. */
    interface Lease {

        /** @return the key of the site */
        String siteKey();

        /**
         * A local check, with no read: the lease was not released, the service that granted it did not stop, and its
         * end has not come.
         */
        boolean valid();

        /**
         * Extends the lease by this long from now, when this holder still holds the lock.
         *
         * @return false when another owner took the lock, or the lease is no longer {@link #valid()}
         */
        boolean extend(Duration length) throws QuarantineStoreException;

        /** Releases the lock, when this holder still holds it. A failure is logged, and the lease ends it. */
        void release();
    }
}
