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
package com.amazon.opendistro.alerting.resthandler

import com.amazon.opendistro.ScheduledJobIndices
import com.amazon.opendistro.Settings.REQUEST_TIMEOUT
import com.amazon.opendistro.model.ScheduledJob
import com.amazon.opendistro.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistro.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.opendistro.alerting.MonitoringPlugin
import com.amazon.opendistro.alerting.model.Monitor
import com.amazon.opendistro.alerting.util.REFRESH
import com.amazon.opendistro.alerting.util._ID
import com.amazon.opendistro.alerting.util._VERSION
import com.amazon.opendistro.util.ElasticAPI
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestRequest.Method.PUT
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException
import java.time.Instant

/**
 * Rest handlers to create and update monitors
 */
class RestIndexMonitorAction(settings: Settings, controller: RestController, jobIndices: ScheduledJobIndices) : BaseRestHandler(settings) {

    private var scheduledJobIndices: ScheduledJobIndices

    init {
        controller.registerHandler(POST, MonitoringPlugin.MONITOR_BASE_URI, this) // Create a new monitor
        controller.registerHandler(PUT, "${MonitoringPlugin.MONITOR_BASE_URI}/{monitorID}", this)
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
            if (!response.isExists) {
                val builder = channel.newErrorBuilder()
                        .startObject()

                        .field("Message", "Monitor with $monitorId is not found")
                        .endObject()
                return channel.sendResponse(BytesRestResponse(RestStatus.NOT_FOUND, response.toXContent(builder, EMPTY_PARAMS)))
            }
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
                        val location = "${MonitoringPlugin.MONITOR_BASE_URI}/${response.id}"
                        restResponse.addHeader("Location", location)
                    }
                    return restResponse
                }
            }
        }
    }
}
