package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunException;
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
 * <p>One run at a time: the run is {@code synchronized} in the JVM, and takes the lock of the run of the site in the
 * repository ({@link RunLock}) before it reads, across the cluster. It extends the lease of the lock while it works,
 * once half of the lease has gone by.</p>
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

    private final CheckList checkList;
    private final Clock clock;
    private final Map<String, Qualifier> qualifiers = new ConcurrentHashMap<>();
    private final AtomicReference<RunSettings> settings = new AtomicReference<>(RunSettings.DEFAULT);
    private final AtomicReference<Duration> lease = new AtomicReference<>(DEFAULT_LEASE);
    private QuarantineStore store;
    private CheckStore checkStore;
    private DeferredActions actions;
    private RunLock runLock;

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
     * Makes one pass over the due held submissions of the site, as {@link #tryRun} does, and fails when another server
     * holds the lock of the run of the site.
     *
     * @throws QuarantineStoreException when the tree cannot be read, or another server runs the site
     */
    public synchronized RunReport run(String siteKey, String formId, boolean ignoreSchedule)
            throws QuarantineStoreException {
        Optional<RunReport> report = tryRun(siteKey, formId, ignoreSchedule);
        if (report.isEmpty()) {
            throw new QuarantineStoreException("Another server runs the quarantine of site " + siteKey
                    + ": its lock is held");
        }
        return report.get();
    }

    /**
     * Makes one pass over the due held submissions of the site. One run at a time: in this JVM, under the lock of this
     * run, which the operations of a manager take too, so no operation runs while the run releases a submission; and
     * across the cluster, under the lock of the run of the site in the repository, taken before the run reads.
     *
     * @param siteKey        the key of the site
     * @param formId         the identifier of the one form to run, or {@code null} for every form of the site
     * @param ignoreSchedule true to take every submission in state {@code held} or {@code checking}, whatever its next
     *                       attempt says
     * @return the submissions processed, with the state of each after the run; empty when another server holds the
     *         lock of the run of the site, and nothing was read
     */
    public synchronized Optional<RunReport> tryRun(String siteKey, String formId, boolean ignoreSchedule)
            throws QuarantineStoreException {
        Duration leaseLength = lease.get();
        if (!runLock.take(siteKey, leaseLength)) {
            log.info("Run of the quarantine of site {} skipped: another server holds its lock", siteKey);
            return Optional.empty();
        }
        try {
            return Optional.of(pass(siteKey, formId, ignoreSchedule, new HeldLease(siteKey, leaseLength)));
        } finally {
            runLock.release(siteKey);
        }
    }

    private RunReport pass(String siteKey, String formId, boolean ignoreSchedule, HeldLease runLease)
            throws QuarantineStoreException {
        Instant now = clock.instant();
        RunSettings current = settings.get();
        SubmissionRun submissionRun = new SubmissionRun(store, checkStore, checkList, qualifiers::get, release(),
                current, clock);
        List<String> forms = formId == null ? checkStore.forms(siteKey) : List.of(formId);
        List<RunReport.Processed> processed = new ArrayList<>();
        for (String form : forms) {
            for (HeldRecord heldRecord : store.list(siteKey, form)) {
                if (!runLease.kept()) {
                    log.warn("Run of the quarantine of site {} stopped: its lock went to another server", siteKey);
                    return new RunReport(siteKey, formId, processed);
                }
                if (isDue(heldRecord, now, ignoreSchedule)) {
                    processed.add(process(submissionRun, heldRecord));
                }
            }
        }
        log.info("Run of the quarantine of site {}: {} held submissions processed", siteKey, processed.size());
        return new RunReport(siteKey, formId, processed);
    }

    /** The lease of the lock of the run, extended once half of it has gone by. */
    private final class HeldLease {
        private final String siteKey;
        private final Duration length;
        private Instant extendAt;

        HeldLease(String siteKey, Duration length) {
            this.siteKey = siteKey;
            this.length = length;
            this.extendAt = clock.instant().plus(length.dividedBy(2));
        }

        /** @return false when the lease cannot be extended: another server took the lock, or the repository failed */
        boolean kept() throws QuarantineStoreException {
            Instant now = clock.instant();
            if (now.isBefore(extendAt)) {
                return true;
            }
            extendAt = now.plus(length.dividedBy(2));
            return runLock.take(siteKey, length);
        }
    }

    static boolean isDue(HeldRecord held, Instant now, boolean ignoreSchedule) {
        if (!HeldStates.HELD.equals(held.state()) && !HeldStates.CHECKING.equals(held.state())) {
            return false;
        }
        return ignoreSchedule || held.nextAttempt() == null || !held.nextAttempt().isAfter(now);
    }

    private RunReport.Processed process(SubmissionRun submissionRun, HeldRecord held) {
        try {
            HeldState state = submissionRun.run(held);
            auditIfStopped(held, state);
            return new RunReport.Processed(held.path(), held.formId(), held.state(), state.value(), null);
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
