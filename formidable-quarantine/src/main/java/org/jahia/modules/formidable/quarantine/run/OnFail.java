package org.jahia.modules.formidable.quarantine.run;

/**
 * What the run does when a check answers {@code FAIL} for a file, and what a reject by a manager does: the setting
 * {@code onFail} of the form ({@code fmdbqmix:quarantinePolicy}).
 */
public enum OnFail {

    /** The submission is rejected: no action after release runs, and the values and the bytes are deleted. */
    REJECT("reject"),

    /**
     * The bytes of every file are deleted, then the actions after release run with the values alone and the failed
     * scan status.
     */
    RELEASE_VALUES("releaseValues");

    private final String value;

    OnFail(String value) {
        this.value = value;
    }

    /** @return the value of the property {@code onFail} */
    public String value() {
        return value;
    }

    /** @return the policy of the value, or {@link #REJECT} for a missing or unknown value: the default fails closed */
    public static OnFail fromValue(String value) {
        for (OnFail policy : values()) {
            if (policy.value.equals(value)) {
                return policy;
            }
        }
        return REJECT;
    }
}
