package org.jahia.modules.formidable.quarantine.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jahia.modules.formidable.quarantine.api.OnInconclusive;
import org.jahia.modules.formidable.quarantine.api.OnUnavailable;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Scope;

/**
 * The rules of the "Checks" list of a form, as a pure function: given the checks in the order of the list, the files
 * of a submission and the outcomes recorded so far, it decides the calls to make now, or a terminal decision.
 * <p>
 * The chain fails closed: no decision is {@link ChainDecision#RELEASE} unless every check that applies to a file
 * recorded {@code PASS} for its bytes, for every file.
 * <ul>
 * <li>A check receives a file only when every earlier check of the list that applies to the file passed it.</li>
 * <li>A {@code FAIL} on any file rejects the whole submission, and no later call is made.</li>
 * <li>An in-process check that applies to a file before any remote check that applies to it refuses the whole
 * list ({@code needsReview}), unless the operator allows it. A metadata check reads no byte: it stands at any place
 * and never counts as the remote check that comes first.</li>
 * <li>A file that no check applies to is withheld ({@code needsReview}), once the other files are decided.</li>
 * <li>An empty list, a submission with no file, or a check that no qualifier serves gives {@code needsReview}.</li>
 * </ul>
 * The run calls {@link #decide} again after it records the answers of the steps. When a pass of steps brings no
 * new terminal answer and no new {@code PASS}, the run waits for the back-off.
 */
public final class CheckChain {

    private final boolean allowInProcessFirst;
    private final int maxAttempts;

