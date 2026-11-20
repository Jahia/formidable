package org.jahia.test.modules.formidable.quarantine.state;

import java.util.Objects;

/**
 * The transitions of a held submission, as a pure class:
 * <pre>
 * held -&gt; checking -&gt; checking | rejected | needsReview | releasing
 * releasing -&gt; completed | needsReview
 * releasing -&gt; releasing | rejected                (manager operations: resume, reject)
 * needsReview -&gt; checking | releasing | rejected    (manager operations)
 * </pre>
 * Nothing leaves {@code completed} or {@code rejected}. The run never takes a submission in {@code releasing}: a
 * release that stopped half way, on a failure of the store, an error or a stop of the JVM, waits for a manager.
 */
public final class StateMachine {

    private StateMachine() {
    }

    /**
     * @return true when the event may leave the state
     */
    public static boolean allows(HeldState from, HeldEvent event) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(event, "event");
        return event.leaves(from);
    }

    /**
     * @return the state the event leads to
     * @throws IllegalTransitionException when the state does not allow the event
     */
    public static HeldState next(HeldState from, HeldEvent event) {
        if (!allows(from, event)) {
            throw new IllegalTransitionException(from, event);
        }
        return event.target();
    }
}
