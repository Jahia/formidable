package org.jahia.modules.formidable.quarantine.schedule;

import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobExecutionContext;

/**
 * A recurring job of the module, run by Jahia's RAM scheduler on the processing server: the scheduled run of the
 * checks, or the scheduled purge. Quartz instantiates it, so it calls the task of its {@link RecurringJob} by the name
 * of the job. Jahia's scheduler calls it once at a time: a call that lasts longer than the period delays the next.
 */
public class QuarantineJob extends BackgroundJob {

    @Override
    public void executeJahiaJob(JobExecutionContext context) {
        RecurringJob.call(context.getJobDetail().getName());
    }
}
