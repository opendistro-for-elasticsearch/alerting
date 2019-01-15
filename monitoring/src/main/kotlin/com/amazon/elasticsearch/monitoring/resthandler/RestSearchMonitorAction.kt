/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler


import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.util.context
import com.amazon.elasticsearch.util.ElasticAPI
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.GET
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.IOException

/**
 * Rest handlers to search for monitors.
 */
class RestSearchMonitorAction(settings: Settings, controller: RestController) : BaseRestHandler(settings) {

    init {
        // Search for monitors
        controller.registerHandler(POST, MonitoringPlugin.MONITOR_BASE_URI + "_search", this)
        controller.registerHandler(GET, MonitoringPlugin.MONITOR_BASE_URI + "_search", this)
    }

    override fun getName(): String {
        return "search_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.parseXContent(request.contentOrSourceParamParser())
        searchSourceBuilder.fetchSource(context(request))
        // We add a term query ontop of the customer query to ensure that only scheduled jobs of monitor type are
        // searched.
        searchSourceBuilder.query(QueryBuilders.boolQuery().must(searchSourceBuilder.query())
                .filter(QueryBuilders.termQuery(Monitor.MONITOR_TYPE + ".type", Monitor.MONITOR_TYPE)))
        val searchRequest = SearchRequest()
                .source(searchSourceBuilder)
                .indices(SCHEDULED_JOBS_INDEX)
                .types(SCHEDULED_JOB_TYPE)
        return RestChannelConsumer { channel -> client.search(searchRequest, searchMonitorResponse(channel)) }
    }

    private fun searchMonitorResponse(channel: RestChannel): RestResponseListener<SearchResponse> {
        return object : RestResponseListener<SearchResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: SearchResponse): RestResponse {
                if (response.isTimedOut) {
                    return BytesRestResponse(RestStatus.REQUEST_TIMEOUT, response.toString())
                }
                for (hit in response.hits) {
                    ElasticAPI.INSTANCE
                            .jsonParser(channel.request().xContentRegistry, hit.sourceAsString).use { hitsParser ->
                                val monitor = ScheduledJob.parse(hitsParser, hit.id, hit.version)
                                val xcb = monitor.toXContent(jsonBuilder(), EMPTY_PARAMS)
                                hit.sourceRef(ElasticAPI.INSTANCE.builderToBytesRef(xcb))
                            }
                }
                return BytesRestResponse(RestStatus.OK, response.toXContent(channel.newBuilder(), EMPTY_PARAMS))
            }
        }
    }
}

