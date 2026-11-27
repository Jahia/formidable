package org.jahia.modules.formidable.quarantine.state;

import java.util.EnumSet;
import java.util.Set;

/**
 * What moves a held submission from one state to another: each event leaves the states it names, and leads to one
 * state.
 */
public enum HeldEvent {

    /** The run picks the submission for the first time. */
    START(HeldState.CHECKING, HeldState.HELD),

    /** The run goes on: the next check, a poll, or a retry. */
    CONTINUE(HeldState.CHECKING, HeldState.CHECKING),

    /** A check failed: the chain rejects. */
    CHECK_FAILED(HeldState.REJECTED, HeldState.CHECKING),

    /** Inconclusive, a withheld file, attempts exhausted, or a list that does not run. */
    REVIEW(HeldState.NEEDS_REVIEW, HeldState.CHECKING),

    /** Every check passed every file. */
    ALL_PASSED(HeldState.RELEASING, HeldState.CHECKING),

    /** The bytes differ from the SHA-256 of intake, before or during the release. */
    INTEGRITY_FAILED(HeldState.NEEDS_REVIEW, HeldState.CHECKING, HeldState.RELEASING),

    /** Every action after release ran. */
    ACTIONS_DONE(HeldState.COMPLETED, HeldState.RELEASING),

    /** An action after release failed: the run stops at its index. */
    ACTION_FAILED(HeldState.NEEDS_REVIEW, HeldState.RELEASING),

    /** A manager runs the checks again; the attempts start over. */
    MANAGER_RETRY(HeldState.CHECKING, HeldState.NEEDS_REVIEW),

    /** A manager releases the submission. */
    MANAGER_RELEASE(HeldState.RELEASING, HeldState.NEEDS_REVIEW),

    /**
     * A manager resumes the actions after release from the one that failed, or from {@code completedActions} of a
     * release that stopped half way in {@code releasing}: no action runs twice.
     */
    MANAGER_RESUME(HeldState.RELEASING, HeldState.NEEDS_REVIEW, HeldState.RELEASING),

    /** A manager rejects the submission, also one whose release stopped half way in {@code releasing}. */
    MANAGER_REJECT(HeldState.REJECTED, HeldState.NEEDS_REVIEW, HeldState.RELEASING),

    /**
     * A check failed a file of a form whose policy releases the values: the bytes of every file are deleted first,
     * and the actions after release run with the values alone.
     */
    RELEASE_VALUES(HeldState.RELEASING, HeldState.CHECKING),

    /**
     * A manager rejects a submission of a form whose policy releases the values: the bytes of every file are deleted
     * first, and the actions after release run with the values alone.
     */
    MANAGER_RELEASE_VALUES(HeldState.RELEASING, HeldState.NEEDS_REVIEW);

    private final HeldState target;
    private final Set<HeldState> sources;

    HeldEvent(HeldState target, HeldState source, HeldState... otherSources) {
        this.target = target;
        this.sources = EnumSet.of(source, otherSources);
    }

    /** @return the state the event leads to */
    public HeldState target() {
        return target;
    }

    /** @return the states the event may leave */
    public Set<HeldState> sources() {
        return EnumSet.copyOf(sources);
    }

    boolean leaves(HeldState state) {
        return sources.contains(state);
    }
}
