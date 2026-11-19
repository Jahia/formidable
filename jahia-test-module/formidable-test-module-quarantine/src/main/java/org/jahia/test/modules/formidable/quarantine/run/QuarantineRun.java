package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.test.modules.formidable.quarantine.check.Qualifier;
import org.jahia.test.modules.formidable.quarantine.state.HeldState;
import org.jahia.test.modules.formidable.quarantine.store.CheckStore;
import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldStates;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The run of the checks over the due held submissions of a site, an OSGi service. It has no thread and no scheduler
 * of its own: a spec starts it through the "run now" endpoint. One run makes one pass over each due submission
 * ({@link SubmissionRun}), each under the system sessions of the stores. A submission that fails is reported, and
 * the pass goes on with the next one.
 *
 * <p>A submission is due when its state is {@code held}, or {@code checking} with no next attempt or a next attempt
 * that is not in the future. A run that ignores the schedule takes every submission in one of these two states.</p>
 */
@Component(service = QuarantineRun.class, configurationPid = QuarantineRun.PID, immediate = true)
@Designate(ocd = RunConfig.class)
public class QuarantineRun {

    private static final Logger log = LoggerFactory.getLogger(QuarantineRun.class);

    /** The PID of the configuration of the run. */
    public static final String PID = "org.jahia.test.modules.formidable.quarantine.run";

    private final CheckList checkList;
    private final Clock clock;
    private final Map<String, Qualifier> qualifiers = new ConcurrentHashMap<>();
    private volatile RunSettings settings = RunSettings.DEFAULT;
    private QuarantineStore store;
    private CheckStore checkStore;
    private DeferredActions actions;

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
        settings = RunSettings.of(config);
        log.info("Run of the quarantine test module configured: allowInProcessFirst={}, maxAttempts={}",
                settings.allowInProcessFirst(), settings.backoff().maxAttempts());
    }

    /** @return the settings the next run applies */
    public RunSettings settings() {
        return settings;
    }

    /**
     * The release of the module, for an operation that releases or resumes a submission by hand: call it with the
     * state {@code releasing}, once the state machine moved the submission there.
     */
    public Release release() {
        return new Release(store, checkStore, actions, new Transitions(store), clock);
    }

    /**
     * Makes one pass over the due held submissions of the site. One run at a time.
     *
     * @param siteKey        the key of the site
     * @param formId         the identifier of the one form to run, or {@code null} for every form of the site
     * @param ignoreSchedule true to take every submission in state {@code held} or {@code checking}, whatever its next
     *                       attempt says
     * @return the submissions processed, with the state of each after the run
     */
    public synchronized RunReport run(String siteKey, String formId, boolean ignoreSchedule)
            throws QuarantineStoreException {
        Instant now = clock.instant();
        RunSettings current = settings;
        SubmissionRun submissionRun = new SubmissionRun(store, checkStore, checkList, qualifiers::get, release(),
                current, clock);
        List<String> forms = formId == null ? checkStore.forms(siteKey) : List.of(formId);
        List<RunReport.Processed> processed = new ArrayList<>();
        for (String form : forms) {
            for (HeldRecord record : store.list(siteKey, form)) {
                if (isDue(record, now, ignoreSchedule)) {
                    processed.add(process(submissionRun, record));
                }
            }
        }
        log.info("Run of the quarantine of site {}: {} held submissions processed", siteKey, processed.size());
        return new RunReport(siteKey, formId, processed);
    }

    static boolean isDue(HeldRecord record, Instant now, boolean ignoreSchedule) {
        if (!HeldStates.HELD.equals(record.state()) && !HeldStates.CHECKING.equals(record.state())) {
            return false;
        }
        return ignoreSchedule || record.nextAttempt() == null || !record.nextAttempt().isAfter(now);
    }

    private RunReport.Processed process(SubmissionRun submissionRun, HeldRecord record) {
        try {
            HeldState state = submissionRun.run(record);
            return new RunReport.Processed(record.path(), record.formId(), record.state(), state.value(), null);
        } catch (QuarantineStoreException | RepositoryException | RuntimeException e) {
            log.warn("Held submission {}: the run failed, the pass goes on: {}", record.path(), e.toString());
            return new RunReport.Processed(record.path(), record.formId(), record.state(), stateOf(record.path()),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String stateOf(String path) {
        try {
            return store.record(path).map(HeldRecord::state).orElse(null);
        } catch (QuarantineStoreException e) {
            return null;
        }
    }
}
