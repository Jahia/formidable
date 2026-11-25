package org.jahia.modules.formidable.quarantine.store;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The records of the run of the checks under a held submission, an OSGi service beside {@link QuarantineStore}: the
 * outcome of each check for each item ({@code checks/<item>/<check>}), and the decision that stopped the run
 * ({@code checks/decision}), and the audit records of the operations and of the run ({@code audit/<n>}). It writes in
 * live, with a system session, as the store does. A held submission is named
 * by its path; every method refuses a path that names no held submission.
 *
 * <p>No record holds a byte or a value. {@link #openItem} reads the bytes of an item for a check, and for nothing
 * else.</p>
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
     * The bytes of one item, for a check. The whole item is read into memory inside the system session, once per
     * call, and handed back as a stream over that copy, which the caller closes: the session is closed when this
     * method returns. Nothing is streamed from the repository.
     *
     * @throws QuarantineStoreException when the item does not exist, or holds no bytes any more
     */
    InputStream openItem(String path, String itemName) throws QuarantineStoreException;

    /**
     * The outcomes, the decision and the audit of several held submissions, in one session: a page of the GraphQL API
     * reads them once, not once per field and per submission. A path that names no held submission is left out.
     *
     * @param paths      the held submissions
     * @param withValues the paths among them whose public values to read too; never a sensitive value
     * @return the details by path
     */
    Map<String, HeldDetails> details(Collection<String> paths, Set<String> withValues) throws QuarantineStoreException;
}
