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
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERTING_MAX_MONITORS
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.INDEX_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.REQUEST_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.alerting.util._ID
import com.amazon.opendistroforelasticsearch.alerting.util._VERSION
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.MAX_ACTION_THROTTLE_VALUE
import com.amazon.opendistroforelasticsearch.alerting.util.IF_PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util.IF_SEQ_NO
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import com.amazon.opendistroforelasticsearch.alerting.util._PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util._SEQ_NO
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.seqno.SequenceNumbers
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
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.IOException
import java.time.Duration
import java.time.Instant

private val log = LogManager.getLogger(RestIndexMonitorAction::class.java)

/**
 * Rest handlers to create and update monitors.
 */
class RestIndexMonitorAction(
    settings: Settings,
    controller: RestController,
    jobIndices: ScheduledJobIndices,
    clusterService: ClusterService
) : BaseRestHandler() {

    private var scheduledJobIndices: ScheduledJobIndices
    private val clusterService: ClusterService
    @Volatile private var maxMonitors = ALERTING_MAX_MONITORS.get(settings)
    @Volatile private var requestTimeout = REQUEST_TIMEOUT.get(settings)
    @Volatile private var indexTimeout = INDEX_TIMEOUT.get(settings)
    @Volatile private var maxActionThrottle = MAX_ACTION_THROTTLE_VALUE.get(settings)

    init {
        controller.registerHandler(POST, AlertingPlugin.MONITOR_BASE_URI, this) // Create a new monitor
        controller.registerHandler(PUT, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}", this)
        scheduledJobIndices = jobIndices

        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERTING_MAX_MONITORS) { maxMonitors = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(REQUEST_TIMEOUT) { requestTimeout = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(INDEX_TIMEOUT) { indexTimeout = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(MAX_ACTION_THROTTLE_VALUE) { maxActionThrottle = it }
        this.clusterService = clusterService
    }

    override fun getName(): String {
        return "index_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val id = request.param("monitorID", Monitor.NO_ID)
        if (request.method() == PUT && Monitor.NO_ID == id) {
            throw IllegalArgumentException("Missing monitor ID")
        }

        // Validate request by parsing JSON to Monitor
        val xcp = request.contentParser()
        ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        val monitor = Monitor.parse(xcp, id).copy(lastUpdateTime = Instant.now())
        val seqNo = request.paramAsLong(IF_SEQ_NO, SequenceNumbers.UNASSIGNED_SEQ_NO)
        val primaryTerm = request.paramAsLong(IF_PRIMARY_TERM, SequenceNumbers.UNASSIGNED_PRIMARY_TERM)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }
        return RestChannelConsumer { channel ->
            IndexMonitorHandler(client, channel, id, seqNo, primaryTerm, refreshPolicy, monitor).start()
        }
    }

    inner class IndexMonitorHandler(
        client: NodeClient,
        channel: RestChannel,
        private val monitorId: String,
        private val seqNo: Long,
        private val primaryTerm: Long,
        private val refreshPolicy: WriteRequest.RefreshPolicy,
        private var newMonitor: Monitor
    ) : AsyncActionHandler(client, channel) {

        fun start() {
            if (!scheduledJobIndices.scheduledJobIndexExists()) {
                scheduledJobIndices.initScheduledJobIndex(ActionListener.wrap(::onCreateMappingsResponse, ::onFailure))
            } else {
                if (!IndexUtils.scheduledJobIndexUpdated) {
                    IndexUtils.updateIndexMapping(SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE,
                            ScheduledJobIndices.scheduledJobMappings(), clusterService.state(), client.admin().indices(),
                            ActionListener.wrap(::onUpdateMappingsResponse, ::onFailure))
                } else {
                    prepareMonitorIndexing()
                }
            }
        }

        /**
         * This function prepares for indexing a new monitor.
         * If this is an update request we can simply update the monitor. Otherwise we first check to see how many monitors already exist,
         * and compare this to the [maxMonitorCount]. Requests that breach this threshold will be rejected.
         */
        private fun prepareMonitorIndexing() {
            validateActionThrottle(newMonitor, maxActionThrottle, TimeValue.timeValueMinutes(1))
            if (channel.request().method() == PUT) return updateMonitor()
            val query = QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("${Monitor.MONITOR_TYPE}.type", Monitor.MONITOR_TYPE))
            val searchSource = SearchSourceBuilder().query(query).timeout(requestTimeout)
            val searchRequest = SearchRequest(SCHEDULED_JOBS_INDEX)
                    .source(searchSource)
            client.search(searchRequest, ActionListener.wrap(::onSearchResponse, ::onFailure))
        }

        private fun validateActionThrottle(monitor: Monitor, maxValue: TimeValue, minValue: TimeValue) {
            monitor.triggers.forEach { trigger ->
                trigger.actions.forEach { action ->
                    if (action.throttle != null) {
                        require(TimeValue(Duration.of(action.throttle.value.toLong(), action.throttle.unit).toMillis())
                                .compareTo(maxValue) <= 0, { "Can only set throttle period less than or equal to $maxValue" })
                        require(TimeValue(Duration.of(action.throttle.value.toLong(), action.throttle.unit).toMillis())
                                .compareTo(minValue) >= 0, { "Can only set throttle period greater than or equal to $minValue" })
                    }
                }
            }
        }

        /**
         * After searching for all existing monitors we validate the system can support another monitor to be created.
         */
        private fun onSearchResponse(response: SearchResponse) {
            val totalHits = response.hits.totalHits?.value
            if (totalHits != null && totalHits >= maxMonitors) {
                log.error("This request would create more than the allowed monitors [$maxMonitors].")
                onFailure(IllegalArgumentException("This request would create more than the allowed monitors [$maxMonitors]."))
            } else {
                indexMonitor()
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                log.info("Created ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
                prepareMonitorIndexing()
                IndexUtils.scheduledJobIndexUpdated()
            } else {
                log.error("Create ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                channel.sendResponse(BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR,
                        response.toXContent(channel.newErrorBuilder(), EMPTY_PARAMS)))
            }
        }

        private fun onUpdateMappingsResponse(response: AcknowledgedResponse) {
            if (response.isAcknowledged) {
                log.info("Updated ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
                IndexUtils.scheduledJobIndexUpdated()
                prepareMonitorIndexing()
            } else {
                log.error("Updated ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                channel.sendResponse(BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR,
                        response.toXContent(channel.newErrorBuilder().startObject()
                                .field("message", "Updated ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                                .endObject(), EMPTY_PARAMS)))
            }
        }

        private fun indexMonitor() {
            newMonitor = newMonitor.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
            val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                        .setRefreshPolicy(refreshPolicy)
                        .source(newMonitor.toXContentWithType(channel.newBuilder()))
                        .setIfSeqNo(seqNo)
                        .setIfPrimaryTerm(primaryTerm)
                        .timeout(indexTimeout)
            client.index(indexRequest, indexMonitorResponse())
        }

        private fun updateMonitor() {
            val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, monitorId)
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
            val xcp = XContentHelper.createParser(channel.request().xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                    response.sourceAsBytesRef, XContentType.JSON)
            val currentMonitor = ScheduledJob.parse(xcp, monitorId) as Monitor
            // If both are enabled, use the current existing monitor enabled time, otherwise the next execution will be
            // incorrect.
            if (newMonitor.enabled && currentMonitor.enabled) newMonitor = newMonitor.copy(enabledTime = currentMonitor.enabledTime)
            newMonitor = newMonitor.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
            val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                    .setRefreshPolicy(refreshPolicy)
                    .source(newMonitor.toXContentWithType(channel.newBuilder()))
                    .id(monitorId)
                    .setIfSeqNo(seqNo)
                    .setIfPrimaryTerm(primaryTerm)
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
                            .field(_SEQ_NO, response.seqNo)
                            .field(_PRIMARY_TERM, response.primaryTerm)
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
