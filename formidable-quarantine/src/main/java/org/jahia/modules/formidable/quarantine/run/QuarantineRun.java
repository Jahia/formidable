package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunException;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunInProgressException;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunStoppedException;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunner;
import org.jahia.modules.formidable.quarantine.api.RunReport;
import org.jahia.modules.formidable.quarantine.api.RunnerSettings;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.HeldStates;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.formidable.quarantine.store.RunLock;
import org.jahia.modules.formidable.quarantine.store.StaleStateException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The run of the checks over the due held submissions of a site, an OSGi service. The scheduled run of the module
 * starts it on the processing server ({@code schedule.RunSchedule}), and "run now" starts it through
 * {@link QuarantineRunner}. One run makes one pass over each due submission ({@link SubmissionRun}), each under the
 * system sessions of the stores. A submission that fails is reported, and the pass goes on with the next one.
 *
 * <p>One pass at a time on a site: a pass takes the lock of its site in this JVM ({@link SiteLocks}), which the
 * operations of a manager take too, then the lock of the run of the site in the repository ({@link RunLock}) before
 * it reads, across the cluster. Before each submission and before each call of a check or of the actions after
 * release, it checks that its lease is still valid, and extends the lease once half of it has gone by; a pass that
 * lost its lease stops before the call. The locks narrow the window; the guard is the compare-and-set of each state
 * move ({@link Transitions}): a submission moved by another run or an operation is skipped.</p>
 *
 * <p>A submission is due when its state is {@code held}, or {@code checking} with no next attempt or a next attempt
 * that is not in the future. A run that ignores the schedule takes every submission in one of these two states.</p>
 */
@Component(service = {QuarantineRun.class, QuarantineRunner.class}, configurationPid = QuarantineRun.PID,
        immediate = true)
@Designate(ocd = RunConfig.class)
public class QuarantineRun implements QuarantineRunner {

    private static final Logger log = LoggerFactory.getLogger(QuarantineRun.class);

    /** The PID of the configuration of the run. */
    public static final String PID = "org.jahia.modules.formidable.quarantine.run";
    /** How long the lock of the run of a site stays held after a server stopped without releasing it. */
    static final Duration DEFAULT_LEASE = Duration.ofMinutes(10);
    /** The error of a submission that another run or an operation moved under the pass. */
    static final String MOVED = "moved by another run or operation";

    private final CheckList checkList;
    private final Clock clock;
    private final Map<String, Qualifier> qualifiers = new ConcurrentHashMap<>();
    private final AtomicReference<RunSettings> settings = new AtomicReference<>(RunSettings.DEFAULT);
    private final AtomicReference<Duration> lease = new AtomicReference<>(DEFAULT_LEASE);
    private QuarantineStore store;
    private CheckStore checkStore;
    private DeferredActions actions;
    private RunLock runLock;
    private SiteLocks siteLocks = new SiteLocks();

    public QuarantineRun() {
        this(new JcrCheckList(), Clock.systemUTC());
    }

