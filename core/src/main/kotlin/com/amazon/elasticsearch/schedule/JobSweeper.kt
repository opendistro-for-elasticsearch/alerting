package com.amazon.elasticsearch.schedule

import com.amazon.elasticsearch.model.ScheduledJob
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.component.Lifecycle
import org.elasticsearch.common.component.LifecycleComponent
import org.elasticsearch.common.component.LifecycleListener
import org.elasticsearch.common.logging.Loggers
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.search.SearchHit
import org.elasticsearch.threadpool.Scheduler
import org.elasticsearch.threadpool.ThreadPool
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class JobSweeper(private val client: Client,
                 private val threadPool: ThreadPool,
                 private val sweeperName: String,
                 private val xContentRegistry: NamedXContentRegistry) : LifecycleComponent, Runnable {
    private val logger = Loggers.getLogger(JobSweeper::class.java)

    private val listeners = mutableListOf<LifecycleListener>()
    private val lifecycle = Lifecycle()
    private val jobScheduler = JobScheduler(threadPool, StubJobRunner())

    private var sweeperThread: Scheduler.Cancellable? = null

    override fun lifecycleState(): Lifecycle.State {
        return this.lifecycle.state()
    }

    override fun run() {
        if (!this.lifecycle.started()) {
            logger.info("$sweeperName is not started.")
            return // Skip running if the lifecycle is not started.
        }
        sweep()
    }

    /**
     * TODO This is naive implementation of Sweeper. There are couple of follow up items that we have to consider.
     * 1. Check enabled/disabled status of Job. I suggest we filter out disabled jobs when we query.
     * 2. Implement safe way to determine that this node should be the one running the ScheduleJob.
     */
    private fun sweep() {
        logger.info("$sweeperName is started. Running...")
        val sweepedScheduledJobs = getScheduledJobs()
        val sweepedScheduledJobNames = sweepedScheduledJobs.map { item -> item.id }
        val currentlyScheduledJobs = jobScheduler.scheduledJobs()

        val jobsToSchedule = getJobsToScheduleByName(sweepedScheduledJobNames, currentlyScheduledJobs)

        // Schedule the Jobs that are in the jobsToSchedule List.
        sweepedScheduledJobs.map { job ->
            if (jobsToSchedule.contains(job.id))
                jobScheduler.schedule(job)
        }

        // Get all the jobs to Deschedule and deschedule them.
        val jobsToDeschedule = getJobsToDeschedule(sweepedScheduledJobNames, currentlyScheduledJobs)
        jobsToDeschedule.map { jobId ->
            jobScheduler.deschedule(jobId)
        }
    }

    /**
     * Look for any new jobs that hasn't been scheduled.
     */
    private fun getJobsToScheduleByName(sweepedScheduledJobs: List<String>, currentlyScheduledJobs: Set<String>): Set<String> {
        val jobsToScheduled = sweepedScheduledJobs.toMutableSet()
        // By remove all currently ScheduledJob we are left we all the jobs that are new.
        jobsToScheduled.removeAll(currentlyScheduledJobs)

        logger.info("New jobs to schedule. $jobsToScheduled")
        return jobsToScheduled
    }

    /**
     * Check for deleted jobs that needs to be descheduled.
     */
    private fun getJobsToDeschedule(sweepedScheduledJobs: List<String>, currentlyScheduledJobs: Set<String>): Set<String> {
        val jobsToDescheduled = currentlyScheduledJobs.toMutableSet()
        // By remove all jobs that are currently in the Index we are left we jobs that should be descheduled.
        jobsToDescheduled.removeAll(sweepedScheduledJobs)
        logger.info("New jobs to deschedule. $jobsToDescheduled")
        return jobsToDescheduled
    }

    private fun getScheduledJobs(): Set<ScheduledJob> {
        val scheduledJobList = mutableSetOf<ScheduledJob>()

        try {
            val searchRequest = SearchRequest(ScheduledJob.SCHEDULED_JOBS_INDEX).preference("_only_local")
            val response: SearchResponse = client.search(searchRequest).actionGet()
            for (searchHit: SearchHit in response.hits.hits) {
                val parser = XContentFactory.xContent(XContentType.JSON).createParser(this.xContentRegistry, searchHit.sourceAsString)

                val scheduledJob = ScheduledJob.parse(parser, id = searchHit.id, version = searchHit.version)
                scheduledJobList.add(scheduledJob)
            }
        } catch (e: ExecutionException) {
            logger.info("ExecutionException $e")
        } catch (e: IndexNotFoundException) {
            logger.info("IndexNotFoundException $e")
        }
        return scheduledJobList
    }

    override fun start() {
        if (lifecycle.canMoveToStarted()) {
            lifecycle.moveToStarted()
            sweeperThread = threadPool.scheduleWithFixedDelay(this, TimeValue(1, TimeUnit.MINUTES), ThreadPool.Names.GENERIC)
        }
        logger.info("$sweeperName started")
    }

    override fun stop() {
        if (lifecycle.canMoveToStopped()) {
            sweeperThread?.cancel()
            lifecycle.moveToStopped()
            jobScheduler.scheduledJobs().map { jobId ->
                jobScheduler.deschedule(jobId)
            }
        }
        logger.info("$sweeperName stopped")
    }

    override fun close() {
        if (lifecycle.canMoveToClosed()) {
            lifecycle.moveToClosed()
            jobScheduler.scheduledJobs().map { jobId ->
                jobScheduler.deschedule(jobId)
            }
        }
        logger.info("$sweeperName closed")
    }

    override fun removeLifecycleListener(listener: LifecycleListener) {
        listeners.remove(listener)
    }

    override fun addLifecycleListener(listener: LifecycleListener) {
        listeners.add(listener)
    }
}