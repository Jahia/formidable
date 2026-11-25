package org.jahia.modules.formidable.quarantine.schedule;

import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.run.QuarantineRun;
import org.jahia.modules.formidable.quarantine.store.QuarantineSites;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One call of the scheduled run and of the scheduled purge, over each site that has a quarantine tree. */
class ScheduleTest {

    private QuarantineSites sites;
    private QuarantineRun run;
    private QuarantineRetention retention;
    private RunSchedule runSchedule;
    private PurgeSchedule purgeSchedule;

    @BeforeEach
    void setUp() throws Exception {
        sites = mock(QuarantineSites.class);
        when(sites.withTree()).thenReturn(List.of("siteA", "siteB", "siteC"));
        run = mock(QuarantineRun.class);
        when(run.tryRun(anyString(), isNull(), eq(false))).thenReturn(Optional.empty());
        retention = mock(QuarantineRetention.class);
        RecurringJob job = new RecurringJob("formidable-quarantine-schedule-test", "a test job", () -> null, () -> false,
                Clock.systemUTC());
        runSchedule = new RunSchedule(job);
        runSchedule.setRun(run);
        runSchedule.setSites(sites);
        purgeSchedule = new PurgeSchedule(job);
        purgeSchedule.setRetention(retention);
        purgeSchedule.setSites(sites);
    }

    @Test
    void theScheduledRunRunsEverySiteOnScheduleAndGoesOnAfterAFailure() throws Exception {
        when(run.tryRun("siteA", null, false)).thenThrow(new QuarantineStoreException("unreadable"));
        when(run.tryRun("siteB", null, false)).thenThrow(new IllegalStateException("unexpected"));

        runSchedule.runEverySite();

        verify(run).tryRun("siteA", null, false);
        verify(run).tryRun("siteB", null, false);
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
}
