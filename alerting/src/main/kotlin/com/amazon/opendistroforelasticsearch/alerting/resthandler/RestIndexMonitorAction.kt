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
package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERTING_MAX_MONITORS
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.INDEX_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.REQUEST_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.alerting.util._ID
import com.amazon.opendistroforelasticsearch.alerting.util._VERSION
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.ElasticAPI
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.ResourceNotFoundException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.index.query.QueryBuilders
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
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.IOException
import java.time.Instant

/**
 * Rest handlers to create and update monitors.
 */
class RestIndexMonitorAction(
    settings: Settings,
    controller: RestController,
    jobIndices: ScheduledJobIndices,
    clusterService: ClusterService
) : BaseRestHandler(settings) {

    private var scheduledJobIndices: ScheduledJobIndices
    @Volatile private var maxMonitors = ALERTING_MAX_MONITORS.get(settings)
    @Volatile private var requestTimeout = REQUEST_TIMEOUT.get(settings)
    @Volatile private var indexTimeout = INDEX_TIMEOUT.get(settings)

    init {
        controller.registerHandler(POST, AlertingPlugin.MONITOR_BASE_URI, this) // Create a new monitor
        controller.registerHandler(PUT, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}", this)
        scheduledJobIndices = jobIndices

        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERTING_MAX_MONITORS) { maxMonitors = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(REQUEST_TIMEOUT) { requestTimeout = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(INDEX_TIMEOUT) { indexTimeout = it }
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

    inner class IndexMonitorHandler(
        client: NodeClient,
        channel: RestChannel,
        private val monitorId: String,
        private val monitorVersion: Long,
        private val refreshPolicy: WriteRequest.RefreshPolicy,
        private var newMonitor: Monitor
    ) : AsyncActionHandler(client, channel) {

        fun start() {
            scheduledJobIndices.initScheduledJobIndex(ActionListener.wrap(::onCreateMappingsResponse, ::onCreateMappingsFailure))
            if (channel.request().method() == PUT) updateMonitor()
            else prepareMonitorIndexing()
        }

        /**
         * This function prepares for indexing a new monitor.
         * We first check to see how many monitors already exist, if this number is larger than [maxMonitorCount] the request is rejected.
         */
        private fun prepareMonitorIndexing() {
            val query = QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("${Monitor.MONITOR_TYPE}.type", Monitor.MONITOR_TYPE))
            val searchSource = SearchSourceBuilder().query(query).timeout(requestTimeout)
            val searchRequest = SearchRequest(ScheduledJob.SCHEDULED_JOBS_INDEX)
                    .types(ScheduledJob.SCHEDULED_JOB_TYPE)
                    .source(searchSource)
            client.search(searchRequest, ActionListener.wrap(::onSearchResponse, ::onSearchFailure))
        }

        /**
         * After searching for all existing monitors we validate the system can support another monitor to be created.
         */
        private fun onSearchResponse(response: SearchResponse) {
            if (response.hits.totalHits >= maxMonitors) {
                logger.error("This request would create more than the allowed monitors [$maxMonitors].")
                onFailure(IllegalArgumentException("This request would create more than the allowed monitors [$maxMonitors]."))
            } else {
                indexMonitor()
            }
        }

        /**
         * If the [SCHEDULED_JOBS_INDEX] mappings have not been created yet we know that this is the first time we are indexing a monitor,
         * and that the index will be created shortly since it is an async request. In this case we simply retry to index the monitor.
         * Any other error is thrown back to the user.
         */
        private fun onSearchFailure(e: Exception) {
            if (e is ResourceNotFoundException) {
                logger.info("Scheduled job index does not exist yet. Retrying to index.")
                indexMonitor()
            } else {
                logger.error("Failed to search for existing monitors.", e)
                onFailure(e)
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                logger.info("Created ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
            } else {
                logger.error("Create ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
            }
        }

        /**
         * The system can fail to create the [SCHEDULED_JOBS_INDEX] and mappings if another node completed the request prior to this node.
         * Any other failures are returned back to the user.
         */
        private fun onCreateMappingsFailure(e: Exception) {
            if (e is ResourceAlreadyExistsException) {
                logger.info("$SCHEDULED_JOBS_INDEX already exists.")
            } else {
                logger.error("Error occurred while trying to create ${ScheduledJob.SCHEDULED_JOBS_INDEX}.", e)
                onFailure(e)
            }
        }

        private fun indexMonitor() {
            val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
                        .setRefreshPolicy(refreshPolicy)
                        .source(newMonitor.toXContentWithType(channel.newBuilder()))
                        .version(monitorVersion)
                        .timeout(indexTimeout)
            client.index(indexRequest, indexMonitorResponse())
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
                    .timeout(indexTimeout)
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
                        val location = "${AlertingPlugin.MONITOR_BASE_URI}/${response.id}"
                        restResponse.addHeader("Location", location)
                    }
                    return restResponse
                }
            }
        }
    }
}
