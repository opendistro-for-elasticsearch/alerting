/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.ScheduledJobIndices
import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.Settings.REQUEST_TIMEOUT
import com.amazon.elasticsearch.model.ScheduledJob
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
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

import java.io.IOException
import java.time.Instant

import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.elasticsearch.monitoring.util.REFRESH
import com.amazon.elasticsearch.monitoring.util._ID
import com.amazon.elasticsearch.monitoring.util._VERSION
import com.amazon.elasticsearch.util.ElasticAPI
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestRequest.Method.PUT

/**
 * Rest handlers to create and update monitors
 */
class RestIndexMonitorAction(settings: Settings, controller: RestController, jobIndices: ScheduledJobIndices) : BaseRestHandler(settings) {

    private var scheduledJobIndices: ScheduledJobIndices

    init {
        controller.registerHandler(POST, MonitoringPlugin.MONITOR_BASE_URI, this) // Create a new monitor
        controller.registerHandler(PUT, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this)
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
        val monitorVersion = RestActions.parseVersion(request)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }
        return RestChannelConsumer { channel ->
            IndexMonitorHandler(client, channel, id, monitorVersion, refreshPolicy, monitor).start()
        }
    }

    inner class IndexMonitorHandler(client: NodeClient,
                                    channel: RestChannel,
                                    private val monitorId: String,
                                    private val monitorVersion: Long,
                                    private val refreshPolicy: WriteRequest.RefreshPolicy,
                                    private var newMonitor: Monitor) :
            AsyncActionHandler(client, channel) {

        fun start() {
            scheduledJobIndices.initScheduledJobIndex(ActionListener.wrap(::onCreateMappingsResponse, ::onCreateMappingsFailure))
            if (channel.request().method() == PUT) updateMonitor()
            else {
                val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
                        .setRefreshPolicy(refreshPolicy)
                        .source(newMonitor.toXContentWithType(channel.newBuilder()))
                        .version(monitorVersion)
                        .timeout(REQUEST_TIMEOUT.get(settings))
                client.index(indexRequest, indexMonitorResponse())
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                logger.info("Created ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
            } else {
                logger.error("Create ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
            }
        }

        private fun onCreateMappingsFailure(e: Exception) {
            if (e is ResourceAlreadyExistsException) {
                logger.info("$SCHEDULED_JOBS_INDEX already exists.")
            } else {
                logger.error("Error occurred while trying to create ${ScheduledJob.SCHEDULED_JOBS_INDEX}.", e)
                onFailure(e)
            }
        }

        private fun updateMonitor() {
            val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE, monitorId)
            client.get(getRequest, ActionListener.wrap(::onGetResponse, ::onFailure))
        }

        private fun onGetResponse(response: GetResponse) {
            val xcp = ElasticAPI.INSTANCE.jsonParser(channel.request().xContentRegistry, response.sourceAsBytesRef)
            val currentMonitor = ScheduledJob.parse(xcp, monitorId) as Monitor
            // If both are enabled, use the current existing monitor enabled time, otherwise the next execution will be
            // incorrect.
            if (newMonitor.enabled && currentMonitor.enabled) newMonitor = newMonitor.copy(enabledTime = currentMonitor.enabledTime)
            val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
                    .setRefreshPolicy(refreshPolicy)
                    .source(newMonitor.toXContentWithType(channel.newBuilder()))
                    .id(monitorId)
                    .version(monitorVersion)
                    .timeout(REQUEST_TIMEOUT.get(settings))
            return client.index(indexRequest, indexMonitorResponse())
        }

        private fun indexMonitorResponse(): RestResponseListener<IndexResponse> {
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
                            .field("monitor", newMonitor)
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
}
