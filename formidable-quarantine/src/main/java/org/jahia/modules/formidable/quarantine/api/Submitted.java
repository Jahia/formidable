package org.jahia.modules.formidable.quarantine.api;

import java.util.Objects;

/**
 * What {@link Qualifier#submit} returns: a {@link Ticket} to poll later, or an outcome at once.
 */
public sealed interface Submitted permits Submitted.Ticket, Submitted.Answered {

    /** @return a ticket to poll */
    static Ticket ticket(String id) {
        return new Ticket(id);
    }

    /** @return an outcome at once */
    static Answered answered(Outcome outcome) {
        return new Answered(outcome);
    }

    /**
     * The engine took the file and answers later.
     *
     * @param id the ticket, given to {@link Qualifier#poll}
     */
    record Ticket(String id) implements Submitted {
        public Ticket {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * The check answered at once. A pending answer needs a ticket, so it is refused here.
     *
     * @param outcome a {@link Outcome.Result} or a {@link Outcome.Failed}
     */
    record Answered(Outcome outcome) implements Submitted {
        public Answered {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome instanceof Outcome.Pending) {
                throw new IllegalArgumentException("A pending answer needs a ticket");
            }
        }
    }
}
