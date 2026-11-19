package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.test.modules.formidable.quarantine.state.HeldEvent;
import org.jahia.test.modules.formidable.quarantine.state.HeldState;
import org.jahia.test.modules.formidable.quarantine.state.IllegalTransitionException;
import org.jahia.test.modules.formidable.quarantine.state.StateMachine;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Moves the state of a held submission, through the {@link StateMachine} only: the store writes the state the
 * machine answers, and nothing else writes a state.
 */
public final class Transitions {

    private static final Logger log = LoggerFactory.getLogger(Transitions.class);

    private final QuarantineStore store;

    public Transitions(QuarantineStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * @return the state the event leads to, written on the held submission
     * @throws IllegalTransitionException when the state does not allow the event: nothing is written
     */
    public HeldState move(String path, HeldState from, HeldEvent event) throws QuarantineStoreException {
        HeldState to = StateMachine.next(from, event);
        store.setState(path, to.value());
        log.info("Held submission {}: {} to {} on {}", path, from.value(), to.value(), event);
        return to;
    }
}
