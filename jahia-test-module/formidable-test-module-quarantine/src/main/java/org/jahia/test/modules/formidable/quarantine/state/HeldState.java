package org.jahia.test.modules.formidable.quarantine.state;

/**
 * The state of a held submission. {@link #COMPLETED} and {@link #REJECTED} are final.
 */
public enum HeldState {

    /** Stored at intake; no check ran yet. */
    HELD("held"),

    /** The checks run. */
    CHECKING("checking"),

    /**
     * Every check passed; the actions after release run. A release that stopped half way stays here, and the run never
     * takes it: a manager resumes or rejects it.
     */
    RELEASING("releasing"),

    /** Every action after release ran. Final. */
    COMPLETED("completed"),

    /** A check failed, or a manager rejected the submission. Final. */
    REJECTED("rejected"),

    /** A manager decides: inconclusive, withheld file, attempts exhausted, or an action after release failed. */
    NEEDS_REVIEW("needsReview");

    private final String value;

    HeldState(String value) {
        this.value = value;
    }

    /** @return the value of the state as the held submission stores it */
    public String value() {
        return value;
    }

    /** @return true when no transition leaves the state */
    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED;
    }

    /**
     * @param value the value a held submission stores
     * @return the state
     * @throws IllegalArgumentException for an unknown value
     */
    public static HeldState fromValue(String value) {
        for (HeldState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown held state: " + value);
    }
}
