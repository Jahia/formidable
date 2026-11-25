package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.quarantine.state.HeldEvent;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.state.IllegalTransitionException;
import org.jahia.modules.formidable.quarantine.state.StateMachine;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.formidable.quarantine.store.Sha256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The release of a held submission: the bytes are read again and hashed again, and a difference from the SHA-256 of
 * intake refuses the release. Otherwise the actions after release of the form run through the engine's
 * {@link DeferredActions}, from the index the held submission records, with a checkpoint after each action. Once
 * every action ran, the values, the sensitive values and the bytes are deleted; the record and the outcomes stay.
 *
 * <p>The released files are the very files whose bytes were hashed: the bytes released are the bytes of intake, under
 * whose SHA-256 the run recorded each outcome.</p>
 */
public final class Release {

    private static final Logger log = LoggerFactory.getLogger(Release.class);

    /** The item named by an integrity failure when the count of files differs from the count of items. */
    static final String ITEM_COUNT = "items";

    private final QuarantineStore store;
    private final CheckStore checkStore;
    private final DeferredActions actions;
    private final Transitions transitions;
    private final Clock clock;

    public Release(QuarantineStore store, CheckStore checkStore, DeferredActions actions, Transitions transitions,
                   Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.checkStore = Objects.requireNonNull(checkStore, "checkStore");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Releases the held submission.
     *
     * @param path  the path of the held submission
     * @param state {@link HeldState#CHECKING} when every check passed: the release moves it on
     *              {@link HeldEvent#ALL_PASSED}; or {@link HeldState#RELEASING} when a manager released or resumed it
     * @return the state after the release: {@code completed}, or {@code needsReview} on a hash difference, an item
     *         with no bytes, or a failed action
     * @throws IllegalTransitionException for any other state: no byte is read and no action runs
     */
    public HeldState release(String path, HeldState state) throws QuarantineStoreException {
        return release(path, state, () -> true);
    }

    /**
     * Releases the held submission, as {@link #release(String, HeldState)} does, for a holder of the lock of the run:
     * the lease is asked before the actions after release run, and a lost lease stops the release before them.
     *
     * @throws RunStoppedException when the lease is lost before the actions: they did not run
     */
    public HeldState release(String path, HeldState state, BooleanSupplier leaseKept) throws QuarantineStoreException {
        // refuses a state the release cannot leave, before any byte is read and any action runs
        StateMachine.next(state, state == HeldState.CHECKING ? HeldEvent.ALL_PASSED : HeldEvent.ACTIONS_DONE);
        HeldRecord held = store.heldRecord(path)
                .orElseThrow(() -> new QuarantineStoreException("No held submission at " + path));
        HeldSubmission submission = store.read(path)
                .orElseThrow(() -> new QuarantineStoreException("No held submission at " + path));
        ItemRecord changed = firstChanged(held, submission);
        if (changed != null) {
            return integrityFailed(path, state, changed);
        }
        if (!leaseKept.getAsBoolean()) {
            throw new RunStoppedException("the lock of the run is lost before the release of " + path);
        }
        HeldState releasing = state == HeldState.CHECKING ? transitions.move(path, state, HeldEvent.ALL_PASSED) : state;
        return runActions(path, releasing, submission, held.completedActions());
    }

    /**
     * @return the first item that has no bytes, or whose bytes differ from the SHA-256 of intake, or an item named
     *         {@link #ITEM_COUNT} when the files and the items are not as many; null when every file is unchanged
     */
    static ItemRecord firstChanged(HeldRecord held, HeldSubmission submission) {
        List<ItemRecord> items = held.items();
        for (ItemRecord item : items) {
            if (!item.binaryPresent()) {
                // the bytes go only once the submission is completed: an item with none lost them
                return item;
            }
        }
        List<SubmittedFile> files = submission.files();
        if (files.size() != items.size()) {
            return new ItemRecord(ITEM_COUNT, null, null, null, files.size(), null, false);
        }
        for (int i = 0; i < files.size(); i++) {
            if (!Sha256.hex(files.get(i).data()).equals(items.get(i).sha256())) {
                return items.get(i);
            }
        }
        return null;
    }

    private HeldState integrityFailed(String path, HeldState state, ItemRecord changed)
            throws QuarantineStoreException {
        // names the item, never its bytes; no audit record here: the run and the operations write those
        log.warn("Held submission {}: the bytes of {} differ from the SHA-256 of intake, the release is refused",
                path, changed.name());
        checkStore.writeDecision(path, new DecisionRecord(DecisionRecord.INTEGRITY_FAILED, changed.name(), null,
                changed.sha256(), null, clock.instant()));
        return transitions.move(path, state, HeldEvent.INTEGRITY_FAILED);
    }

    private HeldState runActions(String path, HeldState releasing, HeldSubmission submission, int from)
            throws QuarantineStoreException {
        try {
            actions.run(submission, from, index -> checkpoint(path, index));
        } catch (DeferredActionException e) {
            int resumeFrom = e.failedAction() == DeferredActionException.NO_ACTION ? from : e.failedAction();
            store.setCompletedActions(path, resumeFrom);
            checkStore.writeDecision(path, new DecisionRecord(DecisionRecord.ACTION_FAILED, null, null, null,
                    resumeFrom, clock.instant()));
            log.warn("Held submission {}: the actions after release stopped, to resume from index {}: {}", path,
                    resumeFrom, e.getMessage());
            return transitions.move(path, releasing, HeldEvent.ACTION_FAILED);
        }
        // one save: a failure leaves releasing with the data, which a manager resumes or rejects
        return transitions.finish(path, releasing, HeldEvent.ACTIONS_DONE);
    }

    /** Records that the action at the index ran: a resumed run starts after it. */
    private void checkpoint(String path, int index) {
        try {
            store.setCompletedActions(path, index + 1);
        } catch (QuarantineStoreException e) {
            // the engine stops the run, and names the next index in its exception
            throw new CheckpointException("Could not record that action " + index + " of " + path + " ran", e);
        }
    }

    /** A checkpoint could not be written: the engine stops the run. */
    static final class CheckpointException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CheckpointException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
