package com.amazon.elasticsearch.schedule

import com.amazon.elasticsearch.model.CronSchedule
import com.amazon.elasticsearch.model.IntervalSchedule
import com.amazon.elasticsearch.model.MockScheduledJob
import com.amazon.elasticsearch.model.Schedule
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.threadpool.ThreadPool
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobSchedulerTest {

    @Test
    fun `schedule and deschedule`() {
        val jobRunner = MockJobRunner()
        val jobScheduler = JobScheduler(ThreadPool(getTestSettings()), jobRunner)
        val mockScheduledJob = MockScheduledJob("mockScheduledJob-id", 1L, "mockScheduledJob-name", "MockScheduledJob", true, IntervalSchedule(1, ChronoUnit.MINUTES))

        assertTrue(jobScheduler.schedule(mockScheduledJob))

        assertEquals(setOf("mockScheduledJob-id"), jobScheduler.scheduledJobs(), "List of ScheduledJobs are not the same.")
        assertEquals(0, jobRunner.numberOfRun, "Number of JobRunner ran is wrong.")
        assertTrue(jobScheduler.deschedule("mockScheduledJob-id"), "Descheduling should be true.")
    }

    @Test
    fun `schedule cron past year`() {
        // This is to run cron in Feb 30 which we should never run.
        val cronExpression = "0/5 * 30 2 *"
        val jobRunner = MockJobRunner()
        val jobScheduler = JobScheduler(ThreadPool(getTestSettings()), jobRunner)
        val mockScheduledJob = MockScheduledJob("mockScheduledJob-id", 1L, "mockScheduledJob-name", "MockScheduledJob", true, CronSchedule(cronExpression, ZoneId.of("UTC")))

        assertTrue(jobScheduler.schedule(mockScheduledJob))
        assertEquals(setOf("mockScheduledJob-id"), jobScheduler.scheduledJobs(), "List of ScheduledJobs are not the same.")

        assertEquals(0, jobRunner.numberOfRun, "Number of JobRunner ran is wrong.")

        assertTrue(jobScheduler.deschedule("mockScheduledJob-id"), "Descheduling should be true.")
    }

    @Test
    fun `schedule disabled`() {
        val cronExpression = "0/5 * * * *"
        val jobRunner = MockJobRunner()
        val jobScheduler = JobScheduler(ThreadPool(getTestSettings()), jobRunner)
        val mockScheduledJob = MockScheduledJob("mockScheduledJob-id", 1L, "mockScheduledJob-name", "MockScheduledJob", false, CronSchedule(cronExpression, ZoneId.of("UTC")))

        assertFalse(jobScheduler.schedule(mockScheduledJob), "We should return false if we try to schedule disabled schedule.")
        assertEquals(setOf(), jobScheduler.scheduledJobs(), "List of ScheduledJobs are not the same.")
    }

    @Test
    fun `deschedule none existing schedule`() {
        val cronExpression = "0/5 * * * *"
        val jobRunner = MockJobRunner()
        val jobScheduler = JobScheduler(ThreadPool(getTestSettings()), jobRunner)
        val mockScheduledJob = MockScheduledJob("mockScheduledJob-id", 1L, "mockScheduledJob-name", "MockScheduledJob", true, CronSchedule(cronExpression, ZoneId.of("UTC")))

        assertTrue(jobScheduler.schedule(mockScheduledJob))
        assertEquals(setOf("mockScheduledJob-id"), jobScheduler.scheduledJobs(), "List of ScheduledJobs are not the same.")

        assertEquals(0, jobRunner.numberOfRun, "Number of JobRunner ran is wrong.")

        assertTrue(jobScheduler.deschedule("mockScheduledJob-invalid"), "Descheduling should be true.")
        assertTrue(jobScheduler.deschedule("mockScheduledJob-id"), "Descheduling should be true.")
    }

    private fun getTestSettings() : Settings {
        return Settings.builder()
                .put("node.name", "node-0")
                .build()
    }
}

