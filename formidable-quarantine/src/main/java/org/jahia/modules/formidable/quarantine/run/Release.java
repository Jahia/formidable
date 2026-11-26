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

import javax.jcr.RepositoryException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The release of a held submission: the bytes are read again and hashed again, and a difference from the SHA-256 of
 * intake refuses the release. Otherwise the actions after release of the form run through the engine's
 * {@link DeferredActions}, from the index the held submission records, with a checkpoint after each action. Once
 * every action ran, the values, the sensitive values and the bytes are deleted; the record and the outcomes stay.
 *
 * <p>The released files are the very files whose bytes were hashed: the bytes released are the bytes of intake, under
 * whose SHA-256 the run recorded each outcome.</p>
 *
 * <p>The policy of the form ({@link FormPolicy}) adds its scan status to the values. A form whose policy releases the
 * values goes through {@link #releaseValues}: the bytes are deleted first, and the actions run with the values alone.
 * From then on, any later release of that submission, such as a resume after a failed action, has no file to
 * release.</p>
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
    private final FormPolicies policies;

    /** A release for forms that all have the default policy. */
    public Release(QuarantineStore store, CheckStore checkStore, DeferredActions actions, Transitions transitions,
                   Clock clock) {
        this(store, checkStore, actions, transitions, clock, formId -> FormPolicy.DEFAULT);
    }

    public Release(QuarantineStore store, CheckStore checkStore, DeferredActions actions, Transitions transitions,
                   Clock clock, FormPolicies policies) {
        this.store = Objects.requireNonNull(store, "store");
        this.checkStore = Objects.requireNonNull(checkStore, "checkStore");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    /**
     * @return the policy of the form, read in live now
     * @throws QuarantineStoreException when the form cannot be read: nothing is released
     */
    public FormPolicy policy(String formId) throws QuarantineStoreException {
        try {
            return policies.of(formId);
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("Could not read the quarantine policy of form " + formId, e);
        }
    }

    /**
     * Releases the held submission, with the clean scan status of the policy of its form. A submission whose values
     * already went out alone ({@link QuarantineStore#valuesOnly}) is released with its values alone again.
     *
     * @param path   the path of the held submission
     * @param state  {@link HeldState#CHECKING} when every check passed: the release moves it on
     *               {@link HeldEvent#ALL_PASSED}; or {@link HeldState#RELEASING} when a manager released or resumed it
     * @param policy the policy of the form, read once by the caller: the run, or the operation that checked it
     * @return the state after the release: {@code completed}, or {@code needsReview} on a hash difference, an item
     *         with no bytes, a field of the form named like the scan status, or a failed action
     * @throws IllegalTransitionException for any other state: no byte is read and no action runs
     */
    public HeldState release(String path, HeldState state, FormPolicy policy) throws QuarantineStoreException {
        // refuses a state the release cannot leave, before any byte is read and any action runs
        StateMachine.next(state, state == HeldState.CHECKING ? HeldEvent.ALL_PASSED : HeldEvent.ACTIONS_DONE);
        HeldRecord held = heldRecord(path);
        if (state == HeldState.CHECKING && policy.scanStatusTaken()) {
            return scanStatusTaken(path, state, held.formId(), policy);
        }
        if (store.valuesOnly(path)) {
            // the bytes went with the first release of the values alone: whatever the event, no file goes out
            HeldState releasing = state == HeldState.CHECKING
                    ? transitions.move(path, state, HeldEvent.RELEASE_VALUES) : state;
            return runActions(path, releasing, valuesAlone(path, policy), held.completedActions());
        }
        HeldSubmission submission = read(path);
        ItemRecord changed = firstChanged(held, submission);
        if (changed != null) {
            return integrityFailed(path, state, changed);
        }
        HeldState releasing = state == HeldState.CHECKING ? transitions.move(path, state, HeldEvent.ALL_PASSED) : state;
        HeldSubmission released = withValues(submission, policy.withScanStatus(submission.parameters(), true),
                submission.files());
        return runActions(path, releasing, released, held.completedActions());
    }

    /**
     * Releases the values of the held submission alone, for a form whose policy releases the values. One save
     * deletes the bytes of every file, marks the submission and moves it to {@code releasing}
     * ({@link QuarantineStore#startValuesRelease}): a failure of that save leaves the submission as it was, and the
     * release starts again. Then the actions after release run with no file and the failed scan status of the policy.
     * The decision stays: the failed verdict, or the reason of the review that the manager rejected.
     *
     * @param path   the path of the held submission
     * @param state  {@link HeldState#CHECKING} when a check failed a file, which sends
     *               {@link HeldEvent#RELEASE_VALUES}; or {@link HeldState#NEEDS_REVIEW} when a manager rejected it,
     *               which sends {@link HeldEvent#MANAGER_RELEASE_VALUES}
     * @param policy the policy of the form, read once by the caller
     * @return the state after the release: {@code completed}, or {@code needsReview} on a failed action
     * @throws IllegalTransitionException for any other state: no byte is deleted and no action runs
     * @throws IllegalStateException      when an action after release already ran, or a field of the form has the
     *                                    name of the scan status: the caller rejects instead, and nothing changed
     */
    public HeldState releaseValues(String path, HeldState state, FormPolicy policy) throws QuarantineStoreException {
        HeldEvent event = state == HeldState.CHECKING ? HeldEvent.RELEASE_VALUES : HeldEvent.MANAGER_RELEASE_VALUES;
        HeldState releasing = StateMachine.next(state, event);
        HeldRecord held = heldRecord(path);
        if (!mayReleaseValues(held, policy)) {
            throw new IllegalStateException("The values of " + path + " cannot go out alone: an action after release "
                    + "ran, or a field of the form has the name of the scan status");
        }
        store.startValuesRelease(path, state.value(), releasing.value());
        log.info("Held submission {}: on {}, its bytes are deleted and its values go out alone", path, event);
        return runActions(path, releasing, valuesAlone(path, policy), held.completedActions());
    }

    /**
     * @return true when the values of the submission may go out alone under this policy: no action after release ran
     *         yet, so no action received the files, and no field of the form has the name of the scan status
     */
    public static boolean mayReleaseValues(HeldRecord held, FormPolicy policy) {
        return policy.releasesValues() && held.completedActions() == 0 && !policy.scanStatusTaken();
    }

    private HeldRecord heldRecord(String path) throws QuarantineStoreException {
        return store.heldRecord(path).orElseThrow(() -> new QuarantineStoreException("No held submission at " + path));
    }

    private HeldSubmission read(String path) throws QuarantineStoreException {
        return store.read(path).orElseThrow(() -> new QuarantineStoreException("No held submission at " + path));
    }

    /** The values of the submission with the failed scan status, and no file. */
    private HeldSubmission valuesAlone(String path, FormPolicy policy) throws QuarantineStoreException {
        HeldSubmission submission = read(path);
        return withValues(submission, policy.withScanStatus(submission.parameters(), false), List.of());
    }

    private static HeldSubmission withValues(HeldSubmission submission, Map<String, List<String>> values,
                                             List<SubmittedFile> files) {
        return new HeldSubmission(submission.formId(), submission.siteKey(), submission.locale(), values,
                submission.sensitiveParameters(), files, submission.origin());
    }

    /**
     * A field of the form has the name of the scan status: the run never writes the status over the value of a field,
     * so the submission goes to review. The editor renames the field or the status, and a manager acts.
     */
    private HeldState scanStatusTaken(String path, HeldState state, String formId, FormPolicy policy)
            throws QuarantineStoreException {
        if (log.isErrorEnabled()) {
            log.error("Held submission {}: a field of form {} has the name of the scan status {}, the release stops "
                    + "at review", path, formId, policy.scanStatusName());
        }
        checkStore.writeDecision(path, new DecisionRecord(DecisionRecord.SCAN_STATUS_TAKEN, null, null, null, null,
                clock.instant()));
        return transitions.move(path, state, HeldEvent.REVIEW);
    }

    /**
     * @return the first item that has no bytes, or whose bytes differ from the SHA-256 of intake, or an item named
     *         {@link #ITEM_COUNT} when the files and the items are not as many; null when every file is unchanged
     */
    static ItemRecord firstChanged(HeldRecord held, HeldSubmission submission) {
        List<ItemRecord> items = held.items();
        for (ItemRecord item : items) {
            if (!item.binaryPresent()) {
                // the bytes go only once the submission is completed, or with a release of the values alone, which
                // never comes here: an item with none lost them
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
