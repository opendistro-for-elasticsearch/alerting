package com.amazon.elasticsearch.action.node

import org.elasticsearch.action.support.nodes.NodesOperationRequestBuilder
import org.elasticsearch.client.ElasticsearchClient

class ScheduledJobsStatsRequestBuilder(client: ElasticsearchClient, action: ScheduledJobsStatsAction) :
        NodesOperationRequestBuilder<ScheduledJobsStatsRequest, ScheduledJobsStatsResponse, ScheduledJobsStatsRequestBuilder>(client, action, ScheduledJobsStatsRequest())