package org.jahia.modules.formidable.quarantine.schedule;

import org.jahia.modules.formidable.quarantine.Configs;
import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.ops.RetentionConfig;
import org.jahia.modules.formidable.quarantine.run.QuarantineRun;
import org.jahia.modules.formidable.quarantine.run.RunConfig;
import org.jahia.modules.formidable.quarantine.store.QuarantineSites;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scheduled run and the scheduled purge: each schedules its job at the period of its configuration, removes it
 * when it stops, and makes one call over each site that has a quarantine tree.
 */
class ScheduleTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private Scheduler scheduler;
    private QuarantineSites sites;
    private QuarantineRun run;
    private QuarantineRetention retention;
    private RunSchedule runSchedule;
    private PurgeSchedule purgeSchedule;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = mock(Scheduler.class);
        sites = mock(QuarantineSites.class);
        when(sites.withTree()).thenReturn(List.of("siteA", "siteB", "siteC"));
        when(sites.anyDue(anyString(), eq(NOW))).thenReturn(true);
        run = mock(QuarantineRun.class);
        when(run.tryRun(anyString(), isNull(), eq(false))).thenReturn(Optional.empty());
        retention = mock(QuarantineRetention.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        runSchedule = new RunSchedule(job(RunSchedule.JOB_NAME, clock), clock);
        runSchedule.setRun(run);
        runSchedule.setSites(sites);
        purgeSchedule = new PurgeSchedule(job(PurgeSchedule.JOB_NAME, clock));
        purgeSchedule.setRetention(retention);
        purgeSchedule.setSites(sites);
    }

    @AfterEach
    void tearDown() {
        runSchedule.deactivate();
        purgeSchedule.deactivate();
    }

    private RecurringJob job(String name, Clock clock) {
        return new RecurringJob(name, "a test job", () -> scheduler, () -> true, clock);
    }

    private long scheduledInterval() throws Exception {
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(any(JobDetail.class), trigger.capture());
        return ((SimpleTrigger) trigger.getValue()).getRepeatInterval();
    }

    @Test
    void theScheduledRunRunsEachSiteWithADueSubmissionAndGoesOnAfterAFailure() throws Exception {
        when(run.tryRun("siteA", null, false)).thenThrow(new QuarantineStoreException("unreadable"));
        when(run.tryRun("siteB", null, false)).thenThrow(new IllegalStateException("unexpected"));

        runSchedule.runEverySite();

        verify(run).tryRun("siteA", null, false);
        verify(run).tryRun("siteB", null, false);
        verify(run).tryRun("siteC", null, false);
    }

    @Test
    void aSiteWithNoDueSubmissionIsNotRunAndItsLockIsNotTaken() throws Exception {
        when(sites.anyDue("siteB", NOW)).thenReturn(false);

        runSchedule.runEverySite();

        verify(run).tryRun("siteA", null, false);
        verify(run, never()).tryRun("siteB", null, false);
        verify(run).tryRun("siteC", null, false);
    }

    @Test
    void theScheduledPurgeAppliesTheLimitOfEachSite() throws Exception {
        when(retention.purge("siteA", null)).thenThrow(new QuarantineStoreException("unreadable"));

        purgeSchedule.purgeEverySite();

        verify(retention).purge("siteA", null);
        verify(retention).purge("siteB", null);
        verify(retention).purge("siteC", null);
    }

    @Test
    void sitesThatCannotBeListedRunNothing() throws Exception {
        when(sites.withTree()).thenThrow(new QuarantineStoreException("unreadable"));

        runSchedule.runEverySite();
        purgeSchedule.purgeEverySite();

        verify(run, never()).tryRun(anyString(), isNull(), eq(false));
        verify(retention, never()).purge(anyString(), isNull());
    }

    @Test
    void theRunIsScheduledAtThePeriodOfTheConfigurationOfTheRun() throws Exception {
        runSchedule.configure(Configs.of(RunConfig.class, Map.of("periodSeconds", 5L)));

        assertEquals(5_000, scheduledInterval());
        assertTrue(RecurringJob.task(RunSchedule.JOB_NAME).isPresent());
    }

    @Test
    void thePurgeIsScheduledAtThePeriodOfTheConfigurationOfTheRetention() throws Exception {
        purgeSchedule.configure(Configs.of(RetentionConfig.class, Map.of("periodSeconds", 3600L)));

        assertEquals(3_600_000, scheduledInterval());
        assertTrue(RecurringJob.task(PurgeSchedule.JOB_NAME).isPresent());
    }

    @Test
    void aPeriodOfZeroSchedulesNeitherJob() throws Exception {
        runSchedule.configure(Configs.of(RunConfig.class, Map.of("periodSeconds", 0L)));
        purgeSchedule.configure(Configs.of(RetentionConfig.class, Map.of("periodSeconds", 0L)));

        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
        assertFalse(RecurringJob.task(RunSchedule.JOB_NAME).isPresent());
        assertFalse(RecurringJob.task(PurgeSchedule.JOB_NAME).isPresent());
    }

    @Test
    void theRunStopsWithItsJob() throws Exception {
        runSchedule.configure(Configs.of(RunConfig.class, Map.of()));

        runSchedule.deactivate();

        // once at the configuration, which replaces any job before, and once at the stop
        verify(scheduler, times(2)).deleteJob(RunSchedule.JOB_NAME, RecurringJob.group());
        assertFalse(RecurringJob.task(RunSchedule.JOB_NAME).isPresent());
    }

    @Test
    void thePurgeStopsWithItsJob() throws Exception {
        purgeSchedule.configure(Configs.of(RetentionConfig.class, Map.of()));

        purgeSchedule.deactivate();

        verify(scheduler, times(2)).deleteJob(PurgeSchedule.JOB_NAME, RecurringJob.group());
        assertFalse(RecurringJob.task(PurgeSchedule.JOB_NAME).isPresent());
    }
}
