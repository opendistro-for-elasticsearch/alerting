package com.amazon.elasticsearch.schedule

import com.amazon.elasticsearch.JobRunner
import com.amazon.elasticsearch.model.ScheduledJob
import org.elasticsearch.common.logging.Loggers
import java.time.Instant

/**
 * Stub JobRunner
 */
class StubJobRunner : JobRunner<ScheduledJob> {
    private val logger = Loggers.getLogger(StubJobRunner::class.java)

    override fun runJob(job: ScheduledJob, periodStart: Instant, periodEnd: Instant) {
        val sleepTime = (2..6).shuffled().last()
        logger.info("Id: ${job.id} Name: ${job.name} Type: ${job.type} Cron: ${job.schedule} - Running for $sleepTime seconds.")

        try {
            Thread.sleep(sleepTime * 1000L)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        logger.info("Id: ${job.id} Name: ${job.name} Type: ${job.type} Cron: ${job.schedule} - Complete!")
    }
}