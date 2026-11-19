package org.jahia.test.modules.formidable.quarantine.ops;

import org.jahia.test.modules.formidable.quarantine.run.QuarantineRun;
import org.jahia.test.modules.formidable.quarantine.run.Release;
import org.jahia.test.modules.formidable.quarantine.run.Transitions;
import org.jahia.test.modules.formidable.quarantine.state.HeldState;
import org.jahia.test.modules.formidable.quarantine.state.StateMachine;
import org.jahia.test.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.test.modules.formidable.quarantine.store.CheckStore;
import org.jahia.test.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The operations of a manager on a held submission in state {@code needsReview}: retry, release, resume and reject, an
 * OSGi service. Each operation checks, in this order, that a held submission is at the path, that the user of the
 * request is a manager of its form ({@link Managers}), and that the state machine and the decision allow it. A refused
 * operation changes nothing. An operation that ran writes one audit record: the operation, the name of the user, the
 * state before and after, and when. No value.
 *
 * <ul>
 * <li>retry: the attempts start over, every outcome that is not a {@code PASS} is dropped, and the state moves to
 * {@code checking}: the next run calls those checks again;</li>
 * <li>release: the state moves to {@code releasing}, and the release of the module runs, from the hash check on. It is
 * refused when an action after release failed: that is a resume;</li>
 * <li>resume: the same, allowed only when an action after release failed ({@code ACTION_FAILED}). The release starts at
 * the index {@code completedActions}, so the actions that ran do not run again;</li>
 * <li>reject: the state moves to {@code rejected}, and the values, the sensitive values and the bytes are deleted.
 * The outcomes and the decision stay.</li>
 * </ul>
 */
@Component(service = QuarantineOperations.class, immediate = true)
public class QuarantineOperations {

    private static final Logger log = LoggerFactory.getLogger(QuarantineOperations.class);

    private final Managers managers;
    private final Clock clock;
    private QuarantineStore store;
    private CheckStore checkStore;
    private Supplier<Release> release;

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
    }

    /** The release the operations call, for a test that builds it from the stores in memory. */
    void setRelease(Supplier<Release> release) {
        this.release = release;
    }

    /**
     * Runs the operation on the held submission, for the user of the current request. One operation at a time.
     *
     * @throws OperationRefusedException when the user is not a manager of the form, when no held submission is at the
     *                                   path, or when the state does not allow the operation: nothing changed
     */
    public synchronized OperationResult apply(String path, Operation operation)
            throws OperationRefusedException, QuarantineStoreException {
        Optional<HeldRecord> found = store.record(path);
        if (found.isEmpty()) {
            // only a server administrator learns that a path names nothing
            throw new OperationRefusedException(managers.isServerAdministrator()
                    ? OperationRefusedException.Reason.NOT_FOUND : OperationRefusedException.Reason.FORBIDDEN,
                    "No held submission that the user manages at " + path);
        }
        HeldRecord record = found.get();
        if (!managers.mayManage(record.formId())) {
            log.info("Held submission {}: {} refused to {}, who does not manage form {}", path, operation.value(),
                    managers.currentUserName(), record.formId());
            throw new OperationRefusedException(OperationRefusedException.Reason.FORBIDDEN,
                    "The user does not manage the quarantine of the form");
        }
        HeldState from = HeldState.fromValue(record.state());
        if (!StateMachine.allows(from, operation.event()) || !fitsTheDecision(path, operation)) {
            throw new OperationRefusedException(OperationRefusedException.Reason.CONFLICT,
                    "The operation " + operation.value() + " is not allowed in state " + from.value());
        }
        HeldState to = run(path, operation, from);
        String user = managers.currentUserName();
        checkStore.writeAudit(path, new AuditRecord(operation.value(), user, from.value(), to.value(), clock.instant()));
        log.info("Held submission {}: {} by {}, from {} to {}", path, operation.value(), user, from.value(), to.value());
        return new OperationResult(path, operation, from, to);
    }

    /** A resume needs an action after release that failed; a release needs any other reason of review. */
    private boolean fitsTheDecision(String path, Operation operation) throws QuarantineStoreException {
        if (operation != Operation.RESUME && operation != Operation.RELEASE) {
            return true;
        }
        boolean actionFailed = checkStore.decision(path)
                .map(decision -> DecisionRecord.ACTION_FAILED.equals(decision.reason()))
                .orElse(false);
        return operation == Operation.RESUME ? actionFailed : !actionFailed;
    }

    private HeldState run(String path, Operation operation, HeldState from) throws QuarantineStoreException {
        Transitions transitions = new Transitions(store);
        switch (operation) {
            case RETRY:
                store.setAttempts(path, 0, null);
                checkStore.keepPassedOutcomesOnly(path);
                return transitions.move(path, from, operation.event());
            case REJECT:
                HeldState rejected = transitions.move(path, from, operation.event());
                store.finish(path, rejected.value());
                return rejected;
            default:
                HeldState releasing = transitions.move(path, from, operation.event());
                return release.get().release(path, releasing);
        }
    }
}
