package org.jahia.modules.formidable.quarantine.store;

import java.time.Duration;

/**
 * The lock of the run of the checks of a site, across the servers of a cluster, an OSGi service: one run at a time
 * reads and moves the held submissions of a site. The lock is a lease: a server that stops without releasing it
 * keeps it only until the lease ends. The rule of the lease is {@link RunLease#mayTake}.
 */
public interface RunLock {

    /**
     * Takes the lock of the run of the site for this long, or extends the lease of this server. A site with no
     * quarantine tree has nothing to run, and the lock is granted with nothing written.
     *
     * @return false when another server holds the lock and its lease has not ended, or took it at the same moment
     * @throws QuarantineStoreException when the repository cannot be read or written
     */
    boolean take(String siteKey, Duration lease) throws QuarantineStoreException;

    /** Releases the lock of the run of the site, when this server holds it. A failure is logged, and the lease ends it. */
    void release(String siteKey);
}
