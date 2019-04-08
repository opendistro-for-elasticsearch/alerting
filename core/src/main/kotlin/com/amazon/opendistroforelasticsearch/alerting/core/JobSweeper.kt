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

package com.amazon.opendistroforelasticsearch.alerting.core

import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.schedule.JobScheduler
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings.Companion.REQUEST_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings.Companion.SWEEPER_ENABLED
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings.Companion.SWEEP_BACKOFF_MILLIS
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings.Companion.SWEEP_BACKOFF_RETRY_COUNT
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings.Companion.SWEEP_PAGE_SIZE
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings.Companion.SWEEP_PERIOD
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.firstFailureOrNull
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.retry
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.ClusterChangedEvent
import org.elasticsearch.cluster.ClusterStateListener
import org.elasticsearch.cluster.routing.IndexShardRoutingTable
import org.elasticsearch.cluster.routing.Murmur3HashFunction
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.Strings
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.component.LifecycleListener
import org.elasticsearch.common.logging.Loggers
import org.elasticsearch.common.lucene.uid.Versions
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.util.concurrent.EsExecutors
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.engine.Engine
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.shard.IndexingOperationListener
import org.elasticsearch.index.shard.ShardId
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.FieldSortBuilder
import org.elasticsearch.threadpool.Scheduler
import org.elasticsearch.threadpool.ThreadPool
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

typealias JobId = String
typealias JobVersion = Long

/**
 * 'Sweeping' is the process of listening for new and updated [ScheduledJob]s and deciding if they should be scheduled for
 * execution on this node. The [JobSweeper] runs on every node, sweeping all local active shards that are present on the node.
 *
 * A [consistent hash][ShardNodes] is used to distribute jobs across all nodes that contain an active instance of the same shard.
 * This minimizes any interruptions in job execution when the cluster configuration changes.
 *
 * There are two types of sweeps:
 * - *Full sweeps* occur when the [routing table][IndexShardRoutingTable] for the shard changes (for e.g. a replica has been
 *  added or removed).  The full sweep re-reads all jobs in the shard, deciding which ones to run locally. All full sweeps
 *  happen asynchronously in the background in a serial manner. See the [sweepAllShards] method.
 * - *Single job sweeps* occur when a new version of the job is indexed or deleted. An [IndexingOperationListener] listens
 * for index changes and synchronously schedules or removes the job from the scheduler.
 */
