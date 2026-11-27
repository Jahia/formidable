package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.run.FormPolicy;
import org.jahia.modules.formidable.quarantine.run.QuarantineRun;
import org.jahia.modules.formidable.quarantine.run.Release;
import org.jahia.modules.formidable.quarantine.run.Transitions;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.state.StateMachine;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The operations of a manager on a held submission in state {@code needsReview}: retry, release, resume and reject, an
 * OSGi service. A release that stopped half way in {@code releasing} (a store failure, an error of an action, a stop of
 * the JVM) takes a resume or a reject only. Each operation checks, in this order, that a held submission is at the path, that the user of the
 * request is a manager of its form ({@link Managers}), and that the state machine and the decision allow it. A refused
 * operation changes nothing. An operation that ran writes one audit record: the operation, the name of the user, the
 * state before and after, and when. No value. An operation that failed after it moved the state writes one too, with
 * the state reached, and so does a resume that failed: a resume of a release stopped in {@code releasing} stays
 * there.
 *
 * <ul>
 * <li>retry: the attempts start over, every outcome that is not a {@code PASS} is dropped, and the state moves to
 * {@code checking}: the next run calls those checks again;</li>
 * <li>release: the state moves to {@code releasing}, and the release of the module runs, from the hash check on. It is
 * refused when an action after release failed: that is a resume;</li>
 * <li>resume: the same, allowed in {@code needsReview} only when an action after release failed
 * ({@code ACTION_FAILED}), and always in {@code releasing}. The release starts at the index {@code completedActions},
 * so the actions that ran do not run again;</li>
 * <li>reject: the state moves to {@code rejected}, and the values, the sensitive values and the bytes are deleted,
 * in one save: a failure keeps the state and the data. The outcomes and the decision stay. In {@code needsReview}, a
 * form whose policy releases the values ({@code onFail=releaseValues}) makes the reject delete the bytes and move to
 * {@code releasing} in one save, then run the actions after release with the values alone
 * ({@code MANAGER_RELEASE_VALUES}), to {@code completed}; the decision and the audit keep the reason of the review and
 * the reject. It stays a plain reject when an action after release already ran, when the values already went out
 * alone, or when a field of the form has the name of the scan status.</li>
 * </ul>
 *
 * <p>A release and a resume are refused with {@code CONFLICT} when a field of the form has the name of the scan status
 * of its policy: neither writes the status over the value of a field.</p>
 */
@Component(service = QuarantineOperations.class, immediate = true)
public class QuarantineOperations {

    private static final Logger log = LoggerFactory.getLogger(QuarantineOperations.class);

    private final Managers managers;
    private final Clock clock;
    private QuarantineStore store;
    private CheckStore checkStore;
    private Supplier<Release> release;
    /** The lock of the run: an operation never runs while the run checks or releases a submission. */
    private Object lock = this;

    public QuarantineOperations() {
        this(Managers.ofThePlatform(), Clock.systemUTC());
    }

    QuarantineOperations(Managers managers, Clock clock) {
        this.managers = managers;
        this.clock = clock;
    }

    @Reference
    public void setStore(QuarantineStore store) {
        this.store = store;
    }

    @Reference
    public void setCheckStore(CheckStore checkStore) {
        this.checkStore = checkStore;
    }

    @Reference
    public void setRun(QuarantineRun run) {
        this.release = run::release;
        this.lock = run;
    }

    /** The release the operations call, for a test that builds it from the stores in memory. */
    void setRelease(Supplier<Release> release) {
        this.release = release;
    }

    /**
     * Runs the operation on the held submission, for the user of the current request. One operation at a time, and
     * never while the run checks or releases submissions: the operation takes the lock of the run, so a submission in
     * {@code releasing} is a release that stopped, not one under way.
     *
     * @throws OperationRefusedException when the user is not a manager of the form ({@code FORBIDDEN}), when no held
     *                                   submission is at the path ({@code NOT_FOUND} for a server administrator,
     *                                   {@code FORBIDDEN} for anyone else), or ({@code CONFLICT}) when the state or the
     *                                   decision does not allow the operation, or a release or a resume would write
     *                                   the scan status over a field of the form: nothing changed
     */
    public OperationResult apply(String path, Operation operation)
            throws OperationRefusedException, QuarantineStoreException {
        synchronized (lock) {
            return applyLocked(path, operation);
        }
    }

    private OperationResult applyLocked(String path, Operation operation)
            throws OperationRefusedException, QuarantineStoreException {
        Optional<HeldRecord> found = store.heldRecord(path);
        if (found.isEmpty()) {
            // only a server administrator learns that a path names nothing
            throw new OperationRefusedException(managers.isServerAdministrator()
                    ? OperationRefusedException.Reason.NOT_FOUND : OperationRefusedException.Reason.FORBIDDEN,
                    "No held submission that the user manages at " + path);
        }
        HeldRecord held = found.get();
        if (!managers.mayManage(held.formId())) {
            if (log.isInfoEnabled()) {
                log.info("Held submission {}: {} refused to {}, who does not manage form {}", path, operation.value(),
                        managers.currentUserName(), held.formId());
            }
            throw new OperationRefusedException(OperationRefusedException.Reason.FORBIDDEN,
                    "The user does not manage the quarantine of the form");
        }
        HeldState from = HeldState.fromValue(held.state());
        if (!StateMachine.allows(from, operation.event()) || !fitsTheDecision(path, from, operation)) {
            throw new OperationRefusedException(OperationRefusedException.Reason.CONFLICT,
                    "The operation " + operation.value() + " is not allowed in state " + from.value());
        }
        Plan plan = plan(path, held, operation, from);
        String user = managers.currentUserName();
        HeldState to;
        try {
            to = run(path, operation, from, plan);
        } catch (QuarantineStoreException | RuntimeException e) {
            auditStopped(path, operation, user, from);
            throw e;
        }
        writeAudit(path, new AuditRecord(operation.value(), user, from.value(), to.value(), clock.instant()));
        if (log.isInfoEnabled()) {
            log.info("Held submission {}: {} by {}, from {} to {}", path, operation.value(), user, from.value(),
                    to.value());
        }
        return new OperationResult(path, operation, from, to);
    }

