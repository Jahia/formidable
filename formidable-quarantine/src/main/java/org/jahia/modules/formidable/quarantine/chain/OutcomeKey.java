package org.jahia.modules.formidable.quarantine.chain;

import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Scope;

import java.util.Objects;

/**
 * The key of a recorded outcome: what the check judged of the file, and the name of the check. A check that reads
 * bytes judges the bytes, so its key is the SHA-256 of the file: an outcome recorded for other bytes never counts for
 * these bytes, and two files with the same bytes share one outcome. A {@link Scope#METADATA} check judges the name and
 * the declared MIME type, so its key holds them too: two files with the same bytes and another name or type never
 * share its outcome.
 *
 * @param sha256    the SHA-256 of the file
 * @param checkName the name of the check node
 * @param fileName  the name of the file, for a metadata check; null otherwise
 * @param mimeType  the declared MIME type of the file, for a metadata check; null otherwise
 */
public record OutcomeKey(String sha256, String checkName, String fileName, String mimeType) {

    public OutcomeKey {
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(checkName, "checkName");
    }

    /** The key of an outcome of a check that reads bytes: the SHA-256 alone. */
    public OutcomeKey(String sha256, String checkName) {
        this(sha256, checkName, null, null);
    }

    /** @return the key of the outcome of the check for the file, as the scope of the check decides */
    public static OutcomeKey of(QuarantinedFile file, ChainCheck check) {
        return of(file.sha256(), file, check);
    }

    /**
     * @param sha256 the SHA-256 the outcome was given for, which a recorded outcome states
     * @return the key of the outcome of the check for the file, as the scope of the check decides
     */
    public static OutcomeKey of(String sha256, QuarantinedFile file, ChainCheck check) {
        if (check.qualifier() != null && check.qualifier().scope() == Scope.METADATA) {
            return new OutcomeKey(sha256, check.name(), file.fileName(), file.mimeType());
        }
        return new OutcomeKey(sha256, check.name());
    }
}
