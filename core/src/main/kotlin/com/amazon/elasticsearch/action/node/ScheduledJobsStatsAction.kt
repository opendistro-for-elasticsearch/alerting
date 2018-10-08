package com.amazon.elasticsearch.action.node

import org.elasticsearch.action.Action
import org.elasticsearch.client.ElasticsearchClient

class ScheduledJobsStatsAction : Action<ScheduledJobsStatsRequest, ScheduledJobsStatsResponse, ScheduledJobsStatsRequestBuilder>(NAME) {
    companion object {
        val INSTANCE = ScheduledJobsStatsAction()
        const val NAME = "cluster:admin/awses/_scheduled_jobs/stats"
    }

    override fun newRequestBuilder(client: ElasticsearchClient): ScheduledJobsStatsRequestBuilder {
        return ScheduledJobsStatsRequestBuilder(client, this)
    }

    override fun newResponse(): ScheduledJobsStatsResponse {
        return ScheduledJobsStatsResponse()
    }
}