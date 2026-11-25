package org.jahia.modules.formidable.quarantine.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the records under a held submission say, read in one go for a page of the GraphQL API: the outcomes, the
 * decision, the audit, and the public values when they were asked for. No sensitive value, no byte.
 *
 * @param outcomes     the outcomes, by item, then by check
 * @param decision     the decision that stopped the run, or {@code null}
 * @param audit        the audit records, oldest first
 * @param publicValues the public values by field, empty when they were not asked for or were deleted
 */
public record HeldDetails(List<OutcomeRecord> outcomes, DecisionRecord decision, List<AuditRecord> audit,
                          Map<String, List<String>> publicValues) {

    public HeldDetails {
        outcomes = List.copyOf(outcomes);
        audit = List.copyOf(audit);
        // in the order of the fields
        publicValues = Collections.unmodifiableMap(new LinkedHashMap<>(publicValues));
    }
}
