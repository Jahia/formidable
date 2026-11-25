package org.jahia.modules.formidable.quarantine.schedule;

import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.ops.RetentionConfig;
import org.jahia.modules.formidable.quarantine.store.QuarantineSites;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import java.time.Duration;

/**
 * The scheduled purge, an OSGi component: on the processing server, every {@code periodSeconds} of the configuration
 * of the retention, the retention of each site that has a quarantine tree, with the limit of the site
 * ({@link QuarantineRetention#retentionDaysOf}). A site that fails is logged, and the call goes on with the next site.
 */
@Component(service = PurgeSchedule.class, configurationPid = QuarantineRetention.PID, immediate = true)
public class PurgeSchedule {

    static final String JOB_NAME = "formidable-quarantine-purge";

    private final RecurringJob job;
    private QuarantineRetention retention;
    private QuarantineSites sites;

    public PurgeSchedule() {
        this(RecurringJob.onJahia(JOB_NAME, "The scheduled purge of the finished held submissions of the quarantine"));
    }

    PurgeSchedule(RecurringJob job) {
        this.job = job;
    }

    @Reference
    public void setRetention(QuarantineRetention retention) {
        this.retention = retention;
    }

    @Reference
    public void setSites(QuarantineSites sites) {
        this.sites = sites;
    }

    @Activate
    @Modified
    public void configure(RetentionConfig config) {
        job.schedule(Duration.ofSeconds(config.periodSeconds()), this::purgeEverySite);
    }

    @Deactivate
    public void deactivate() {
        job.unschedule();
    }

    /** One call of the job: the retention of each site that has a quarantine tree. */
    void purgeEverySite() {
        EverySite.apply(sites, "scheduled purge", siteKey -> retention.purge(siteKey, null));
    }
}
