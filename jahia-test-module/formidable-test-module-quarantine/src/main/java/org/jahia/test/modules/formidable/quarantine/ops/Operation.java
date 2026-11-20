package org.jahia.test.modules.formidable.quarantine.ops;

import org.jahia.test.modules.formidable.quarantine.state.HeldEvent;

/**
 * What a manager may do by hand with a held submission in state {@code needsReview}, or with a release that stopped
 * half way in {@code releasing} (resume and reject only), and the event of the state machine that each operation sends.
 */
public enum Operation {

    /** Runs the checks again: the attempts and the failures start over. */
    RETRY("retry", HeldEvent.MANAGER_RETRY),

    /** Releases the submission whose checks did not all pass: the actions after release run. */
    RELEASE("release", HeldEvent.MANAGER_RELEASE),

    /** Runs the actions after release again, from the one that failed or from the first one that did not run. */
    RESUME("resume", HeldEvent.MANAGER_RESUME),

    /** Rejects the submission: its values and its bytes are deleted. */
    REJECT("reject", HeldEvent.MANAGER_REJECT);

    private final String value;
    private final HeldEvent event;

    Operation(String value, HeldEvent event) {
        this.value = value;
        this.event = event;
    }

    /** @return the name of the operation, as a request and an audit record name it */
    public String value() {
        return value;
    }

    /** @return the event of the state machine that the operation sends */
    public HeldEvent event() {
        return event;
    }

    /** @return the operation of that name, or {@code null} for any other name */
    public static Operation fromValue(String value) {
        for (Operation operation : values()) {
            if (operation.value.equals(value)) {
                return operation;
            }
        }
        return null;
    }
}
