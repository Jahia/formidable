package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine;
import org.jahia.modules.formidable.quarantine.run.Release;
import org.jahia.modules.formidable.quarantine.run.SiteLocks;
import org.jahia.modules.formidable.quarantine.run.Transitions;
import org.jahia.modules.formidable.quarantine.store.RunLock;

import java.time.Clock;

/**
 * The operations of a manager over the stores in memory, for a test of another package: the release of the module
 * with the default policy, and actions after release that do nothing.
 */
public final class InMemoryOperations {

    private InMemoryOperations() {
    }

    public static QuarantineOperations of(InMemoryQuarantine stores, Managers managers, RunLock runLock,
                                          SiteLocks siteLocks, Clock clock) {
        QuarantineOperations operations = new QuarantineOperations(managers, clock);
        operations.setStore(stores);
        operations.setCheckStore(stores);
        operations.setRelease(() -> new Release(stores, stores, (submission, from, completed) -> {
            // no action after release
        }, new Transitions(stores), clock));
        operations.setRunLock(runLock);
        operations.setSiteLocks(siteLocks);
        return operations;
    }
}