    /**
     * @param allowInProcessFirst true to lift the refusal of an in-process check placed first; an operator setting,
     *                            false by default
     * @param maxAttempts         the count of retryable failures since the last result after which a check is given
     *                            up
     */
    public CheckChain(boolean allowInProcessFirst, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts < 1");
        }
        this.allowInProcessFirst = allowInProcessFirst;
        this.maxAttempts = maxAttempts;
    }

    /**
     * @param checks   the checks in the order of the list
     * @param files    the files of the submission, in a stable order
     * @param recorded the outcomes recorded so far, by SHA-256 of the file and name of the check
     * @return the calls to make now, or a terminal decision
     */
    public ChainDecision decide(List<ChainCheck> checks, List<? extends QuarantinedFile> files,
                                Map<OutcomeKey, RecordedOutcome> recorded) {
        Objects.requireNonNull(recorded, "recorded");
        ChainDecision refusal = refuseList(checks, files);
        if (refusal != null) {
            return refusal;
        }
        List<List<ChainCheck>> applicable = new ArrayList<>(files.size());
        for (QuarantinedFile file : files) {
            applicable.add(applicableChecks(checks, file));
        }
        ChainDecision inProcessFirst = refuseInProcessFirst(files, applicable);
        if (inProcessFirst != null) {
            return inProcessFirst;
        }
        return walk(files, applicable, recorded);
    }

    private static ChainDecision refuseList(List<ChainCheck> checks, List<? extends QuarantinedFile> files) {
        if (checks.isEmpty()) {
            return new ChainDecision.Review(ChainReason.NO_CHECK, null, null);
        }
        if (files.isEmpty()) {
            return new ChainDecision.Review(ChainReason.NO_FILE, null, null);
        }
        for (ChainCheck check : checks) {
            if (!check.isResolved()) {
                return new ChainDecision.Review(ChainReason.QUALIFIER_MISSING, null, check.name());
            }
        }
        return null;
    }

    private static List<ChainCheck> applicableChecks(List<ChainCheck> checks, QuarantinedFile file) {
        List<ChainCheck> applicable = new ArrayList<>();
        for (ChainCheck check : checks) {
            if (check.qualifier().appliesTo(file, check.settings())) {
                applicable.add(check);
            }
        }
        return applicable;
    }

    private ChainDecision refuseInProcessFirst(List<? extends QuarantinedFile> files,
                                               List<List<ChainCheck>> applicable) {
        if (allowInProcessFirst) {
            return null;
        }
        for (int i = 0; i < files.size(); i++) {
            ChainCheck first = firstInProcessBeforeRemote(applicable.get(i));
            if (first != null) {
                return new ChainDecision.Review(ChainReason.IN_PROCESS_FIRST, files.get(i).sha256(), first.name());
            }
        }
        return null;
    }

    /** A {@link Scope#METADATA} check is neither: the walk goes past it. */
    private static ChainCheck firstInProcessBeforeRemote(List<ChainCheck> checks) {
        for (ChainCheck check : checks) {
            if (check.qualifier().scope() == Scope.REMOTE) {
                return null;
            }
            if (check.qualifier().scope() == Scope.IN_PROCESS) {
                return check;
            }
        }
        return null;
    }

    private ChainDecision walk(List<? extends QuarantinedFile> files, List<List<ChainCheck>> applicable,
                               Map<OutcomeKey, RecordedOutcome> recorded) {
        ChainDecision.Review review = null;
        QuarantinedFile withheld = null;
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            QuarantinedFile file = files.get(i);
            List<ChainCheck> checks = applicable.get(i);
            if (checks.isEmpty()) {
                withheld = withheld == null ? file : withheld;
                continue;
            }
            ChainDecision decision = frontier(file, checks, recorded);
            if (decision instanceof ChainDecision.Reject) {
                return decision;
            }
            if (decision instanceof ChainDecision.Review fileReview && review == null) {
                review = fileReview;
            }
            if (decision instanceof ChainDecision.Proceed proceed) {
                steps.addAll(proceed.steps());
            }
        }
        return conclude(review, steps, withheld);
    }

    private static ChainDecision conclude(ChainDecision.Review review, List<Step> steps, QuarantinedFile withheld) {
        if (review != null) {
            return review;
        }
        if (!steps.isEmpty()) {
            return new ChainDecision.Proceed(steps);
        }
        if (withheld != null) {
            return new ChainDecision.Review(ChainReason.FILE_WITHHELD, withheld.sha256(), null);
        }
        return ChainDecision.RELEASE;
    }

    /**
     * @return null when every check of the file passed it; otherwise the decision at the first check that did not
     */
    private ChainDecision frontier(QuarantinedFile file, List<ChainCheck> checks,
                                   Map<OutcomeKey, RecordedOutcome> recorded) {
        for (ChainCheck check : checks) {
            RecordedOutcome outcome = recorded.get(new OutcomeKey(file.sha256(), check.name()));
            if (outcome == null) {
                return proceed(new Step.Submit(file, check));
            }
            ChainDecision decision = judge(file, check, outcome);
            if (decision != null) {
                return decision;
            }
        }
        return null;
    }

    /**
     * @return null when the outcome is a {@code PASS}; otherwise what follows from it
     */
    private ChainDecision judge(QuarantinedFile file, ChainCheck check, RecordedOutcome recorded) {
        Outcome outcome = recorded.outcome();
        if (outcome instanceof Outcome.Result result) {
            return judgeResult(file, check, result);
        }
        if (outcome instanceof Outcome.Failed failed) {
            return judgeFailure(file, check, recorded, failed);
        }
        return proceed(new Step.Poll(file, check, recorded.ticket()));
    }

    private static ChainDecision judgeResult(QuarantinedFile file, ChainCheck check, Outcome.Result result) {
        switch (result.verdict()) {
            case PASS:
                return null;
            case FAIL:
                return new ChainDecision.Reject(ChainReason.CHECK_FAILED, file.sha256(), check.name());
            default:
                if (check.settings().onInconclusive() == OnInconclusive.REJECT) {
                    return new ChainDecision.Reject(ChainReason.INCONCLUSIVE, file.sha256(), check.name());
                }
                return new ChainDecision.Review(ChainReason.INCONCLUSIVE, file.sha256(), check.name());
        }
    }

    private ChainDecision judgeFailure(QuarantinedFile file, ChainCheck check, RecordedOutcome recorded,
                                      Outcome.Failed failed) {
        if (check.settings().onUnavailable() == OnUnavailable.REJECT) {
            return new ChainDecision.Reject(ChainReason.UNAVAILABLE, file.sha256(), check.name());
        }
        if (!failed.retryable()) {
            return new ChainDecision.Review(ChainReason.UNAVAILABLE, file.sha256(), check.name());
        }
        if (recorded.attempts() >= maxAttempts) {
            return new ChainDecision.Review(ChainReason.ATTEMPTS_EXHAUSTED, file.sha256(), check.name());
        }
        if (recorded.ticket() != null) {
            return proceed(new Step.Poll(file, check, recorded.ticket()));
        }
        return proceed(new Step.Submit(file, check));
    }

    private static ChainDecision proceed(Step step) {
        return new ChainDecision.Proceed(List.of(step));
    }
}
