package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.state.HeldState;

/**
 * An operation that ran: the state before it, and the state after it. After a release or a resume, the state after is
 * {@code completed}, or {@code needsReview} when an action after release failed again or the bytes changed.
 */
public record OperationResult(String path, Operation operation, HeldState from, HeldState to) {
}
