package org.jahia.modules.formidable.formsimport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportJobsStateTest {

    @Test
    void aStoredStateReadsBackAsItself() {
        for (ImportJobs.State state : ImportJobs.State.values()) {
            assertEquals(state, ImportJobs.State.of(state.stored()));
        }
    }

    @Test
    void aStateThisVersionDoesNotKnowReadsAsFailed() {
        // "attaching": a job of the withdrawn attachment action, left by a development build
        assertEquals(ImportJobs.State.FAILED, ImportJobs.State.of("attaching"));
        assertEquals(ImportJobs.State.FAILED, ImportJobs.State.of(null));
    }
}
