package org.jahia.modules.formidable.quarantine.state;

/**
 * An event that the state of a held submission does not allow, such as any event out of {@code completed}.
 */
public class IllegalTransitionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final HeldState from;
    private final HeldEvent event;

    public IllegalTransitionException(HeldState from, HeldEvent event) {
        super("Event " + event + " is not allowed in state " + from.value());
        this.from = from;
        this.event = event;
    }

    /** @return the state the submission is in */
    public HeldState from() {
        return from;
    }

    /** @return the event that was refused */
    public HeldEvent event() {
        return event;
    }
}
