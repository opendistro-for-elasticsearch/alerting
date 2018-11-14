/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch

import com.amazon.elasticsearch.Settings.REQUEST_TIMEOUT
import com.amazon.elasticsearch.Settings.SWEEP_BACKOFF_MILLIS
import com.amazon.elasticsearch.Settings.SWEEP_BACKOFF_RETRY_COUNT
import com.amazon.elasticsearch.Settings.SWEEP_PAGE_SIZE
import com.amazon.elasticsearch.Settings.SWEEP_PERIOD
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.schedule.JobScheduler
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.firstFailureOrNull
import com.amazon.elasticsearch.util.retry
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
import org.elasticsearch.common.lucene.uid.Versions
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.util.concurrent.EsExecutors
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.index.engine.Engine
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
class JobSweeper(private val settings: Settings,
                 private val client: Client,
                 private val clusterService: ClusterService,
                 private val threadPool: ThreadPool,
                 private val xContentRegistry: NamedXContentRegistry,
                 private val scheduler: JobScheduler) : ClusterStateListener, IndexingOperationListener, LifecycleListener() {

    private val logger = ElasticAPI.INSTANCE.getLogger(javaClass, settings)

    private val fullSweepExecutor = Executors.newSingleThreadExecutor(EsExecutors.daemonThreadFactory("scheduled-job-sweeper"))

    private val sweptJobs = ConcurrentHashMap<ShardId, ConcurrentHashMap<JobId, JobVersion>>()

    private val sweepPeriod = SWEEP_PERIOD.get(settings)

    private var scheduledFullSweep: Scheduler.Cancellable? = null

    @Volatile private var lastFullSweepTimeMillis = threadPool.relativeTimeInMillis()

    private val SWEEP_PAGE_MAX_SIZE = SWEEP_PAGE_SIZE.get(settings)
    private val SWEEP_SEARCH_TIMEOUT = REQUEST_TIMEOUT.get(settings)
    private val SWEEP_SEARCH_BACKOFF = BackoffPolicy.exponentialBackoff(
            SWEEP_BACKOFF_MILLIS.get(settings),
            SWEEP_BACKOFF_RETRY_COUNT.get(settings))

    init {
        clusterService.addListener(this)
        clusterService.addLifecycleListener(this)
    }

    override fun afterStart() {
        // Setup an anti-entropy/self-healing background sweep, in case a sweep that was triggered by an event fails.
        val scheduledSweep = Runnable {
            val elapsedTime = TimeValue.timeValueMillis(threadPool.relativeTimeInMillis() - lastFullSweepTimeMillis)
            // Rate limit to at most one full sweep per sweep period
            if (elapsedTime >= sweepPeriod) {
                fullSweepExecutor.submit {
                    logger.debug("Performing background sweep of scheduled jobs.")
                    sweepAllShards()
                }
            }
        }
        scheduledFullSweep = threadPool.scheduleWithFixedDelay(scheduledSweep, sweepPeriod, ThreadPool.Names.SAME)
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
        if (!event.indexRoutingTableChanged(ScheduledJob.SCHEDULED_JOBS_INDEX)) {
            return
        }

        logger.debug("Scheduled Jobs routing table changed. Running full sweep...")
        fullSweepExecutor.submit {
            sweepAllShards()
        }
    }


    /**
     * This callback is invoked when a new job (or new version of a job) is indexed. If the job is assigned to the node
     * it is scheduled. Relies on all indexing operations using optimistic concurrency control to ensure that stale versions
     * of jobs are not scheduled.
     */
    override fun postIndex(shardId: ShardId, index: Engine.Index, result: Engine.IndexResult) {
        if (ElasticAPI.INSTANCE.hasWriteFailed(result)) {
            val shardJobs = sweptJobs[shardId] ?: emptyMap<JobId, JobVersion>()
            val currentVersion = shardJobs[index.id()] ?: Versions.NOT_FOUND
            logger.debug("Indexing failed for ScheduledJob: ${index.id()}. Continuing with current version $currentVersion")
            return
        }

        val localNodeId = clusterService.localNode().id
        val shardNodeIds = clusterService.state().routingTable.shardRoutingTable(shardId)
                .filter { it.active() }
                .map { it.currentNodeId() }
        val shardNodes = ShardNodes(localNodeId, shardNodeIds)
        if (shardNodes.isOwningNode(index.id())) {
            sweep(shardId, index.id(), result.version, index.source())
        }
    }

    /**
     * This callback is invoked when a job is deleted from a shard. The job is descheduled. Relies on all delete operations
     * using optimistic concurrency control to ensure that stale versions of jobs are not scheduled.
     */
    override fun postDelete(shardId: ShardId, delete: Engine.Delete, result: Engine.DeleteResult) {
        if (ElasticAPI.INSTANCE.hasWriteFailed(result)) {
            val shardJobs = sweptJobs[shardId] ?: emptyMap<JobId, JobVersion>()
            val currentVersion = shardJobs[delete.id()] ?: Versions.NOT_FOUND
            logger.debug("Deletion failed for ScheduledJob: ${delete.id()}. Continuing with current version $currentVersion")
            return
        }

        // easier to just check if the job is scheduled and remove it rather than check the consistent hash
        if (scheduler.scheduledJobs().contains(delete.id())) {
            sweep(shardId, delete.id(), result.version, null)
        }
    }

    private fun sweepAllShards() {
        val clusterState = clusterService.state()
        if (!clusterState.routingTable.hasIndex(ScheduledJob.SCHEDULED_JOBS_INDEX)) {
            scheduler.deschedule(scheduler.scheduledJobs())
            sweptJobs.clear()
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
                val shardLogger = ElasticAPI.INSTANCE.getLogger(javaClass, settings, shardId)
                shardLogger.error("Error while sweeping shard $shardId", e)
            }
        }
        lastFullSweepTimeMillis = threadPool.relativeTimeInMillis()
    }

    private fun sweepShard(shardId: ShardId, shardNodes: ShardNodes, startAfter: String = "") {
        val logger = ElasticAPI.INSTANCE.getLogger(javaClass, settings, shardId)
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
            val jobSearchRequest = SearchRequest()
                    .indices(ScheduledJob.SCHEDULED_JOBS_INDEX)
                    .preference("_shards:${shardId.id}|_only_local")
                    .source(SearchSourceBuilder.searchSource()
                            .version(true)
                            //TODO: Remove this after AESAlerting-85
                            .sort(FieldSortBuilder("_id").unmappedType("keyword").missing("_last"))
                            .searchAfter(arrayOf(searchAfter))
                            .size(SWEEP_PAGE_MAX_SIZE)
                            .query(QueryBuilders.matchAllQuery()))


            val response = SWEEP_SEARCH_BACKOFF.retry {
                client.search(jobSearchRequest).actionGet(SWEEP_SEARCH_TIMEOUT)
            }
            if (response.status() != RestStatus.OK) {
                logger.error("Error sweeping shard $shardId.", response.firstFailureOrNull())
                return
            }
            for (hit in response.hits) {
                if (shardNodes.isOwningNode(hit.id)) {
                    sweep(shardId, hit.id, hit.version, hit.sourceRef)
                }
            }
            searchAfter = response.hits.lastOrNull()?.id
        }
    }

    private fun sweep(shardId: ShardId, jobId: JobId, newVersion: JobVersion, jobSource: BytesReference?) {
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
                    if (jobSource != null) {
                        val xcp = ElasticAPI.INSTANCE.jsonParser(xContentRegistry, jobSource)
                        val job: ScheduledJob
                        try {
                            job = ScheduledJob.parse(xcp, jobId, newVersion)
                            if (job.enabled) {
                                scheduler.schedule(job)
                            }
                            return@compute newVersion
                        } catch (e: Exception) {
                            logger.warn("Unable to parse ScheduledJob source: {}",
                                    Strings.cleanTruncate(jobSource.utf8ToString(), 1000))
                            return@compute currentVersion
                        }
                    } else {
                        return@compute null
                    }
                }
    }

    fun getJobSweeperMetrics(): JobSweeperMetrics {
        val bufferMillis = 5 * 1000
        val lastFullSweepTimeMillis = threadPool.relativeTimeInMillis() - lastFullSweepTimeMillis
        return JobSweeperMetrics(lastFullSweepTimeMillis,
                lastFullSweepTimeMillis <= (sweepPeriod.millis + bufferMillis))
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
