package org.jahia.modules.formidable.quarantine.store;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who may take the lock of the run of a site, by the lease that the lock node stores. */
class RunLeaseTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @Test
    void aLockThatNoServerEverTookIsFree() {
        assertTrue(RunLease.mayTake(null, "serverA:t1", NOW));
        assertTrue(RunLease.mayTake(new RunLease(null, null), "serverA:t1", NOW));
    }

    @Test
    void aLeaseOfAnotherServerThatHasNotEndedRefusesTheLock() {
        assertFalse(RunLease.mayTake(new RunLease("serverB:t1", NOW.plusSeconds(1)), "serverA:t1", NOW));
    }

    @Test
    void aLeaseThatEndedFreesTheLock() {
        assertTrue(RunLease.mayTake(new RunLease("serverB:t1", NOW), "serverA:t1", NOW));
        assertTrue(RunLease.mayTake(new RunLease("serverB:t1", NOW.minusSeconds(1)), "serverA:t1", NOW));
    }

    @Test
    void theServerThatHoldsTheLeaseMayExtendIt() {
        assertTrue(RunLease.mayTake(new RunLease("serverA:t1", NOW.plusSeconds(600)), "serverA:t1", NOW));
    }

    @Test
    void anotherActivationOfTheSameServerIsAnotherOwner() {
        assertFalse(RunLease.mayTake(new RunLease("serverA:t1", NOW.plusSeconds(600)), "serverA:t2", NOW));
        assertTrue(RunLease.mayTake(new RunLease("serverA:t1", NOW), "serverA:t2", NOW));
    }
}
