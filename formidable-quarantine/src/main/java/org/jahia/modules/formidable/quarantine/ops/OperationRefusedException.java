package org.jahia.modules.formidable.quarantine.ops;

/** An operation that did not run: nothing changed. The reason says why. */
public class OperationRefusedException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Why an operation did not run. */
    public enum Reason {
        /**
         * No held submission is at the path, or the user does not manage its site: a user without the permission gets
         * the same answer as for a submission that does not exist.
         */
        NOT_FOUND,
        /** The state of the submission, or its decision, does not allow the operation. */
        CONFLICT
    }

    private final Reason reason;

    public OperationRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
