package org.jahia.test.modules.formidable.quarantine.stub;

/**
 * What the marker of a text file asks a stub check to answer.
 */
public sealed interface StubVerdict {

    /** {@code VERDICT:PASS}: the stub answers {@code PASS}. */
    record Pass() implements StubVerdict {
    }

    /** {@code VERDICT:FAIL}: the stub answers {@code FAIL}. */
    record Fail() implements StubVerdict {
    }

    /** {@code INCONCLUSIVE}: the stub answers {@code INCONCLUSIVE}. */
    record Inconclusive() implements StubVerdict {
    }

    /**
     * {@code PENDING:<n>}: the stub answers pending for {@code n} polls, then {@code PASS}.
     *
     * @param polls the count of polls that answer pending
     */
    record Pending(int polls) implements StubVerdict {
        public Pending {
            if (polls < 0) {
                throw new IllegalArgumentException("polls < 0");
            }
        }
    }

    /** {@code DOWN}: a retryable failure while the outage is on, then {@code PASS}. */
    record Down() implements StubVerdict {
    }

    /** No marker: the stub answers {@code INCONCLUSIVE}, so an unmarked file is never released by the stub. */
    record Unmarked() implements StubVerdict {
    }
}
