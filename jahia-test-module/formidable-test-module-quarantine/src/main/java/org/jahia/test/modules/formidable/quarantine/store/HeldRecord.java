package org.jahia.test.modules.formidable.quarantine.store;

import java.time.Instant;
import java.util.List;

/**
 * What the store says of one held submission: its state and its items, the names of its fields, and no value and no
 * byte. A run reads it to decide; it reads the values and the bytes through {@link QuarantineStore#read} only to
 * release.
 *
 * @param path             the path of the held submission, its identifier for every method of the store
 * @param formId           the identifier of the form, in live
 * @param siteKey          the key of the site of the form
 * @param state            one of {@link HeldStates#ALL}
 * @param createdAt        the time of intake
 * @param attempts         the number of attempts of the checks so far
 * @param nextAttempt      when the next attempt is due, or {@code null}
 * @param completedActions the number of actions after release that ran: the index a resumed run starts from
 * @param valueFields      the names of the public fields, in the order of the submission
 * @param sensitiveFields  the names of the sensitive fields, in the order of the submission
 * @param valuesPresent    false once the values were deleted
 * @param items            the items, in the order of the files of the submission
 * @param changedAt        the last change of the state, or the time of intake when the state never changed
 */
public record HeldRecord(String path, String formId, String siteKey, String state, Instant createdAt, int attempts,
                         Instant nextAttempt, int completedActions, List<String> valueFields,
                         List<String> sensitiveFields, boolean valuesPresent, List<ItemRecord> items,
                         Instant changedAt) {

    public HeldRecord {
        valueFields = List.copyOf(valueFields);
        sensitiveFields = List.copyOf(sensitiveFields);
        items = List.copyOf(items);
    }
}
