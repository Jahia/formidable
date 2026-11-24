package org.jahia.test.modules.formidable.quarantine.store;

import java.util.List;

/**
 * The six states of a held submission, as the CND constrains the property {@code state}. The store writes a state
 * and checks that it is one of these; the state machine of the module decides which move is allowed.
 */
public final class HeldStates {

    public static final String HELD = "held";
    public static final String CHECKING = "checking";
    public static final String RELEASING = "releasing";
    public static final String COMPLETED = "completed";
    public static final String REJECTED = "rejected";
    public static final String NEEDS_REVIEW = "needsReview";

    /** Every state, in the order of the life of a submission. */
    public static final List<String> ALL = List.of(HELD, CHECKING, RELEASING, COMPLETED, REJECTED, NEEDS_REVIEW);

    private HeldStates() {
    }

    /** @return true when the name is one of the six states */
    public static boolean isState(String name) {
        return name != null && ALL.contains(name);
    }
}
