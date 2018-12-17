/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.ScheduledJobIndices
import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.Settings.REQUEST_TIMEOUT
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.threadpool.ThreadPool

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.elasticsearch.monitoring.util.REFRESH
import com.amazon.elasticsearch.monitoring.util._ID
import com.amazon.elasticsearch.monitoring.util._VERSION
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestRequest.Method.PUT

/**
 * Rest handlers to create and update monitors
 */
class RestIndexMonitorAction(settings: Settings, controller: RestController, jobIndices: ScheduledJobIndices) : BaseRestHandler(settings) {
    private val TIMEOUT: TimeValue
    private var scheduledJobIndices: ScheduledJobIndices

    init {
        controller.registerHandler(POST, MonitoringPlugin.MONITOR_BASE_URI, this) // Create a new monitor
        controller.registerHandler(PUT, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this)
        TIMEOUT = REQUEST_TIMEOUT.get(settings)
        scheduledJobIndices = jobIndices
    }

    override fun getName(): String {
        return "index_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer {
        val id = request.param("monitorID", Monitor.NO_ID)
        if (request.method() == PUT && Monitor.NO_ID == id) {
            throw IllegalArgumentException("Missing monitor ID")
        }

        // Validate request by parsing JSON to Monitor
        val xcp = request.contentParser()
        ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        val monitor = Monitor.parse(xcp, id).copy(lastUpdateTime = Instant.now())
        val builder = XContentFactory.contentBuilder(request.xContentType)
        val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
        if (request.method() == PUT) {
            indexRequest.id(id).version(RestActions.parseVersion(request))
        }
        indexRequest.refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }
        indexRequest.source(monitor.toXContentWithType(builder))
        return RestChannelConsumer { channel ->
            try {
                client.threadPool().executor(ThreadPool.Names.MANAGEMENT).submit { scheduledJobIndices.initScheduledJobIndex() }.get(TIMEOUT.seconds, TimeUnit.SECONDS)
                client.index(indexRequest, indexMonitorResponse(channel))
            } catch (e: Exception) {
                val status = if (e is ElasticsearchException)
                    e.status()
                else
                    RestStatus.INTERNAL_SERVER_ERROR
                channel.sendResponse(BytesRestResponse(status, e.message))
            }
        }
    }

    private fun indexMonitorResponse(channel: RestChannel): RestResponseListener<IndexResponse> {
        return object : RestResponseListener<IndexResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: IndexResponse): RestResponse {
                if (response.shardInfo.successful < 1) {
                    return BytesRestResponse(response.status(), response.toXContent(channel.newErrorBuilder(), EMPTY_PARAMS))
                }

                val builder = channel.newBuilder()
                        .startObject()
                        .field(_ID, response.id)
                        .field(_VERSION, response.version)
                        .endObject()

                val restResponse = BytesRestResponse(response.status(), builder)
                if (response.status() == RestStatus.CREATED) {
                    val location = MonitoringPlugin.MONITOR_BASE_URI + response.id
                    restResponse.addHeader("Location", location)
                }
                return restResponse
            }
        }
    }
}
