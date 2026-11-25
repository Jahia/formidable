package org.jahia.modules.formidable.quarantine.store;

import java.time.Instant;

/**
 * The lease of the lock of the run of a site, as the lock node stores it.
 *
 * @param owner the server that holds the lock, or {@code null} when none ever took it
 * @param until when the lease ends, or {@code null} when none ever took it
 */
public record RunLease(String owner, Instant until) {

    /**
     * Whether a server may take the lock: no server holds it, its lease ended, or the server holds it already. A server
     * that holds the lock is one run at a time on its own (the run is {@code synchronized}), so a lease of its own is
     * one that an earlier start of the server left behind, or one that the run extends.
     *
     * @param current what the lock node stores, or {@code null} when the node does not exist
     * @param server  the identifier of the server that asks
     * @param now     the time of the request
     */
    public static boolean mayTake(RunLease current, String server, Instant now) {
        if (current == null || current.owner() == null || current.until() == null) {
            return true;
        }
        return server.equals(current.owner()) || !current.until().isAfter(now);
    }
}
