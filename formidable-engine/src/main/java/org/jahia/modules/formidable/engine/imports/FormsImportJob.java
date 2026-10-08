package org.jahia.modules.formidable.engine.imports;

import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

/**
 * One phase of an import, run by Jahia's persistent scheduler on the processing server: the dry run of
 * an upload, or the import of a reviewed one. Quartz instantiates it, so it reaches the active
 * {@link ImportJobs} through its static accessor and hands it the keys of the job.
 */
public class FormsImportJob extends BackgroundJob {

    @Override
    public void executeJahiaJob(JobExecutionContext context) {
        JobDataMap data = context.getJobDetail().getJobDataMap();
        ImportJobs.current().run(data.getString(ImportJobs.DATA_SITE), data.getString(ImportJobs.DATA_JOB),
                ImportJobs.Phase.valueOf(data.getString(ImportJobs.DATA_PHASE)));
    }
}
