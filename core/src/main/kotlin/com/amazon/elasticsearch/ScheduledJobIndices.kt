package com.amazon.elasticsearch

import com.amazon.elasticsearch.Settings.REQUEST_TIMEOUT
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.util.ElasticAPI
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.client.AdminClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentType

/**
 * Initialize the Elasticsearch components required to run [ScheduledJobs].
 *
 * [initScheduledJobIndex] is called before indexing a new scheduled job. It verifies that the index exists before
 * allowing the index to go through. This is to ensure the correct mappings exist for [ScheduledJob].
 */
class ScheduledJobIndices(private val client: AdminClient,
                          private val settings: Settings,
                          private val clusterService: ClusterService) {

    private val logger = ElasticAPI.INSTANCE.getLogger(javaClass, settings)

    /**
     *  Initialize the indices required for scheduled jobs.
     *
     *  Check if the index already exists via the [clusterService], and if not create it.
     *  The  [CreateIndexRequest] happens on a background thread as it would otherwise be a blocking operation during
     *  the rest call.
     */
    fun initScheduledJobIndex() {
        val clusterState = clusterService.state()
        if (!clusterState.routingTable.hasIndex(ScheduledJob.SCHEDULED_JOBS_INDEX)) {
            try {
                var result: CreateIndexResponse
                var indexRequest = CreateIndexRequest(ScheduledJob.SCHEDULED_JOBS_INDEX)
                        .mapping(ScheduledJob.SCHEDULED_JOB_TYPE, scheduledJobMappings(), XContentType.JSON)
                result = client.indices().create(indexRequest).actionGet(REQUEST_TIMEOUT.get(settings))
                if (!result.isAcknowledged) {
                    throw InternalError("${ScheduledJob.SCHEDULED_JOBS_INDEX} and mappings call not acknowledged.")
                }
                logger.info("Created ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
            } catch (indexExists: ResourceAlreadyExistsException) {
                logger.info("${ScheduledJob.SCHEDULED_JOBS_INDEX} already exists")
            } catch (e: Exception) {
                logger.error("Error occurred while trying to create ${ScheduledJob.SCHEDULED_JOBS_INDEX}.", e)
                throw e
            }
        }
    }

    private fun scheduledJobMappings(): String {
        return javaClass.classLoader.getResource("mappings/scheduled-jobs.json").readText()
    }
}
