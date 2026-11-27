package org.jahia.modules.formidable.quarantine.ops;

/** An operation that did not run: nothing changed. The reason says why. */
public class OperationRefusedException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Why an operation did not run. */
    public enum Reason {
        /** The user does not hold the permission of a manager on the form. */
        FORBIDDEN,
        /** No held submission is at the path. Only a server administrator learns it. */
        NOT_FOUND,
        /**
         * The operation cannot run now: the state of the submission or its decision does not allow it, a run holds
         * the site (the lock of the site in this JVM, or the lock of the run of the site in the repository), or another
         * run or operation moved the state first.
         */
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
