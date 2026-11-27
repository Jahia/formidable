package org.jahia.modules.formidable.quarantine.store;

/**
 * A state move refused because the stored state is not the state the mover read: another run or another operation
 * moved the held submission first, or saved it at the same moment. Nothing was written. The run skips the submission,
 * and an operation answers a conflict.
 */
public class StaleStateException extends QuarantineStoreException {

    private static final long serialVersionUID = 1L;

    public StaleStateException(String message) {
        super(message);
    }

    public StaleStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
