package org.jahia.modules.formidable.quarantine.schedule;

import org.jahia.modules.formidable.quarantine.run.QuarantineRun;
import org.jahia.modules.formidable.quarantine.run.RunConfig;
import org.jahia.modules.formidable.quarantine.store.QuarantineSites;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import java.time.Clock;
import java.time.Duration;

/**
 * The scheduled run of the checks, an OSGi component: on the processing server, every {@code periodSeconds} of the
 * configuration of the run, one pass over the due held submissions of each site that has a quarantine tree. A site
 * whose lock another server holds is skipped until the next call. A site that fails is logged, and the call goes on
 * with the next site. A change of the configuration schedules the job again at the new period.
 */
@Component(service = RunSchedule.class, configurationPid = QuarantineRun.PID, immediate = true)
public class RunSchedule {

    static final String JOB_NAME = "formidable-quarantine-run";

    private final RecurringJob job;
    private final Clock clock;
    private QuarantineRun run;
    private QuarantineSites sites;

    public RunSchedule() {
        this(RecurringJob.onJahia(JOB_NAME, "The scheduled run of the checks of the quarantine"));
    }

    RunSchedule(RecurringJob job) {
        this(job, Clock.systemUTC());
    }

    RunSchedule(RecurringJob job, Clock clock) {
        this.job = job;
        this.clock = clock;
    }

    @Reference
    public void setRun(QuarantineRun run) {
        this.run = run;
    }

    @Reference
    public void setSites(QuarantineSites sites) {
        this.sites = sites;
    }

    @Activate
    @Modified
    public void configure(RunConfig config) {
        job.schedule(Duration.ofSeconds(config.periodSeconds()), this::runEverySite);
    }

    @Deactivate
    public void deactivate() {
        job.unschedule();
    }

    /** One call of the job: one pass over each site that has a quarantine tree, on schedule. */
    void runEverySite() {
        EverySite.apply(sites, "scheduled run", this::runIfDue);
    }

    /** A site with no due submission is left alone: no lock is taken, nothing is written. */
    private void runIfDue(String siteKey) throws QuarantineStoreException {
        if (sites.anyDue(siteKey, clock.instant())) {
            run.tryRun(siteKey, null, false);
        }
    }
}
