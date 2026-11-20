package org.jahia.test.modules.formidable.quarantine.check;

/**
 * What an {@link Outcome.Verdict#INCONCLUSIVE} answer of a check means. There is no "accept": the chain fails closed.
 */
public enum OnInconclusive {

    /** The submission waits for a manager: {@code needsReview}. The default. */
    REVIEW("review"),

    /** The submission is rejected. */
    REJECT("reject");

    private final String value;

    OnInconclusive(String value) {
        this.value = value;
    }

    /** @return the value of the setting as the check node stores it */
    public String value() {
        return value;
    }

    /**
     * @param value the value the check node stores, or null when the node holds none
     * @return the setting; {@link #REVIEW} for a null or blank value
     * @throws IllegalArgumentException for any other value
     */
    public static OnInconclusive fromValue(String value) {
        if (value == null || value.isBlank()) {
            return REVIEW;
        }
        for (OnInconclusive setting : values()) {
            if (setting.value.equals(value)) {
                return setting;
            }
        }
        throw new IllegalArgumentException("Unknown onInconclusive setting: " + value);
    }
}
