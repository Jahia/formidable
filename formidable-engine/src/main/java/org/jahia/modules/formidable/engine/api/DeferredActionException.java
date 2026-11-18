package org.jahia.modules.formidable.engine.api;

/**
 * A run of the actions after release stopped ({@link DeferredActions#run}). The message and the cause carry no
 * sensitive value: the engine removes the text of a failure of an action that held one.
 */
public class DeferredActionException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The value of {@link #failedAction()} when the list was refused before any action ran. */
    public static final int NO_ACTION = -1;

    private final int failedAction;
    private final int total;

    public DeferredActionException(String message, int failedAction, int total, Throwable cause) {
        super(message, cause);
        this.failedAction = failedAction;
        this.total = total;
    }

    /** The index of the action that failed, the one to resume from, or {@link #NO_ACTION}. */
    public int failedAction() {
        return failedAction;
    }

    /** The number of actions of the list, or {@link #NO_ACTION} when the list could not be read. */
    public int total() {
        return total;
    }
}
