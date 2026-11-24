package org.jahia.modules.formidable.quarantine.chain;

import java.util.Objects;

/**
 * The key of a recorded outcome: the SHA-256 of the file it was given for, and the name of the check. An outcome
 * recorded for other bytes never counts for these bytes.
 *
 * @param sha256    the SHA-256 of the file
 * @param checkName the name of the check node
 */
public record OutcomeKey(String sha256, String checkName) {

    public OutcomeKey {
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(checkName, "checkName");
    }
}
