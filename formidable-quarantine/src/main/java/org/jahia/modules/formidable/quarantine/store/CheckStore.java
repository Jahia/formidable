package org.jahia.modules.formidable.quarantine.store;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * The records of the run of the checks under a held submission, an OSGi service beside {@link QuarantineStore}: the
 * outcome of each check for each item ({@code checks/<item>/<check>}), and the decision that stopped the run
 * ({@code checks/decision}), and the audit records of the operations and of the run ({@code audit/<n>}). It writes in
 * live, with a system session, as the store does. A held submission is named
 * by its path; every method refuses a path that names no held submission.
 *
 * <p>No record holds a byte or a value. {@link #withItems} reads the bytes of the items for a check and for the
 * SHA-256 of the release, and for nothing else.</p>
 */
public interface CheckStore {

    /** The name of the decision under {@code checks}. */
    String DECISION_NODE = "decision";

    /** The identifiers of the forms that have a folder in the quarantine tree of the site. Empty when it has none. */
    List<String> forms(String siteKey) throws QuarantineStoreException;

    /** The outcomes recorded under the held submission, by item, then by check. */
    List<OutcomeRecord> outcomes(String path) throws QuarantineStoreException;

    /** Writes the outcome of one check for one item, in place of the one before. */
    void writeOutcome(String path, OutcomeRecord outcome) throws QuarantineStoreException;

    /** The decision that stopped the run of the held submission, if any. */
    Optional<DecisionRecord> decision(String path) throws QuarantineStoreException;

    /** Writes the decision that stopped the run, in place of the one before. */
    void writeDecision(String path, DecisionRecord decision) throws QuarantineStoreException;

    /**
     * Removes every outcome that is not a {@code PASS}, so that the next run calls those checks again with no failure
     * counted: a manager retries the submission. A {@code PASS} stays, for the bytes it was given for.
     */
    void keepPassedOutcomesOnly(String path) throws QuarantineStoreException;

    /** The audit records of the held submission, oldest first. */
    List<AuditRecord> audit(String path) throws QuarantineStoreException;

    /** Adds an audit record after the others, under {@code audit}. */
    void writeAudit(String path, AuditRecord audit) throws QuarantineStoreException;

    /**
     * Runs the work with the bytes of the items of the held submission open to read, in one system session. A stream
     * that {@link ItemStreams#open} hands reads the binary of the repository as the caller reads it, with no copy in
     * memory, and only until the work returns: the session closes then, and a later read of the stream throws.
     *
     * @return what the work returns
     * @throws QuarantineStoreException when the held submission cannot be read, or the work threw it
     */
    <T> T withItems(String path, ItemWork<T> work) throws QuarantineStoreException;

    /** What reads the bytes of the items while {@link #withItems} holds the session. */
    @FunctionalInterface
    interface ItemWork<T> {
        T run(ItemStreams items) throws QuarantineStoreException;
    }

    /** The bytes of the items of one held submission, during {@link #withItems}. */
    @FunctionalInterface
    interface ItemStreams {

        /**
         * @return a new stream over the bytes of the item, which the caller closes
         * @throws QuarantineStoreException when the item does not exist, or holds no bytes any more
         */
        InputStream open(String itemName) throws QuarantineStoreException;
    }
}
