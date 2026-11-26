package org.jahia.modules.formidable.quarantine.store;

import org.jahia.modules.formidable.engine.api.HeldSubmission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The quarantine tree of the module, an OSGi service: {@code /sites/<site>/formidable-quarantine/<form>/YYYY/MM/DD/
 * <held>}, in live, written and read with a system session. The hold writes a submission here at intake; the run
 * of the checks reads it, moves its state and releases it; the operations change it by hand.
 *
 * <p>A held submission is named by its path, the {@link HeldRecord#path()} that {@link #hold} and {@link #list}
 * return. Every method that takes a path refuses a node that is not a held submission.</p>
 *
 * <p>No method returns a sensitive value but {@link #read}, which the release alone calls: it hands the submission
 * back to {@code DeferredActions}. No method writes a log line that holds a value.</p>
 */
public interface QuarantineStore {

    /**
     * Stores a submission at intake, with state {@link HeldStates#HELD}: its public values, its sensitive values
     * apart, and one item for each file, with the SHA-256 of its bytes. Creates the root of the tree on first use,
     * with its ACL inheritance cut and no access control entry.
     *
     * @return the path of the held submission
     * @throws QuarantineStoreException when the submission names no site, or the repository refused the write
     */
    String hold(HeldSubmission submission) throws QuarantineStoreException;

    /**
     * The held submission as the engine handed it, with its values, its sensitive values and its files, for the
     * release. A file whose bytes were deleted is left out. The files keep the order of {@link HeldRecord#items()}.
     *
     * @return empty when no held submission is at this path
     */
    Optional<HeldSubmission> read(String path) throws QuarantineStoreException;

    /**
     * What the store says of a held submission, with no value and no byte.
     *
     * @return empty when no held submission is at this path
     */
    Optional<HeldRecord> heldRecord(String path) throws QuarantineStoreException;

    /** The held submissions of a form, oldest first. Empty when the site or the form has none. */
    List<HeldRecord> list(String siteKey, String formId) throws QuarantineStoreException;

    /**
     * Writes the state, and the time of the change ({@link HeldRecord#changedAt()}), when the stored state is still
     * {@code expected}: a compare-and-set, read and saved in one session. The store checks only that the state is one
     * of {@link HeldStates#ALL}: the state machine of the module decides which move is allowed.
     *
     * @throws StaleStateException when the stored state is not {@code expected}, or another session saved the held
     *                             submission at the same moment: nothing changed
     */
    void setState(String path, String expected, String state) throws QuarantineStoreException;

    /**
     * Starts the checks of a held submission again, for a retry of a manager, in one save: the attempts start over at
     * 0 with no next attempt, every outcome that is not a {@code PASS} is dropped, and the state is written. It is a
     * compare-and-set, as {@link #setState} is: when the stored state is not {@code expected}, nothing changes, the
     * attempts and the outcomes included.
     *
     * @throws StaleStateException when the stored state is not {@code expected}, or another session saved the held
     *                             submission at the same moment: nothing changed
     */
    void restartChecks(String path, String expected, String state) throws QuarantineStoreException;

    /**
     * Writes the number of attempts of the checks and when the next one is due.
     *
     * @param nextAttempt {@code null} removes the date: no attempt is due
     */
    void setAttempts(String path, int attempts, Instant nextAttempt) throws QuarantineStoreException;

    /** Writes the number of actions after release that ran: the index a resumed run starts from. */
    void setCompletedActions(String path, int completedActions) throws QuarantineStoreException;

    /**
     * Writes a final state, {@link HeldStates#COMPLETED} or {@link HeldStates#REJECTED}, with the time of the change
     * ({@link HeldRecord#changedAt()}, which the retention reads), and deletes the public values, the sensitive
     * values and the bytes of every item, in one save: a failure writes neither, so a final
     * state never keeps its data. The record stays: the state, the names of the fields, and of each item its name,
     * size and SHA-256.
     *
     * The state is written when the stored state is still {@code expected}, as {@link #setState} does.
     *
     * @throws StaleStateException      when the stored state is not {@code expected}, or another session saved the
     *                                  held submission at the same moment: nothing changed
     * @throws QuarantineStoreException for any other state, or when the repository refused the save: nothing changed
     */
    void finish(String path, String expected, String state) throws QuarantineStoreException;

    /**
     * Removes the whole held submission: its items, its values, the outcomes and the decision of its checks, and its
     * audit. The retention calls it. The datastore of the repository frees the bytes only at its garbage collection.
     */
    void purge(String path) throws QuarantineStoreException;
}
