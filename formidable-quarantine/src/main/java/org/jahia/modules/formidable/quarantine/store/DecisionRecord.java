package org.jahia.modules.formidable.quarantine.store;

import java.time.Instant;
import java.util.Objects;

/**
 * Why the run stopped a held submission, as {@link CheckStore} writes it under {@code checks/decision}. The last
 * decision replaces the one before.
 *
 * @param reason       a reason of the chain, such as {@code CHECK_FAILED}, or {@link #INTEGRITY_FAILED}, or
 *                     {@link #ACTION_FAILED}, or {@link #FORM_GONE}, or {@link #MANAGER_REJECTED}, or
 *                     {@link #SCAN_STATUS_TAKEN}
 * @param itemName     the item the reason is about, or {@code null}
 * @param checkName    the check the reason is about, or {@code null}
 * @param sha256       the SHA-256 of the file the reason is about, or {@code null}
 * @param failedAction for {@link #ACTION_FAILED}: the index of the action after release to resume from; otherwise
 *                     {@code null}
 * @param at           when the decision was taken
 */
public record DecisionRecord(String reason, String itemName, String checkName, String sha256, Integer failedAction,
                             Instant at) {

    /** The bytes of an item differ from the SHA-256 of intake: no release. */
    public static final String INTEGRITY_FAILED = "INTEGRITY_FAILED";

    /** An action after release failed: the run stopped at its index. */
    public static final String ACTION_FAILED = "ACTION_FAILED";

    /** The form of the submission is gone, so its checks cannot be read: no check runs and no release. */
    public static final String FORM_GONE = "FORM_GONE";

    /**
     * A manager rejected a submission of a form whose policy releases the values: the bytes are deleted, and the values
     * go out alone.
     */
    public static final String MANAGER_REJECTED = "MANAGER_REJECTED";

    /** A field of the form has the name of the scan status of its policy: no release writes the status over it. */
    public static final String SCAN_STATUS_TAKEN = "SCAN_STATUS_TAKEN";

    public DecisionRecord {
        Objects.requireNonNull(reason, "reason");
    }
}
