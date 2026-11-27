package org.jahia.test.modules.formidable.quarantine.web;

import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The period of the scheduled run as a scheduler holds it: a trigger, none, or a scheduler that cannot be read. */
class ScheduledRunTest {

    @Test
    void theTriggerOfTheJobGivesItsPeriodInSeconds() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        SimpleTrigger trigger = new SimpleTrigger(ScheduledRun.JOB_NAME, ScheduledRun.JOB_GROUP, new Date(), null,
                SimpleTrigger.REPEAT_INDEFINITELY, 2_000);
        when(scheduler.getTriggersOfJob(ScheduledRun.JOB_NAME, ScheduledRun.JOB_GROUP))
                .thenReturn(new Trigger[]{trigger});

        assertEquals(2, ScheduledRun.periodSeconds(() -> scheduler));
    }

    @Test
    void noTriggerIsAScheduledRunThatIsOff() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getTriggersOfJob(ScheduledRun.JOB_NAME, ScheduledRun.JOB_GROUP)).thenReturn(new Trigger[0]);

        assertEquals(0, ScheduledRun.periodSeconds(() -> scheduler));
    }

    @Test
    void aSchedulerThatFailsToAnswerIsNoPeriod() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getTriggersOfJob(ScheduledRun.JOB_NAME, ScheduledRun.JOB_GROUP))
                .thenThrow(new SchedulerException("the scheduler is shut down"));

        assertEquals(ScheduledRun.UNREAD, ScheduledRun.periodSeconds(() -> scheduler));
    }

    @Test
    void aSchedulerThatCannotBeReachedIsNoPeriod() {
        assertEquals(ScheduledRun.UNREAD, ScheduledRun.periodSeconds(() -> {
            throw new IllegalStateException("the scheduler service is not started");
        }));
    }
}
