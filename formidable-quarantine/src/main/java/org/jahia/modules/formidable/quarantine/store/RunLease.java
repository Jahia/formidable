package org.jahia.modules.formidable.quarantine.store;

import java.time.Instant;

/**
 * The lease of the lock of the run of a site, as the lock node stores it.
 *
 * @param owner the owner that holds the lock, a server and the token of one activation of the lock service, or
 *              {@code null} when none ever took it
 * @param until when the lease ends, or {@code null} when none ever took it
 */
public record RunLease(String owner, Instant until) {

    /**
     * Whether an owner may take the lock: no owner holds it, its lease ended, or the owner holds it already. An owner
     * is one run at a time on its own (the run is {@code synchronized}), so a lease of its own is one that the run
     * extends. Another activation on the same server is another owner, and waits for the lease.
     *
     * @param current what the lock node stores, or {@code null} when the node does not exist
     * @param owner   the owner that asks
     * @param now     the time of the request
     */
    public static boolean mayTake(RunLease current, String owner, Instant now) {
        if (current == null || current.owner() == null || current.until() == null) {
            return true;
        }
        return owner.equals(current.owner()) || !current.until().isAfter(now);
    }
}
