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

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ACKNOWLEDGED
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ACTIVE
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.COMPLETED
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ERROR
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalTimeField
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.IOException
import java.time.Instant

private val log: Logger = LogManager.getLogger(RestAcknowledgeAlertAction::class.java)

/**
 * This class consists of the REST handler to acknowledge alerts.
 * The user provides the monitorID to which these alerts pertain and in the content of the request provides
 * the ids to the alerts he would like to acknowledge.
 */
class RestAcknowledgeAlertAction : BaseRestHandler() {

    override fun getName(): String {
        return "acknowledge_alert_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                // Acknowledge alerts
                Route(POST, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}/_acknowledge/alerts")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val monitorId = request.param("monitorID")
        require(!monitorId.isNullOrEmpty()) { "Missing monitor id." }
        val alertIds = getAlertIds(request.contentParser())
        require(alertIds.isNotEmpty()) { "You must provide at least one alert id." }
        val refreshPolicy = RefreshPolicy.parse(request.param(REFRESH, RefreshPolicy.IMMEDIATE.value))

        return RestChannelConsumer { channel ->
            AcknowledgeHandler(client, channel, monitorId, alertIds, refreshPolicy).start()
        }
    }

    inner class AcknowledgeHandler(
        client: NodeClient,
        channel: RestChannel,
        private val monitorId: String,
        private val alertIds: List<String>,
        private val refreshPolicy: WriteRequest.RefreshPolicy?
    ) : AsyncActionHandler(client, channel) {
        val alerts = mutableMapOf<String, Alert>()

        fun start() = findActiveAlerts()

        private fun findActiveAlerts() {
            val queryBuilder = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitorId))
                    .filter(QueryBuilders.termsQuery("_id", alertIds))
            val searchRequest = SearchRequest()
                    .indices(AlertIndices.ALERT_INDEX)
                    .routing(monitorId)
                    .source(SearchSourceBuilder().query(queryBuilder).version(true).seqNoAndPrimaryTerm(true))

            client.search(searchRequest, ActionListener.wrap(::onSearchResponse, ::onFailure))
        }

        private fun onSearchResponse(response: SearchResponse) {
            val updateRequests = response.hits.flatMap { hit ->
                val xcp = XContentHelper.createParser(channel.request().xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                        hit.sourceRef, XContentType.JSON)
                ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                val alert = Alert.parse(xcp, hit.id, hit.version)
                alerts[alert.id] = alert
                if (alert.state == ACTIVE) {
                    listOf(UpdateRequest(AlertIndices.ALERT_INDEX, hit.id)
                            .routing(monitorId)
                            .setIfSeqNo(hit.seqNo)
                            .setIfPrimaryTerm(hit.primaryTerm)
                            .doc(XContentFactory.jsonBuilder().startObject()
                                    .field(Alert.STATE_FIELD, ACKNOWLEDGED.toString())
                                    .optionalTimeField(Alert.ACKNOWLEDGED_TIME_FIELD, Instant.now())
                                    .endObject()))
                } else {
                    emptyList()
                }
            }

            log.info("Acknowledging monitor: $monitorId, alerts: ${updateRequests.map { it.id() }}")
            val request = BulkRequest().add(updateRequests).setRefreshPolicy(refreshPolicy)
            client.bulk(request, ActionListener.wrap(::onBulkResponse, ::onFailure))
        }

        private fun onBulkResponse(response: BulkResponse) {
            val missing = alertIds.toMutableSet()
            val acknowledged = mutableListOf<Alert>()
            val failed = mutableListOf<Alert>()
            // First handle all alerts that aren't currently ACTIVE. These can't be acknowledged.
            alerts.values.forEach {
                if (it.state != ACTIVE) {
                    missing.remove(it.id)
                    failed.add(it)
                }
            }
            // Now handle all alerts we tried to acknowledge...
            response.items.forEach { item ->
                missing.remove(item.id)
                if (item.isFailed) {
                    failed.add(alerts[item.id]!!)
                } else {
                    acknowledged.add(alerts[item.id]!!)
                }
            }

            channel.sendResponse(BytesRestResponse(RestStatus.OK,
                    responseBuilder(channel.newBuilder(), acknowledged.toList(), failed.toList(), missing.toList())))
        }
    }

    /**
     * Parse the request content and return a list of the alert ids to acknowledge
     */
    private fun getAlertIds(xcp: XContentParser): List<String> {
        val ids = mutableListOf<String>()
        ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
            val fieldName = xcp.currentName()
            xcp.nextToken()
            when (fieldName) {
                "alerts" -> {
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                    while (xcp.nextToken() != XContentParser.Token.END_ARRAY) {
                        ids.add(xcp.text())
                    }
                }
            }
        }
        return ids
    }

    /**
     * Build the response containing the acknowledged alerts and the failed to acknowledge alerts.
     */
    private fun responseBuilder(
        builder: XContentBuilder,
        acknowledgedAlerts: List<Alert>,
        failedAlerts: List<Alert>,
        missing: List<String>
    ): XContentBuilder {
        builder.startObject().startArray("success")
        acknowledgedAlerts.forEach { builder.value(it.id) }
        builder.endArray().startArray("failed")
        failedAlerts.forEach { buildFailedAlertAcknowledgeObject(builder, it) }
        missing.forEach { buildMissingAlertAcknowledgeObject(builder, it) }
        return builder.endArray().endObject()
    }

    private fun buildFailedAlertAcknowledgeObject(builder: XContentBuilder, failedAlert: Alert) {
        builder.startObject()
                .startObject(failedAlert.id)
        val reason = when (failedAlert.state) {
            ERROR -> "Alert is in an error state and can not be acknowledged."
            COMPLETED -> "Alert has already completed and can not be acknowledged."
            ACKNOWLEDGED -> "Alert has already been acknowledged."
            else -> "Alert state unknown and can not be acknowledged"
        }
        builder.field("failed_reason", reason)
                .endObject()
                .endObject()
    }

    private fun buildMissingAlertAcknowledgeObject(builder: XContentBuilder, alertID: String) {
        builder.startObject()
                .startObject(alertID)
                .field("failed_reason", "Alert: $alertID does not exist (it may have already completed).")
                .endObject()
                .endObject()
    }
}
