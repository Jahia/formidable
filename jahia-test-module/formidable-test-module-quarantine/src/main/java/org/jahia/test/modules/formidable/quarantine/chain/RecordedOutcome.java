package org.jahia.test.modules.formidable.quarantine.chain;

import java.util.Objects;

import org.jahia.test.modules.formidable.quarantine.check.Outcome;

/**
 * The last outcome the run recorded for one file and one check.
 *
 * @param outcome  the last answer of the check
 * @param ticket   the ticket the check returned, or null when it answered at once
 * @param attempts the count of retryable failures in a row for this file and this check
 */
public record RecordedOutcome(Outcome outcome, String ticket, int attempts) {

    public RecordedOutcome {
        Objects.requireNonNull(outcome, "outcome");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts < 0");
        }
        if (outcome instanceof Outcome.Pending && ticket == null) {
            throw new IllegalArgumentException("A pending outcome needs a ticket");
        }
    }
}