class JobSweeper(
    private val settings: Settings,
    private val client: Client,
    private val clusterService: ClusterService,
    private val threadPool: ThreadPool,
    private val xContentRegistry: NamedXContentRegistry,
    private val scheduler: JobScheduler,
    private val sweepableJobTypes: List<String>
) : ClusterStateListener, IndexingOperationListener, LifecycleListener() {
    private val logger = LogManager.getLogger(javaClass)

    private val fullSweepExecutor = Executors.newSingleThreadExecutor(EsExecutors.daemonThreadFactory("opendistro_job_sweeper"))

    private val sweptJobs = ConcurrentHashMap<ShardId, ConcurrentHashMap<JobId, JobVersion>>()

    private var scheduledFullSweep: Scheduler.Cancellable? = null

    @Volatile private var lastFullSweepTimeNano = System.nanoTime()

    @Volatile private var requestTimeout = REQUEST_TIMEOUT.get(settings)
    @Volatile private var sweepPeriod = SWEEP_PERIOD.get(settings)
    @Volatile private var sweeperEnabled = SWEEPER_ENABLED.get(settings)
    @Volatile private var sweepPageSize = SWEEP_PAGE_SIZE.get(settings)
    @Volatile private var sweepBackoffMillis = SWEEP_BACKOFF_MILLIS.get(settings)
    @Volatile private var sweepBackoffRetryCount = SWEEP_BACKOFF_RETRY_COUNT.get(settings)
    @Volatile private var sweepSearchBackoff = BackoffPolicy.exponentialBackoff(sweepBackoffMillis, sweepBackoffRetryCount)

    init {
        clusterService.addListener(this)
        clusterService.addLifecycleListener(this)
        clusterService.clusterSettings.addSettingsUpdateConsumer(SWEEP_PERIOD) {
            // if sweep period change, restart background sweep with new sweep period
            logger.debug("Reinitializing background full sweep with period: ${sweepPeriod.minutes()}")
            sweepPeriod = it
            initBackgroundSweep()
        }
        clusterService.clusterSettings.addSettingsUpdateConsumer(SWEEPER_ENABLED) {
            sweeperEnabled = it
            if (!sweeperEnabled) disable() else enable()
        }
        clusterService.clusterSettings.addSettingsUpdateConsumer(SWEEP_BACKOFF_MILLIS) {
            sweepBackoffMillis = it
            sweepSearchBackoff = BackoffPolicy.exponentialBackoff(sweepBackoffMillis, sweepBackoffRetryCount)
        }
        clusterService.clusterSettings.addSettingsUpdateConsumer(SWEEP_BACKOFF_RETRY_COUNT) {
            sweepBackoffRetryCount = it
            sweepSearchBackoff = BackoffPolicy.exponentialBackoff(sweepBackoffMillis, sweepBackoffRetryCount)
        }
        clusterService.clusterSettings.addSettingsUpdateConsumer(SWEEP_PAGE_SIZE) { sweepPageSize = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(REQUEST_TIMEOUT) { requestTimeout = it }
    }

    override fun afterStart() {
        initBackgroundSweep()
    }

    override fun beforeStop() {
        scheduledFullSweep?.cancel()
    }

    override fun beforeClose() {
        fullSweepExecutor.shutdown()
    }

    /**
     * Initiates a full sweep of all local shards when the index routing table is changed (for e.g. when the node joins
     * the cluster, a replica is added, removed or promoted to primary).
     *
     * This callback won't be invoked concurrently since cluster state changes are applied serially to the node
     * in the order they occur on the master. However we can't block this callback for the duration of a full sweep so
     * we perform the sweep in the background in a single threaded executor [fullSweepExecutor].
     */
    override fun clusterChanged(event: ClusterChangedEvent) {
        if (!isSweepingEnabled()) return

        if (!event.indexRoutingTableChanged(ScheduledJob.SCHEDULED_JOBS_INDEX)) return

        logger.debug("Scheduled Jobs routing table changed. Running full sweep...")
        fullSweepExecutor.submit {
            sweepAllShards()
        }
    }

    /**
     * This callback is invoked when a new job (or new version of a job) is indexed. If the job is assigned to the node
     * it is scheduled. Relies on all indexing operations using optimistic concurrency control to ensure that stale versions
     * of jobs are not scheduled. It schedules job only if it is one of the [sweepableJobTypes]
     *
     */
    override fun postIndex(shardId: ShardId, index: Engine.Index, result: Engine.IndexResult) {
        if (!isSweepingEnabled()) return

        if (result.resultType != Engine.Result.Type.SUCCESS) {
            val shardJobs = sweptJobs[shardId] ?: emptyMap<JobId, JobVersion>()
            val currentVersion = shardJobs[index.id()] ?: Versions.NOT_FOUND
            logger.debug("Indexing failed for ScheduledJob: ${index.id()}. Continuing with current version $currentVersion")
            return
        }

        if (isOwningNode(shardId, index.id())) {
            val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, index.source(), XContentType.JSON)
            if (isSweepableJobType(xcp)) {
                val job = parseAndSweepJob(xcp, shardId, index.id(), result.version, index.source(), true)
                if (job != null) scheduler.postIndex(job)
            } else {
                logger.debug("Not a valid job type in document ${index.id()} to sweep.")
            }
        }
    }

    /**
     * This callback is invoked when a job is deleted from a shard. The job is descheduled. Relies on all delete operations
     * using optimistic concurrency control to ensure that stale versions of jobs are not scheduled.
     */
    override fun postDelete(shardId: ShardId, delete: Engine.Delete, result: Engine.DeleteResult) {
        if (!isSweepingEnabled()) return

        if (result.resultType != Engine.Result.Type.SUCCESS) {
            val shardJobs = sweptJobs[shardId] ?: emptyMap<JobId, JobVersion>()
            val currentVersion = shardJobs[delete.id()] ?: Versions.NOT_FOUND
            logger.debug("Deletion failed for ScheduledJob: ${delete.id()}. Continuing with current version $currentVersion")
            return
        }

        if (isOwningNode(shardId, delete.id())) {
            if (scheduler.scheduledJobs().contains(delete.id())) {
                sweep(shardId, delete.id(), result.version, null)
            }
            scheduler.postDelete(delete.id())
        }
    }

    fun enable() {
        // initialize background sweep
        initBackgroundSweep()
        // set sweeperEnabled flag to true to make the listeners aware of this setting
        sweeperEnabled = true
    }

    fun disable() {
        // cancel background sweep
        scheduledFullSweep?.cancel()
        // deschedule existing jobs on this node
        logger.info("Descheduling all jobs as sweeping is disabled")
        scheduler.deschedule(scheduler.scheduledJobs())
        // set sweeperEnabled flag to false to make the listeners aware of this setting
        sweeperEnabled = false
    }

    public fun isSweepingEnabled(): Boolean {
        // Although it is a single link check, keeping it as a separate function, so we
        // can abstract out logic of finding out whether to proceed or not
        return sweeperEnabled == true
    }

    private fun initBackgroundSweep() {

        // if sweeping disabled, background sweep should not be triggered
        if (!isSweepingEnabled()) return

        // cancel existing background thread if present
        scheduledFullSweep?.cancel()

        // Setup an anti-entropy/self-healing background sweep, in case a sweep that was triggered by an event fails.
        val scheduledSweep = Runnable {
            val elapsedTime = getFullSweepElapsedTime()

            // Rate limit to at most one full sweep per sweep period
            // The schedule runs may wake up a few milliseconds early.
            // Delta will be giving some buffer on the schedule to allow waking up slightly earlier.
            val delta = sweepPeriod.millis - elapsedTime.millis
            if (delta < 20L) { // give 20ms buffer.
                fullSweepExecutor.submit {
                    logger.debug("Performing background sweep of scheduled jobs.")
                    sweepAllShards()
                }
            }
        }
        scheduledFullSweep = threadPool.scheduleWithFixedDelay(scheduledSweep, sweepPeriod, ThreadPool.Names.SAME)
    }

    private fun sweepAllShards() {
        val clusterState = clusterService.state()
        if (!clusterState.routingTable.hasIndex(ScheduledJob.SCHEDULED_JOBS_INDEX)) {
            scheduler.deschedule(scheduler.scheduledJobs())
            sweptJobs.clear()
            lastFullSweepTimeNano = System.nanoTime()
            return
        }

        // Find all shards that are currently assigned to this node.
        val localNodeId = clusterState.nodes.localNodeId
        val localShards = clusterState.routingTable.allShards(ScheduledJob.SCHEDULED_JOBS_INDEX)
                // Find all active shards
                .filter { it.active() }
                // group by shardId
                .groupBy { it.shardId() }
                // assigned to local node
                .filter { (_, shards) -> shards.any { it.currentNodeId() == localNodeId } }

        // Remove all jobs on shards that are no longer assigned to this node.
        val removedShards = sweptJobs.keys - localShards.keys
        removedShards.forEach { shardId ->
            val shardJobs = sweptJobs.remove(shardId) ?: emptyMap<JobId, JobVersion>()
            scheduler.deschedule(shardJobs.keys)
        }

        // resweep all shards that are assigned to this node.
        localShards.forEach { (shardId, shards) ->
            try {
                sweepShard(shardId, ShardNodes(localNodeId, shards.map { it.currentNodeId() }))
            } catch (e: Exception) {
                val shardLogger = Loggers.getLogger(javaClass, shardId)
                shardLogger.error("Error while sweeping shard $shardId", e)
            }
        }
        lastFullSweepTimeNano = System.nanoTime()
    }

    private fun sweepShard(shardId: ShardId, shardNodes: ShardNodes, startAfter: String = "") {
        val logger = Loggers.getLogger(javaClass, shardId)
        logger.debug("Sweeping shard $shardId")

        // Remove any jobs that are currently scheduled that are no longer owned by this node
        val currentJobs = sweptJobs.getOrPut(shardId) { ConcurrentHashMap() }
        currentJobs.keys.filterNot { shardNodes.isOwningNode(it) }.forEach {
            scheduler.deschedule(it)
            currentJobs.remove(it)
        }

        // sweep the shard for new and updated jobs. Uses a search after query to paginate, assuming that any concurrent
        // updates and deletes are handled by the index operation listener.
        var searchAfter: String? = startAfter
        while (searchAfter != null) {
            val boolQueryBuilder = BoolQueryBuilder()
            sweepableJobTypes.forEach { boolQueryBuilder.should(QueryBuilders.existsQuery(it)) }
            val jobSearchRequest = SearchRequest()
                    .indices(ScheduledJob.SCHEDULED_JOBS_INDEX)
                    .preference("_shards:${shardId.id}|_only_local")
                    .source(SearchSourceBuilder.searchSource()
                            .version(true)
                            .sort(FieldSortBuilder("_id")
                            .unmappedType("keyword")
                            .missing("_last"))
                            .searchAfter(arrayOf(searchAfter))
                            .size(sweepPageSize)
                            .query(boolQueryBuilder))

            val response = sweepSearchBackoff.retry {
                client.search(jobSearchRequest).actionGet(requestTimeout)
            }
            if (response.status() != RestStatus.OK) {
                logger.error("Error sweeping shard $shardId.", response.firstFailureOrNull())
                return
            }
            for (hit in response.hits) {
                if (shardNodes.isOwningNode(hit.id)) {
                    val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                            hit.sourceRef, XContentType.JSON)
                    parseAndSweepJob(xcp, shardId, hit.id, hit.version, hit.sourceRef)
                }
            }
            searchAfter = response.hits.lastOrNull()?.id
        }
    }

    private fun sweep(
        shardId: ShardId,
        jobId: JobId,
        newVersion: JobVersion,
        job: ScheduledJob?,
        failedToParse: Boolean = false
    ) {
        sweptJobs.getOrPut(shardId) { ConcurrentHashMap() }
            // Use [compute] to update atomically in case another thread concurrently indexes/deletes the same job
            .compute(jobId) { _, currentVersion ->
                if (newVersion <= (currentVersion ?: Versions.NOT_FOUND)) {
                    logger.debug("Skipping job $jobId, $newVersion <= $currentVersion")
                    return@compute currentVersion
                }

                // deschedule the currently scheduled version
                if (scheduler.scheduledJobs().contains(jobId)) {
                    scheduler.deschedule(jobId)
                }

                if (failedToParse) {
                    return@compute currentVersion
                }
                if (job != null) {
                    if (job.enabled) {
                        scheduler.schedule(job)
                    }
                    return@compute newVersion
                } else {
                    return@compute null
                }
            }
    }

    private fun parseAndSweepJob(
        xcp: XContentParser,
        shardId: ShardId,
        jobId: JobId,
        jobVersion: JobVersion,
        jobSource: BytesReference,
        typeIsParsed: Boolean = false
    ): ScheduledJob? {
        return try {
            val job = parseScheduledJob(xcp, jobId, jobVersion, typeIsParsed)
            sweep(shardId, jobId, jobVersion, job)
            job
        } catch (e: Exception) {
            logger.warn("Unable to parse ScheduledJob source: {}",
                    Strings.cleanTruncate(jobSource.utf8ToString(), 1000))
            sweep(shardId, jobId, jobVersion, null, true)
            null
        }
    }

    private fun parseScheduledJob(xcp: XContentParser, jobId: JobId, jobVersion: JobVersion, typeIsParsed: Boolean): ScheduledJob {
        return if (typeIsParsed) {
            ScheduledJob.parse(xcp, xcp.currentName(), jobId, jobVersion)
        } else {
            ScheduledJob.parse(xcp, jobId, jobVersion)
        }
    }

    private fun getFullSweepElapsedTime(): TimeValue {
        return TimeValue.timeValueNanos(System.nanoTime() - lastFullSweepTimeNano)
    }

    fun getJobSweeperMetrics(): JobSweeperMetrics {
        if (!isSweepingEnabled()) {
            return JobSweeperMetrics(-1, true)
        }
        val elapsedTime = getFullSweepElapsedTime()
        return JobSweeperMetrics(elapsedTime.millis, elapsedTime.millis <= sweepPeriod.millis)
    }

    private fun isSweepableJobType(xcp: XContentParser): Boolean {
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
        val jobType = xcp.currentName()
        return sweepableJobTypes.contains(jobType)
    }

    private fun isOwningNode(shardId: ShardId, jobId: JobId): Boolean {
        val localNodeId = clusterService.localNode().id
        val shardNodeIds = clusterService.state().routingTable.shardRoutingTable(shardId)
                .filter { it.active() }
                .map { it.currentNodeId() }
        val shardNodes = ShardNodes(localNodeId, shardNodeIds)
        return shardNodes.isOwningNode(jobId)
    }
}

/**
 * A group of nodes in the cluster that contain active instances of a single ES shard.  This uses a consistent hash to divide
 * the jobs indexed in that shard amongst the nodes such that each job is "owned" by exactly one of the nodes.
 * The local node must have an active instance of the shard.
 *
 * Implementation notes: This class is not thread safe. It uses the same [hash function][Murmur3HashFunction] that ES uses
 * for routing. For each real node `100` virtual nodes are added to provide a good distribution.
 */
private class ShardNodes(val localNodeId: String, activeShardNodeIds: Collection<String>) {

    private val circle = TreeMap<Int, String>()

    companion object {
        private const val VIRTUAL_NODE_COUNT = 100
    }

    init {
        for (node in activeShardNodeIds) {
            for (i in 0 until VIRTUAL_NODE_COUNT) {
                circle[Murmur3HashFunction.hash(node + i)] = node
            }
        }
    }

    fun isOwningNode(id: JobId): Boolean {
        if (circle.isEmpty()) {
            return false
        }
        val hash = Murmur3HashFunction.hash(id)
        val nodeId = (circle.higherEntry(hash) ?: circle.firstEntry()).value
        return (localNodeId == nodeId)
    }
}
