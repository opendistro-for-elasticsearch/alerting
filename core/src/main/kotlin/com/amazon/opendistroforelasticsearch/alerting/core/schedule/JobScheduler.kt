/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.alerting.core.schedule

import com.amazon.opendistroforelasticsearch.alerting.core.JobRunner
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import org.elasticsearch.common.logging.Loggers
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.threadpool.ThreadPool
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * JobScheduler is a class for scheduling and descheduling ScheduleJobs. This class keeps list of ScheduledJob Ids that are currently scheduled.
 *
 * JobScheduler is unaware of the ScheduledJob version and it is up to callers to ensure that the older version of ScheduledJob to be descheduled and schedule the new version.
 */
class JobScheduler(private val threadPool: ThreadPool, private val jobRunner: JobRunner) {
    private val logger = Loggers.getLogger(JobScheduler::class.java)

    /**
     * Map of ScheduledJobName to Info of the ScheduledJob.
     */
    private val scheduledJobIdToInfo = ConcurrentHashMap<String, ScheduledJobInfo>()

    /**
     * Schedules the jobs in [jobsToSchedule] for execution.
     *
     * @return List of jobs that could not be scheduled
     */
    fun schedule(vararg jobsToSchedule: ScheduledJob): List<ScheduledJob> {
        return jobsToSchedule.filter {
            !this.schedule(it)
        }
    }

    /**
     * Schedules a single [scheduledJob]
     *
     * The [schedule] does not check for new version of the ScheduledJob.
     * The caller should be aware of the update that happened in [ScheduledJob] and must first call [deschedule] if the Job version is updated and then followed by [schedule]
     *
     * [schedule] is considered successfully scheduled when
     * 1. Cron expression is out of Scheduled. eg. past year 2016.
     * 2. If the schedule already exists. This is to keep the function idempotent.
     * 3. we are able to schedule the job in the [ThreadPool.schedule]
     *
     * [schedule] is considered unsuccessfully schedule when
     * 1. Schedule is disabled.
     * 2. In rare race condition where scheduledJob is already marked [ScheduledJobInfo.descheduled] <code>true</code> at the time of making [ThreadPool.schedule]
     * 3. any unexpected failures.
     *
     * @return <code>true</code> if the ScheduledJob is scheduled successfully;
     *         <code>false</code> otherwise.
     */
    fun schedule(scheduledJob: ScheduledJob): Boolean {
        logger.info("Scheduling jobId : ${scheduledJob.id}, name: ${scheduledJob.name}")

        if (!scheduledJob.enabled) {
            // ensure that the ScheduledJob is not enabled. The caller should be also checking this before calling this function.
            return false
        }

        val scheduledJobInfo = scheduledJobIdToInfo.getOrPut(scheduledJob.id) {
            ScheduledJobInfo(scheduledJob.id, scheduledJob)
        }
        if (scheduledJobInfo.scheduledFuture != null) {
            // This means that the given ScheduledJob already has schedule running. We should not schedule any more.
            return true
        }

        // Start the first schedule.
        return this.reschedule(scheduledJob, scheduledJobInfo)
    }

    /**
     * Deschedules the jobs given ScheduledJob [ids].
     *
     * caller should retry [deschedule] that failed.
     *
     * @return List of job ids failed to deschedule.
     */
    fun deschedule(ids: Collection<String>): List<String> {
        return ids.filter {
            !this.deschedule(it)
        }.also {
            if (it.isNotEmpty()) {
                logger.error("Unable to deschedule jobs $it")
            }
        }
    }

    /**
     * Mark the scheduledJob as descheduled and try to cancel any future schedule for given scheduledJob id.
     *
     * [deschedule] is considered successful when
     * 1. ScheduledJob id does not exist.
     * 2. ScheduledJob is complete.
     * 3. ScheduledJob is not complete and is successfully cancelled.
     *
     * Caller should retry if ScheduledJob [deschedule] fails.
     *
     * @return <code>true</code> if job is successfully descheduled;
     *         <code>false</code> otherwise.
     */
    fun deschedule(id: String): Boolean {
        val scheduledJobInfo = scheduledJobIdToInfo[id]
        if (scheduledJobInfo == null) {
            logger.info("JobId $id does not exist.")
            return true
        } else {
            logger.info("Descheduling jobId : $id")
            scheduledJobInfo.descheduled = true
            scheduledJobInfo.actualPreviousExecutionTime = null
            scheduledJobInfo.expectedNextExecutionTime = null
            var result = true
            val scheduledFuture = scheduledJobInfo.scheduledFuture

            if (scheduledFuture != null && !scheduledFuture.isDone) {
                result = scheduledFuture.cancel(false)
            }

            if (result) {
                // If we have successfully descheduled the job, remove from the info map.
                scheduledJobIdToInfo.remove(scheduledJobInfo.scheduledJobId, scheduledJobInfo)
            }
            return result
        }
    }

