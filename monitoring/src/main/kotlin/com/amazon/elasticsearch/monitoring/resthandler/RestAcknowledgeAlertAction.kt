package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.Settings.REQUEST_TIMEOUT
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.util.REFRESH
import com.amazon.elasticsearch.util.ElasticAPI
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.IOException
import java.time.Instant

/**
 * This class consists of the REST handler to acknowledge alerts.
 * The user provides the monitorID to which these alerts pertain and in the content of the request provides
 * the ids to the alerts he would like to acknowledge.
 */
class RestAcknowledgeAlertAction(settings: Settings, controller: RestController) : BaseRestHandler(settings) {

    init {
        // Acknowledge alerts
        controller.registerHandler(POST, "/_awses/monitors/{monitorID}/_acknowledge/alerts", this)
    }

    override fun getName(): String {
        return "acknowledge_alert_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val monitorId = request.param("monitorID")
        if (monitorId.isNullOrEmpty()) {
            throw IllegalArgumentException("You must provide a monitor id.")
        }
        var alertIds = getAlertIds(request.xContentRegistry, request.content())
        if (alertIds.isEmpty()) {
            throw IllegalArgumentException("You must provide at least one alert id.")
        }
        logger.info("Acknowledging monitor: $monitorId, alerts: $alertIds")
        var queryBuilder = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitorId))
                .filter(QueryBuilders.termsQuery("_id", alertIds))
        var searchRequest = SearchRequest()
                .indices(AlertIndices.ALL_INDEX_PATTERN)
                .types(Alert.ALERT_TYPE)
                .routing(monitorId)
                .source(SearchSourceBuilder().query(queryBuilder).timeout(REQUEST_TIMEOUT.get(settings)))
        val refreshPolicy = if (request.hasParam(REFRESH)) request.param(REFRESH) else null
        return RestChannelConsumer { channel -> client.search(searchRequest, searchAlertResponse(channel, client, alertIds, refreshPolicy)) }
    }

    /**
     * Async response to the search request. Return a list of the acknowledged alerts.
     */
    private fun searchAlertResponse(channel: RestChannel, client: NodeClient, alertIds: List<String>, refreshPolicy: String? = null): RestResponseListener<SearchResponse> {
        return object: RestResponseListener<SearchResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: SearchResponse): RestResponse {
                if (response.isTimedOut) {
                    return BytesRestResponse(RestStatus.REQUEST_TIMEOUT, response.toString())
                }
                var acknowledgedAlerts = mutableListOf<Alert>()
                var failedAlerts = mutableListOf<Alert>()
                for (hit in response.hits) {
                    var xcp = ElasticAPI.INSTANCE.jsonParser(channel.request().xContentRegistry, hit.sourceRef)
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                    val alert = Alert.parse(xcp, hit.id, hit.version)
                    if (alert.state == Alert.State.ACTIVE) {
                        val acknowledgedAlert = alert.copy(state = Alert.State.ACKNOWLEDGED, acknowledgedTime = Instant.now())
                        val indexRequest = IndexRequest(AlertIndices.ALERT_INDEX, Alert.ALERT_TYPE, hit.id)
                                .source(acknowledgedAlert.toXContent(channel.newBuilder(), ToXContent.EMPTY_PARAMS))
                                .routing(acknowledgedAlert.monitorId)
                        refreshPolicy?.let { indexRequest.setRefreshPolicy(it) }
                        val indexResponse = client.index(indexRequest).actionGet(REQUEST_TIMEOUT.get(settings))
                        if (indexResponse.status() != RestStatus.OK) {
                            failedAlerts.add(alert)
                        } else {
                            acknowledgedAlerts.add(alert)
                        }
                    } else {
                        failedAlerts.add(alert)
                    }
                }
                var acknowledgedIds = mutableListOf<String>()
                acknowledgedAlerts.forEach { acknowledgedIds.add(it.id) }
                var failedIds = mutableListOf<String>()
                failedAlerts.forEach { failedIds.add(it.id) }
                var notFound = alertIds.minus(acknowledgedIds).minus(failedIds)
                return BytesRestResponse(RestStatus.OK, responseBuilder(channel.newBuilder(), acknowledgedAlerts, failedAlerts, notFound))
            }
        }
    }

    /**
     * Parse the [contentRef] provided by the request content and return a list of the alert ids
     */
    private fun getAlertIds(registry: NamedXContentRegistry ,contentRef: BytesReference): List<String> {
        var ids = mutableListOf<String>()
        var xcp = ElasticAPI.INSTANCE.jsonParser(registry, contentRef)
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
    private fun responseBuilder(builder: XContentBuilder, acknowledgedAlerts: List<Alert>, failedAlerts: List<Alert>, missing: List<String>): XContentBuilder {
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
        var reason = ""
        when (failedAlert.state) {
            Alert.State.ERROR -> { reason += "Alert is in an error state and can not be acknowledged." }
            Alert.State.COMPLETED -> { reason += "Alert has already completed and can not be acknowledged." }
            Alert.State.ACKNOWLEDGED -> { reason += "Alert has already been acknowledged." }
            else -> { reason += "Alert state unknown and can not be acknowledged" }
        }
        builder.field("failed_reason", reason)
                .endObject()
                .endObject()
    }

    private fun buildMissingAlertAcknowledgeObject(builder: XContentBuilder, alertID: String) {
        builder.startObject()
                .startObject(alertID)
                .field("failed_reason", "Alert does not exist.")
                .endObject()
                .endObject()
    }
}