    /** The operation already ran: a failure to write its audit record is logged, and the result stands. */
    private void writeAudit(String path, AuditRecord audit) {
        try {
            checkStore.writeAudit(path, audit);
        } catch (QuarantineStoreException e) {
            log.warn("Held submission {}: the audit record of {} could not be written: {}", path, audit.operation(),
                    e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Held submission {}: the audit record of {} could not be written: {}", path, audit.operation(),
                    e.getClass().getName());
        }
    }

    /**
     * An operation that failed after it moved the state, such as a release whose action threw, or a resume of a
     * stopped release that stopped again: one audit record names the user and the state reached, so a stopped release
     * says who started it. A failure to write it is logged.
     */
    private void auditStopped(String path, Operation operation, String user, HeldState from) {
        try {
            String reached = store.heldRecord(path).map(HeldRecord::state).orElse(null);
            if (reached != null && (!reached.equals(from.value()) || operation == Operation.RESUME)) {
                checkStore.writeAudit(path, new AuditRecord(operation.value(), user, from.value(), reached,
                        clock.instant()));
            }
        } catch (QuarantineStoreException e) {
            log.warn("Held submission {}: the audit record of a failed {} could not be written: {}", path,
                    operation.value(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Held submission {}: the audit record of a failed {} could not be written: {}", path,
                    operation.value(), e.getClass().getName());
        }
    }

    /**
     * In {@code needsReview}, a resume needs an action after release that failed, and a release needs any other reason
     * of review but a form that is gone, whose actions after release cannot run. A release that stopped half way in
     * {@code releasing} resumes whatever the decision says.
     */
    private boolean fitsTheDecision(String path, HeldState from, Operation operation) throws QuarantineStoreException {
        if (from != HeldState.NEEDS_REVIEW || operation != Operation.RESUME && operation != Operation.RELEASE) {
            return true;
        }
        String reason = checkStore.decision(path).map(DecisionRecord::reason).orElse(null);
        if (operation == Operation.RESUME) {
            return DecisionRecord.ACTION_FAILED.equals(reason);
        }
        return !DecisionRecord.ACTION_FAILED.equals(reason) && !DecisionRecord.FORM_GONE.equals(reason);
    }

    /**
     * What an operation does under the policy of the form, read once for the operation.
     *
     * @param policy      the policy of the form, or the default one when the operation does not read it
     * @param valuesAlone true when the reject releases the values alone
     */
    private record Plan(FormPolicy policy, boolean valuesAlone) {

        static final Plan PLAIN = new Plan(FormPolicy.DEFAULT, false);
    }

    /**
     * Reads the policy of the form, once, for an operation it can change: a release, a resume, and a reject in
     * {@code needsReview}. A retry, and a reject of a release that stopped in {@code releasing}, read nothing. A reject
     * releases the values alone when the policy asks for it, no action after release ran, the values did not already go
     * out alone, and no field of the form has the name of the scan status; otherwise it is a plain reject.
     *
     * @throws OperationRefusedException when a release or a resume would write the scan status over the value of a
     *                                   field of the form that has its name
     */
    private Plan plan(String path, HeldRecord held, Operation operation, HeldState from)
            throws OperationRefusedException, QuarantineStoreException {
        if (operation == Operation.RETRY || (operation == Operation.REJECT && from != HeldState.NEEDS_REVIEW)) {
            return Plan.PLAIN;
        }
        FormPolicy policy = release.get().policy(held.formId());
        if (operation == Operation.REJECT) {
            if (policy.releasesValues() && policy.scanStatusTaken() && log.isErrorEnabled()) {
                log.error("Held submission {}: a field of form {} has the name of the scan status {}, the reject does "
                        + "not release the values", path, held.formId(), policy.scanStatusName());
            }
            return new Plan(policy, Release.mayReleaseValues(held, policy) && !store.valuesOnly(path));
        }
        if (policy.scanStatusTaken()) {
            if (log.isErrorEnabled()) {
                log.error("Held submission {}: {} refused, a field of form {} has the name of the scan status {}",
                        path, operation.value(), held.formId(), policy.scanStatusName());
            }
            throw new OperationRefusedException(OperationRefusedException.Reason.CONFLICT,
                    "A field of the form has the name of the scan status of its quarantine policy");
        }
        return new Plan(policy, false);
    }

    private HeldState run(String path, Operation operation, HeldState from, Plan plan)
            throws QuarantineStoreException {
        Transitions transitions = new Transitions(store);
        switch (operation) {
            case RETRY:
                store.setAttempts(path, 0, null);
                checkStore.keepPassedOutcomesOnly(path);
                return transitions.move(path, from, operation.event());
            case REJECT:
                if (plan.valuesAlone()) {
                    // policy releaseValues: the bytes go first, then the actions after release run with the values
                    // alone and the failed scan status, so the people the actions reach learn of the reject
                    return release.get().releaseValues(path, from, plan.policy());
                }
                // one save: a failure keeps the state and the data, and the manager rejects again
                return transitions.finish(path, from, operation.event());
            default:
                HeldState releasing = transitions.move(path, from, operation.event());
                return release.get().release(path, releasing, plan.policy());
        }
    }
}
