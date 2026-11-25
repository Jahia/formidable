package org.jahia.modules.formidable.quarantine.api;

/**
 * What a {@link Outcome.Failed} answer of a check means: the engine of the check is unavailable or broke.
 */
public enum OnUnavailable {

    /** A retryable failure is tried again later; once the attempts are exhausted, {@code needsReview}. The default. */
    RETRY("retry"),

    /** The submission is rejected. */
    REJECT("reject");

    private final String value;

    OnUnavailable(String value) {
        this.value = value;
    }

    /** @return the value of the setting as the check node stores it */
    public String value() {
        return value;
    }

    /**
     * @param value the value the check node stores, or null when the node holds none
     * @return the setting; {@link #RETRY} for a null or blank value
     * @throws IllegalArgumentException for any other value
     */
    public static OnUnavailable fromValue(String value) {
        if (value == null || value.isBlank()) {
            return RETRY;
        }
        for (OnUnavailable setting : values()) {
            if (setting.value.equals(value)) {
                return setting;
            }
        }
        throw new IllegalArgumentException("Unknown onUnavailable setting: " + value);
    }
}