    /**
     * @return list of jobIds that are scheduled.
     */
    fun scheduledJobs(): Set<String> {
        return scheduledJobIdToInfo.keys
    }

    private fun reschedule(scheduleJob: ScheduledJob, scheduledJobInfo: ScheduledJobInfo): Boolean {
        if (scheduleJob.enabledTime == null) {
            logger.info("${scheduleJob.name} there is no enabled time. This job should never have been scheduled.")
            return false
        }
        scheduledJobInfo.expectedNextExecutionTime = scheduleJob.schedule.getExpectedNextExecutionTime(
                scheduleJob.enabledTime!!, scheduledJobInfo.expectedNextExecutionTime)

        // Validate if there is next execution that needs to happen.
        // e.g cron job that is expected to run in 30th of Feb (which doesn't exist). "0/5 * 30 2 *"
        if (scheduledJobInfo.expectedNextExecutionTime == null) {
            logger.info("${scheduleJob.name} there is no next execution time.")
            return true
        }

        val duration = Duration.between(Instant.now(), scheduledJobInfo.expectedNextExecutionTime)

        // Create anonymous runnable.
        val runnable = Runnable {
            // Check again if the scheduled job is marked descheduled.
            if (scheduledJobInfo.descheduled) {
                return@Runnable // skip running job if job is marked descheduled.
            }

            // Order of operations inside here matter, we specifically call getPeriodEndingAt before reschedule because
            // reschedule will update expectedNextExecutionTime to the next one which would throw off the startTime/endTime
            val (startTime, endTime) = scheduleJob.schedule.getPeriodEndingAt(scheduledJobInfo.expectedNextExecutionTime)
            scheduledJobInfo.actualPreviousExecutionTime = Instant.now()

            this.reschedule(scheduleJob, scheduledJobInfo)

            jobRunner.runJob(scheduleJob, startTime, endTime)
        }

        // Check descheduled flag as close as possible before we actually schedule a job.
        // This way we will can minimize race conditions.
        if (scheduledJobInfo.descheduled) {
            // Do not reschedule if schedule has been marked descheduled.
            return false
        }

        // Finally schedule the job in the ThreadPool with next time to execute.
        val scheduledFuture = threadPool.schedule(TimeValue(duration.toNanos(), TimeUnit.NANOSECONDS), ThreadPool.Names.SAME, runnable)
        scheduledJobInfo.scheduledFuture = scheduledFuture

        return true
    }

    fun getJobSchedulerMetric(): List<JobSchedulerMetrics> {
        return scheduledJobIdToInfo.entries.stream()
                .map { entry ->
                    JobSchedulerMetrics(entry.value.scheduledJobId,
                            entry.value.actualPreviousExecutionTime?.toEpochMilli(),
                            entry.value.scheduledJob.schedule.runningOnTime(entry.value.actualPreviousExecutionTime))
                }
                .collect(Collectors.toList())
    }

    fun postIndex(job: ScheduledJob) {
        jobRunner.postIndex(job)
    }

    fun postDelete(jobId: String) {
        jobRunner.postDelete(jobId)
    }

    /**
     * ScheduledJobInfo which we can use to check if the job should be descheduled.
     * Some Idea for more use of this class is
     * 1. Total number of runs.
     * 2. Tracking of number of failed runs (helps to control error handling.)
     */
    private data class ScheduledJobInfo(
        val scheduledJobId: String,
        val scheduledJob: ScheduledJob,
        var descheduled: Boolean = false,
        var actualPreviousExecutionTime: Instant? = null,
        var expectedNextExecutionTime: Instant? = null,
        var scheduledFuture: ScheduledFuture<*>? = null
    )
}
