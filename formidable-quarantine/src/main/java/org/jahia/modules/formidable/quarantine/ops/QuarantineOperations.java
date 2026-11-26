package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.run.LeaseKeeper;
import org.jahia.modules.formidable.quarantine.run.QuarantineRun;
import org.jahia.modules.formidable.quarantine.run.Release;
import org.jahia.modules.formidable.quarantine.run.SiteLocks;
import org.jahia.modules.formidable.quarantine.run.Transitions;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.state.StateMachine;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.formidable.quarantine.store.QuarantineTree;
import org.jahia.modules.formidable.quarantine.store.RunLock;
import org.jahia.modules.formidable.quarantine.store.StaleStateException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
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
 * in one save: a failure keeps the state and the data. The outcomes and the decision stay.</li>
 * </ul>
 *
 * <p>Concurrency. An operation takes the lock of the site of the submission in this JVM ({@link SiteLocks}), which
 * a pass of the run holds for the whole pass: it waits {@link SiteLocks#OPERATION_WAIT} at most, then answers a
 * conflict, "a run is in progress, retry". On another server of the cluster, the scheduled run may work on the same
 * site: every state move is a compare-and-set ({@link Transitions}), so an operation on a submission that the run
 * moved first answers a conflict, and a submission enters {@code releasing} once. A release and a resume, which run
 * the actions after release, and a reject of a submission in {@code releasing}, also take the lock of the run of the
 * site in the repository ({@link RunLock}) for their duration: so a resume of a release that stopped never runs beside
 * a release under way on another server. A submission in {@code releasing} is then a
 * release that stopped, not one under way.</p>
 */
@Component(service = QuarantineOperations.class, immediate = true)
public class QuarantineOperations {

    private static final Logger log = LoggerFactory.getLogger(QuarantineOperations.class);
    /** The lease of a release when no run is bound, as in a test: the default of {@code lockLeaseSeconds}. */
    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(10);

    private final Managers managers;
    private final Clock clock;
    private QuarantineStore store;
    private CheckStore checkStore;
    private Supplier<Release> release;
    private RunLock runLock;
    /** The locks per site, shared with the run; a test that sets none gets locks of its own. */
    private SiteLocks siteLocks = new SiteLocks();
    /** How long a release or a resume leases the lock of the run of the site: {@code lockLeaseSeconds} of the run. */
    private Supplier<Duration> leaseLength = () -> DEFAULT_LEASE;

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
        this.leaseLength = run::leaseLength;
    }

    @Reference
    public void setRunLock(RunLock runLock) {
        this.runLock = runLock;
    }

    @Reference
    public void setSiteLocks(SiteLocks siteLocks) {
        this.siteLocks = siteLocks;
    }


    /** The release the operations call, for a test that builds it from the stores in memory. */
    void setRelease(Supplier<Release> release) {
        this.release = release;
    }

    /**
     * Runs the operation on the held submission, for the user of the current request, under the lock of its site in
     * this JVM.
     *
     * @throws OperationRefusedException when the user is not a manager of the form, when no held submission is at the
     *                                   path, when the state does not allow the operation, or when a run holds the
     *                                   site or moved the submission first (a conflict): nothing changed
     */
    public OperationResult apply(String path, Operation operation)
            throws OperationRefusedException, QuarantineStoreException {
        String siteKey = siteKeyOf(path);
        if (siteKey == null) {
            // no site: no held submission either, and applyLocked refuses it as such
            return applyLocked(path, operation);
        }
        if (!siteLocks.tryLock(siteKey, SiteLocks.OPERATION_WAIT)) {
            throw new OperationRefusedException(OperationRefusedException.Reason.CONFLICT, RUN_IN_PROGRESS);
        }
        try {
            return applyLocked(path, operation);
        } finally {
            siteLocks.of(siteKey).unlock();
        }
    }

    /** The answer of an operation that a run of the site holds back. */
    static final String RUN_IN_PROGRESS = "A run of the quarantine of the site is in progress: retry";

    /** The key of the site of a path under {@code /sites/<key>/}, or null. */
    static String siteKeyOf(String path) {
        String prefix = QuarantineTree.sitePath("");
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }
        int end = path.indexOf('/', prefix.length());
        return end <= prefix.length() ? null : path.substring(prefix.length(), end);
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
        String user = managers.currentUserName();
        HeldState to;
        try {
            to = run(held, operation, from);
        } catch (StaleStateException e) {
            // a run or an operation moved it first, and audits its own move
            throw new OperationRefusedException(OperationRefusedException.Reason.CONFLICT,
                    "The held submission was moved by a run or another operation: reload it");
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

    private HeldState run(HeldRecord held, Operation operation, HeldState from)
            throws QuarantineStoreException, OperationRefusedException {
        String path = held.path();
        Transitions transitions = new Transitions(store);
        switch (operation) {
            case RETRY:
                // the attempts, the outcomes and the state in one compare-and-set save: a stale retry changes nothing
                return transitions.retry(path, from);
            case REJECT:
                if (from == HeldState.RELEASING) {
                    return underTheLock(held, lease -> transitions.finish(path, from, operation.event()));
                }
                // one save: a failure keeps the state and the data, and the manager rejects again
                return transitions.finish(path, from, operation.event());
            default:
                return underTheLock(held, lease -> release.get().release(path,
                        transitions.move(path, from, operation.event()), lease::kept));
        }
    }

    /** What an operation does while it holds the lock of the run of the site. */
    @FunctionalInterface
    private interface UnderTheLock {
        HeldState apply(LeaseKeeper lease) throws QuarantineStoreException;
    }

    /**
     * Runs a release, a resume, or a reject of a submission in {@code releasing}, under a lease of the lock of the run
     * of the site, of the length of the run's {@code lockLeaseSeconds}: so none of them runs beside a release under way
     * on another server. The release checks the lease before its actions, and extends it as a pass does.
     */
    private HeldState underTheLock(HeldRecord held, UnderTheLock operation)
            throws QuarantineStoreException, OperationRefusedException {
        Duration length = leaseLength.get();
        Optional<RunLock.Lease> taken = runLock.take(siteOf(held), length);
        if (taken.isEmpty()) {
            throw new OperationRefusedException(OperationRefusedException.Reason.CONFLICT, RUN_IN_PROGRESS);
        }
        try {
            return operation.apply(new LeaseKeeper(taken.get(), length, clock));
        } finally {
            taken.get().release();
        }
    }

    private static String siteOf(HeldRecord held) {
        String fromPath = siteKeyOf(held.path());
        return fromPath != null ? fromPath : held.siteKey();
    }
}
