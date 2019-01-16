package com.amazon.elasticsearch

import com.amazon.elasticsearch.model.ScheduledJob
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.client.AdminClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.xcontent.XContentType

/**
 * Initialize the Elasticsearch components required to run [ScheduledJobs].
 *
 * [initScheduledJobIndex] is called before indexing a new scheduled job. It verifies that the index exists before
 * allowing the index to go through. This is to ensure the correct mappings exist for [ScheduledJob].
 */
class ScheduledJobIndices(private val client: AdminClient,
                          private val clusterService: ClusterService) {

    /**
     *  Initialize the indices required for scheduled jobs.
     *
     *  Check if the index already exists via the [clusterService], and if not create it.
     *  The  [CreateIndexRequest] happens on a background thread as it would otherwise be a blocking operation during
     *  the rest call.
     */
    fun initScheduledJobIndex(actionListener: ActionListener<CreateIndexResponse>) {
        val clusterState = clusterService.state()
        if (!clusterState.routingTable.hasIndex(ScheduledJob.SCHEDULED_JOBS_INDEX)) {
            var indexRequest = CreateIndexRequest(ScheduledJob.SCHEDULED_JOBS_INDEX)
                    .mapping(ScheduledJob.SCHEDULED_JOB_TYPE, scheduledJobMappings(), XContentType.JSON)
            client.indices().create(indexRequest, actionListener)
        }
    }

    private fun scheduledJobMappings(): String {
        return javaClass.classLoader.getResource("mappings/scheduled-jobs.json").readText()
    }
}
