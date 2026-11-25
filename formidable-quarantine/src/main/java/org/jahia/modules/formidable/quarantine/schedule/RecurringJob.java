package org.jahia.modules.formidable.quarantine.schedule;

import org.jahia.registries.ServicesRegistry;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.settings.SettingsBean;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One recurring job of the module on Jahia's RAM scheduler, a {@link QuarantineJob} that calls a task at a period.
 * The job is scheduled on the processing server only, and on no other server of a cluster. Each call of
 * {@link #schedule} replaces the job of the call before, so a new period applies at once; {@link #unschedule} removes
 * it when the module stops. The RAM scheduler keeps nothing across a restart: the module schedules the job again when
 * it starts.
 */
public final class RecurringJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringJob.class);
    /** The first call comes after one period, and never later than this after the job is scheduled. */
    static final Duration LONGEST_FIRST_DELAY = Duration.ofHours(1);
    /** The task of each job, by the name of the job: Quartz instantiates the job, which looks its task up here. */
    private static final Map<String, Runnable> TASKS = new ConcurrentHashMap<>();

    private final String name;
    private final String description;
    private final Supplier<Scheduler> scheduler;
    private final BooleanSupplier processingServer;
    private final Clock clock;

    /** A job on the RAM scheduler of Jahia, on the processing server of the settings of Jahia. */
    public static RecurringJob onJahia(String name, String description) {
        return new RecurringJob(name, description,
                () -> ServicesRegistry.getInstance().getSchedulerService().getRAMScheduler(),
                () -> SettingsBean.getInstance().isProcessingServer(), Clock.systemUTC());
    }

    RecurringJob(String name, String description, Supplier<Scheduler> scheduler, BooleanSupplier processingServer,
                 Clock clock) {
        this.name = name;
        this.description = description;
        this.scheduler = scheduler;
        this.processingServer = processingServer;
        this.clock = clock;
    }

    /** The task of the job of this name, while the job is scheduled. */
    static Optional<Runnable> task(String jobName) {
        return Optional.ofNullable(TASKS.get(jobName));
    }

    /** One call of the job of this name, by the scheduler: its task, while the job is scheduled. */
    static void call(String jobName) {
        task(jobName).ifPresentOrElse(Runnable::run,
                () -> log.info("The job {} has no task any more: the module stopped it", jobName));
    }

    /**
     * The group of the jobs of the module, as Jahia names a group after the class of its jobs
     * ({@code BackgroundJob.getGroupName}).
     */
    static String group() {
        return QuarantineJob.class.getSimpleName();
    }

    /**
     * Schedules the task at this period, in place of the job scheduled before. Nothing is scheduled on a server that
     * is not the processing server, or for a period that is not positive: the task then never runs on its own.
     *
     * @return true when the job is scheduled
     */
    public boolean schedule(Duration period, Runnable task) {
        unschedule();
        if (!processingServer.getAsBoolean()) {
            log.info("The job {} runs on the processing server only: this server does not schedule it", name);
            return false;
        }
        if (period.isZero() || period.isNegative()) {
            log.info("The job {} is off: its period is {} seconds", name, period.toSeconds());
            return false;
        }
        Duration firstDelay = period.compareTo(LONGEST_FIRST_DELAY) < 0 ? period : LONGEST_FIRST_DELAY;
        JobDetail detail = new JobDetail(name, group(), QuarantineJob.class);
        detail.setDescription(description);
        JobDataMap data = new JobDataMap();
        data.put(BackgroundJob.JOB_CREATED, new Date(clock.millis()));
        data.put(BackgroundJob.JOB_STATUS, BackgroundJob.STATUS_ADDED);
        detail.setJobDataMap(data);
        SimpleTrigger trigger = new SimpleTrigger(name, group(), new Date(clock.instant().plus(firstDelay).toEpochMilli()),
                null, SimpleTrigger.REPEAT_INDEFINITELY, period.toMillis());
        // a call that the scheduler could not make in time is dropped: the next one comes at its period
        trigger.setMisfireInstruction(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        TASKS.put(name, task);
        try {
            scheduler.get().scheduleJob(detail, trigger);
        } catch (SchedulerException | RuntimeException e) {
            TASKS.remove(name);
            log.error("The job {} could not be scheduled: {}", name, e.toString());
            return false;
        }
        log.info("The job {} is scheduled every {} seconds", name, period.toSeconds());
        return true;
    }

    /** Removes the job, if it is scheduled. A call under way ends on its own. */
    public void unschedule() {
        TASKS.remove(name);
        try {
            Scheduler current = scheduler.get();
            if (current != null) {
                current.deleteJob(name, group());
            }
        } catch (SchedulerException | RuntimeException e) {
            log.warn("The job {} could not be removed from the scheduler: {}", name, e.toString());
        }
    }
}
