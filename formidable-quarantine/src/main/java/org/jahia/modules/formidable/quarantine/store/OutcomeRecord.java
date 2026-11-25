package org.jahia.modules.formidable.quarantine.store;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The last outcome of one check for one item, as {@link CheckStore} writes it under {@code checks/<item>/<check>}.
 * It holds no byte and no value.
 *
 * @param itemName  the name of the item, {@code item0} for the first file
 * @param checkName the name of the check node of the "Checks" list
 * @param sha256    the SHA-256 of the bytes the outcome was given for
 * @param outcome   one of {@link #OUTCOMES}
 * @param reason    the reason code of the check, or {@code null}
 * @param retryable for {@link #FAILED}: true when a later attempt may succeed
 * @param ticket    the ticket to poll, or {@code null}
 * @param attempts  the count of retryable failures since the last result
 * @param at        when the outcome was recorded
 */
public record OutcomeRecord(String itemName, String checkName, String sha256, String outcome, String reason,
                            boolean retryable, String ticket, int attempts, Instant at) {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String INCONCLUSIVE = "INCONCLUSIVE";
    public static final String PENDING = "PENDING";
    public static final String FAILED = "FAILED";

    /** The values of {@code outcome}, as the CND constrains them. */
    public static final List<String> OUTCOMES = List.of(PASS, FAIL, INCONCLUSIVE, PENDING, FAILED);

    public OutcomeRecord {
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(checkName, "checkName");
        Objects.requireNonNull(sha256, "sha256");
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Not an outcome: " + outcome);
        }
    }
}
