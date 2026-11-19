package org.jahia.test.modules.formidable.quarantine.store;

import java.time.Instant;
import java.util.Objects;

/**
 * One audit record of a held submission, as {@link CheckStore} writes it under {@code audit/<n>}: who changed the
 * state, by which operation, from which state to which state, and when. It holds no value and no byte.
 *
 * @param operation the operation: {@code retry}, {@code release}, {@code resume}, {@code reject}, or {@link #RUN}
 *                  when the run of the checks moved the state
 * @param userName  the name of the user that asked for the operation, or {@code null} for the run
 * @param fromState the state before the operation
 * @param toState   the state after the operation
 * @param at        when the operation ended
 */
public record AuditRecord(String operation, String userName, String fromState, String toState, Instant at) {

    /** The operation of a record that the run of the checks writes. */
    public static final String RUN = "run";

    public AuditRecord {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(toState, "toState");
        Objects.requireNonNull(at, "at");
    }
}
