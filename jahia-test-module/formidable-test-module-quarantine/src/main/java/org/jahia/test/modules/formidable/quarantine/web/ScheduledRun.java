package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.registries.ServicesRegistry;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The scheduled run of formidable-quarantine as Jahia's RAM scheduler holds it on this server, so that a spec that
 * edits its period waits until the module applied the edit. The names are those that the module gives its job
 * ({@code RunSchedule.JOB_NAME}, and the group Jahia names after {@code QuarantineJob}): the module exports no API for
 * its schedule.
 */
final class ScheduledRun {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRun.class);
    static final String JOB_NAME = "formidable-quarantine-run";
    static final String JOB_GROUP = "QuarantineJob";

    private ScheduledRun() {
    }

    /** The answer when the scheduler cannot be read: no period, so a spec never takes it for "off". */
    static final long UNREAD = -1;

    /**
     * @return the period of the scheduled run, in seconds; 0 when the scheduler holds no trigger of the job on this
     *         server; {@link #UNREAD} when the scheduler cannot be read
     */
    static long periodSeconds() {
        try {
            Trigger[] triggers = ServicesRegistry.getInstance().getSchedulerService().getRAMScheduler()
                    .getTriggersOfJob(JOB_NAME, JOB_GROUP);
            for (Trigger trigger : triggers) {
                if (trigger instanceof SimpleTrigger simple) {
                    return simple.getRepeatInterval() / 1000;
                }
            }
        } catch (SchedulerException | RuntimeException e) {
            log.warn("The schedule of the run of the quarantine cannot be read: {}", e.toString());
            return UNREAD;
        }
        return 0;
    }
}
