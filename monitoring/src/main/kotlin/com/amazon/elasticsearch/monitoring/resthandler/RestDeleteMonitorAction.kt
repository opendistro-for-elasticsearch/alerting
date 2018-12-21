/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.util.REFRESH
import com.amazon.elasticsearch.util.ElasticAPI
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.index.VersionType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.DELETE
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestStatusToXContentListener
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.IOException

/**
 * This class consists of the REST handler to delete monitors.
 * When a monitor is deleted, all alerts are moved to the [Alert.State.DELETED] state and moved to the alert history index.
 * If this process fails the monitor is not deleted.
 */
class RestDeleteMonitorAction(settings: Settings, controller: RestController, private val alertIndices: AlertIndices) :
        BaseRestHandler(settings) {

    init {
        controller.registerHandler(DELETE, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this) // Delete a monitor
    }

    override fun getName(): String {
        return "delete_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val monitorId = request.param("monitorID")
        val refreshPolicy = RefreshPolicy.parse(request.param(REFRESH, RefreshPolicy.IMMEDIATE.value))

        return RestChannelConsumer { channel ->
            if (alertIndices.isInitialized()) {
                MoveAlertsHandler(client, channel, monitorId, refreshPolicy).start()
            } else {
                val deleteMonitorRequest =
                        DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE, monitorId)
                                .setRefreshPolicy(refreshPolicy)
                client.delete(deleteMonitorRequest, RestStatusToXContentListener(channel))
            }
        }
    }

    inner class MoveAlertsHandler(client: NodeClient, channel: RestChannel, private val monitorId: String,
                                  private val refreshPolicy: WriteRequest.RefreshPolicy) :
            AsyncActionHandler(client, channel) {

        @Volatile private var failureMessage: String? = null
        @Volatile private var failureStatus : RestStatus? = null

        fun start() = findActiveAlerts()

        // Can't use the reindex API here since it doesn't return the IDs of successfully moved documents. Without IDs
        // we run a small risk of deleting alerts that haven't been moved to history.
        private fun findActiveAlerts() {
            val activeAlertsQuery = SearchSourceBuilder.searchSource()
                    .query(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitorId))
                    .version(true)
            val activeAlertsRequest = SearchRequest(AlertIndices.ALERT_INDEX)
                    .routing(monitorId)
                    .source(activeAlertsQuery)

            client.search(activeAlertsRequest, ActionListener.wrap(::onSearchResponse, ::onFailure))
        }

        private fun onSearchResponse(response: SearchResponse) {
            val indexRequests = response.hits.map { hit ->
                IndexRequest(AlertIndices.HISTORY_WRITE_INDEX, AlertIndices.MAPPING_TYPE)
                        .routing(monitorId)
                        .source(Alert.parse(alertContentParser(hit.sourceRef), hit.id, hit.version)
                                .copy(state = Alert.State.DELETED)
                                .toXContent(jsonBuilder(), ToXContent.EMPTY_PARAMS))
                        .version(hit.version)
                        .versionType(VersionType.EXTERNAL)
                        .id(hit.id)
            }

            val copyRequest = BulkRequest().add(indexRequests)
            client.bulk(copyRequest, ActionListener.wrap(::onCopyResponse, ::onFailure))
        }

        private fun onCopyResponse(response: BulkResponse) {
            val deleteRequests = response.items.filterNot { it.isFailed }.map {
                DeleteRequest(AlertIndices.ALERT_INDEX, AlertIndices.MAPPING_TYPE, it.id)
                        .routing(monitorId)
                        .version(it.version)
            }
            if (response.hasFailures()) {
                failureMessage = response.buildFailureMessage()
                failureStatus = response.items.find { it.isFailed }!!.status()
            }

            val bulkRequest = BulkRequest().add(deleteRequests)
            client.bulk(bulkRequest, ActionListener.wrap(::onDeleteResponse, ::onFailure))
        }

        private fun onDeleteResponse(response: BulkResponse) {
            if (response.hasFailures()) {
                failureMessage = (failureMessage ?: "") + "\n" + response.buildFailureMessage()
                failureStatus = failureStatus ?: response.items.find { it.isFailed }!!.status()
            }
            if (failureMessage != null) {
                channel.sendResponse(BytesRestResponse(failureStatus, failureMessage))
                return
            }

            val deleteRequest = DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE, monitorId)
                    .setRefreshPolicy(refreshPolicy)
            client.delete(deleteRequest, RestStatusToXContentListener(channel))
        }

        private fun alertContentParser(bytesReference: BytesReference): XContentParser {
            val xcp = ElasticAPI.INSTANCE.jsonParser(NamedXContentRegistry.EMPTY, bytesReference)
            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            return xcp
        }
    }
}