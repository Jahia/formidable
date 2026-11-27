package org.jahia.modules.formidable.quarantine.schedule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The recurring job on a mocked scheduler: on the processing server only, at its period, replaced and removed. */
class RecurringJobTest {

    private static final String NAME = "formidable-quarantine-test";
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private Scheduler scheduler;
    private boolean processing;
    private RecurringJob job;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        scheduler = mock(Scheduler.class);
        processing = true;
        job = new RecurringJob(NAME, "a test job", () -> scheduler, () -> processing, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        job.unschedule();
    }

    private SimpleTrigger scheduledTrigger() throws SchedulerException {
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(any(JobDetail.class), trigger.capture());
        return (SimpleTrigger) trigger.getValue();
    }

    @Test
    void theProcessingServerSchedulesTheJobAtItsPeriodForEver() throws Exception {
        assertTrue(job.schedule(Duration.ofSeconds(60), calls::incrementAndGet));

        SimpleTrigger trigger = scheduledTrigger();
        assertEquals(60_000, trigger.getRepeatInterval());
        assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, trigger.getRepeatCount());
        assertEquals(NOW.plusSeconds(60).toEpochMilli(), trigger.getStartTime().getTime());
        assertEquals(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT,
                trigger.getMisfireInstruction());
    }

    @Test
    void aLongPeriodStartsWithinAnHour() throws Exception {
        job.schedule(Duration.ofDays(1), calls::incrementAndGet);

        SimpleTrigger trigger = scheduledTrigger();
        assertEquals(Duration.ofDays(1).toMillis(), trigger.getRepeatInterval());
        assertEquals(NOW.plus(RecurringJob.LONGEST_FIRST_DELAY).toEpochMilli(), trigger.getStartTime().getTime());
    }

    @Test
    void anotherServerOfTheClusterSchedulesNothing() throws Exception {
        processing = false;

        assertFalse(job.schedule(Duration.ofSeconds(60), calls::incrementAndGet));

        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
        assertFalse(RecurringJob.task(NAME).isPresent());
    }

    @Test
    void aPeriodOfZeroTurnsTheJobOff() throws Exception {
        assertFalse(job.schedule(Duration.ZERO, calls::incrementAndGet));

        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
        verify(scheduler).deleteJob(NAME, RecurringJob.group());
    }

    @Test
    void aNewPeriodReplacesTheJobScheduledBefore() throws Exception {
        job.schedule(Duration.ofSeconds(60), calls::incrementAndGet);
        job.schedule(Duration.ofSeconds(5), calls::incrementAndGet);

        var order = inOrder(scheduler);
        order.verify(scheduler).deleteJob(NAME, RecurringJob.group());
        order.verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        order.verify(scheduler).deleteJob(NAME, RecurringJob.group());
        order.verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void theJobCallsItsTaskUntilItIsUnscheduled() {
        job.schedule(Duration.ofSeconds(60), calls::incrementAndGet);

        RecurringJob.call(NAME);
        assertEquals(1, calls.get());

        job.unschedule();
        RecurringJob.call(NAME);
        assertEquals(1, calls.get());
    }

    @Test
    void aSchedulerThatRefusesTheJobLeavesNoTask() throws Exception {
        doThrow(new SchedulerException("refused")).when(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));

        assertFalse(job.schedule(Duration.ofSeconds(60), calls::incrementAndGet));

        assertFalse(RecurringJob.task(NAME).isPresent());
    }
}