    QuarantineRun(CheckList checkList, Clock clock) {
        this.checkList = checkList;
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
    public void setActions(DeferredActions actions) {
        this.actions = actions;
    }

    @Reference
    public void setRunLock(RunLock runLock) {
        this.runLock = runLock;
    }

    /** The locks per site that the operations share; a test that sets none gets locks of its own. */
    @Reference
    public void setSiteLocks(SiteLocks siteLocks) {
        this.siteLocks = siteLocks;
    }

    /** Every qualifier of the platform, matched to a check node by its node type. */
    @Reference(service = Qualifier.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC,
            unbind = "removeQualifier")
    public void addQualifier(Qualifier qualifier) {
        qualifiers.put(qualifier.getNodeType(), qualifier);
    }

    public void removeQualifier(Qualifier qualifier) {
        qualifiers.remove(qualifier.getNodeType(), qualifier);
    }

    @Activate
    @Modified
    public void configure(RunConfig config) {
        RunSettings configured = RunSettings.of(config);
        settings.set(configured);
        if (config.lockLeaseSeconds() > 0) {
            lease.set(Duration.ofSeconds(config.lockLeaseSeconds()));
        } else {
            log.warn("lockLeaseSeconds={} is not positive: the lease of the lock of the run is {}",
                    config.lockLeaseSeconds(), DEFAULT_LEASE);
            lease.set(DEFAULT_LEASE);
        }
        log.info("Run of the quarantine configured: allowInProcessFirst={}, maxAttempts={}, lease={}",
                configured.allowInProcessFirst(), configured.backoff().maxAttempts(), lease.get());
    }

    /** @return how long the lock of the run of a site is leased, from {@code lockLeaseSeconds} */
    public Duration leaseLength() {
        return lease.get();
    }

    /** @return the settings the next run applies */
    public RunSettings settings() {
        return settings.get();
    }

    @Override
    public RunnerSettings runnerSettings() {
        RunSettings current = settings.get();
        return new RunnerSettings(current.allowInProcessFirst(), current.backoff().maxAttempts(),
                current.backoff().delay(1).toSeconds(), current.backoff().delay(Integer.MAX_VALUE).toSeconds());
    }

    @Override
    public RunReport runNow(String siteKey, String formId, boolean ignoreSchedule) throws QuarantineRunException {
        try {
            return run(siteKey, formId, ignoreSchedule);
        } catch (RunLockedException e) {
            throw new QuarantineRunInProgressException(e.getMessage(), e);
        } catch (PassStoppedException e) {
            throw new QuarantineRunStoppedException(e.getMessage(), e.report(), e);
        } catch (QuarantineStoreException e) {
            throw new QuarantineRunException(e.getMessage(), e);
        }
    }

    /**
     * The release of the module, for an operation that releases or resumes a submission by hand: call it with the
     * state {@code releasing}, once the state machine moved the submission there.
     */
    public Release release() {
        return new Release(store, checkStore, actions, new Transitions(store), clock);
    }

    /**
     * Makes one pass over the due held submissions of the site, as {@link #tryRun} does, and fails when another run
     * holds the lock of the run of the site.
     *
     * @throws RunLockedException       when another run holds the lock of the site: nothing was read
     * @throws QuarantineStoreException when the tree cannot be read
     */
    public RunReport run(String siteKey, String formId, boolean ignoreSchedule) throws QuarantineStoreException {
        Optional<RunReport> report = tryRun(siteKey, formId, ignoreSchedule);
        if (report.isEmpty()) {
            throw new RunLockedException("A run of the quarantine of site " + siteKey + " is in progress");
        }
        return report.get();
    }

    /**
     * Makes one pass over the due held submissions of the site. One pass at a time on a site: in this JVM, under the
     * lock of the site, which the operations of a manager take too; and across the cluster, under the lock of the run
     * of the site in the repository, taken before the run reads.
     *
     * @param siteKey        the key of the site
     * @param formId         the identifier of the one form to run, or {@code null} for every form of the site
     * @param ignoreSchedule true to take every submission in state {@code held} or {@code checking}, whatever its next
     *                       attempt says
     * @return the submissions processed, with the state of each after the run; empty when another run holds the lock
     *         of the site, in this JVM for longer than {@link SiteLocks#OPERATION_WAIT} or in the repository, and
     *         nothing was read
     * @throws PassStoppedException when the pass lost the lock of the run of its site and stopped: it carries the
     *                              report of the submissions processed before the stop
     */
    public Optional<RunReport> tryRun(String siteKey, String formId, boolean ignoreSchedule)
            throws QuarantineStoreException {
        if (!siteLocks.tryLock(siteKey, SiteLocks.OPERATION_WAIT)) {
            log.info("Run of the quarantine of site {} skipped: a run or an operation of this server holds it",
                    siteKey);
            return Optional.empty();
        }
        try {
            Duration leaseLength = lease.get();
            Optional<RunLock.Lease> taken = runLock.take(siteKey, leaseLength);
            if (taken.isEmpty()) {
                log.info("Run of the quarantine of site {} skipped: another run holds its lock", siteKey);
                return Optional.empty();
            }
            try {
                return Optional.of(pass(siteKey, formId, ignoreSchedule,
                        new LeaseKeeper(taken.get(), leaseLength, clock)));
            } finally {
                taken.get().release();
            }
        } finally {
            siteLocks.of(siteKey).unlock();
        }
    }

    private RunReport pass(String siteKey, String formId, boolean ignoreSchedule, LeaseKeeper runLease)
            throws QuarantineStoreException {
        Instant now = clock.instant();
        RunSettings current = settings.get();
        SubmissionRun submissionRun = new SubmissionRun(store, checkStore, checkList, qualifiers::get, release(),
                current, clock, runLease::kept);
        List<String> forms = formId == null ? checkStore.forms(siteKey) : List.of(formId);
        List<RunReport.Processed> processed = new ArrayList<>();
        try {
            for (String form : forms) {
                for (HeldRecord heldRecord : store.list(siteKey, form)) {
                    runLease.keptOrStop();
                    if (isDue(heldRecord, now, ignoreSchedule)) {
                        processed.add(process(submissionRun, heldRecord));
                    }
                }
            }
        } catch (RunStoppedException e) {
            log.warn("Run of the quarantine of site {} stopped after {} held submissions: {}", siteKey,
                    processed.size(), e.getMessage());
            throw new PassStoppedException(e.getMessage(), new RunReport(siteKey, formId, processed));
        }
        log.info("Run of the quarantine of site {}: {} held submissions processed", siteKey, processed.size());
        return new RunReport(siteKey, formId, processed);
    }

    static boolean isDue(HeldRecord held, Instant now, boolean ignoreSchedule) {
        if (!HeldStates.HELD.equals(held.state()) && !HeldStates.CHECKING.equals(held.state())) {
            return false;
        }
        return ignoreSchedule || held.nextAttempt() == null || !held.nextAttempt().isAfter(now);
    }

    private RunReport.Processed process(SubmissionRun submissionRun, HeldRecord held) throws RunStoppedException {
        try {
            HeldState state = submissionRun.run(held);
            auditIfStopped(held, state);
            return new RunReport.Processed(held.path(), held.formId(), held.state(), state.value(), null);
        } catch (RunStoppedException e) {
            throw e;
        } catch (StaleStateException e) {
            // another run or an operation moved it first: it is theirs, and this pass leaves it alone
            log.info("Held submission {}: moved by another run or operation, the pass skips it", held.path());
            return new RunReport.Processed(held.path(), held.formId(), held.state(), stateOf(held.path()), MOVED);
        } catch (QuarantineStoreException | RepositoryException e) {
            log.warn("Held submission {}: the run failed, the pass goes on: {}", held.path(), e.toString());
            return failed(held, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            // the class alone: the message of an unexpected failure, such as one of an action, may quote a held value
            log.warn("Held submission {}: the run failed with {}, the pass goes on", held.path(),
                    e.getClass().getName());
            return failed(held, e.getClass().getSimpleName());
        }
    }

    private RunReport.Processed failed(HeldRecord held, String error) {
        String reached = stateOf(held.path());
        if (HeldStates.RELEASING.equals(reached)) {
            // a release that stopped half way: a manager resumes or rejects it, and the audit says how it got there
            writeRunAudit(held, reached);
        }
        return new RunReport.Processed(held.path(), held.formId(), held.state(), reached, error);
    }

    /**
     * One audit record when the run released, rejected or sent the submission to review, with no user: the run moved
     * the state, not a manager. A submission that keeps checking gets none. A failure to write it is logged: the state
     * already moved.
     */
    private void auditIfStopped(HeldRecord held, HeldState state) {
        if (state == HeldState.COMPLETED || state == HeldState.REJECTED || state == HeldState.NEEDS_REVIEW) {
            writeRunAudit(held, state.value());
        }
    }

    private void writeRunAudit(HeldRecord held, String reached) {
        try {
            checkStore.writeAudit(held.path(), new AuditRecord(AuditRecord.RUN, null, held.state(), reached,
                    clock.instant()));
        } catch (QuarantineStoreException e) {
            log.warn("Held submission {}: the audit record of the run could not be written: {}", held.path(),
                    e.getMessage());
        } catch (RuntimeException e) {
            // the state already moved: the run reports the submission as it is, and logs the class alone
            log.warn("Held submission {}: the audit record of the run could not be written: {}", held.path(),
                    e.getClass().getName());
        }
    }

    private String stateOf(String path) {
        try {
            return store.heldRecord(path).map(HeldRecord::state).orElse(null);
        } catch (QuarantineStoreException e) {
            return null;
        }
    }
}
