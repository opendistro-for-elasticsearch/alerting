/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.action.RestResponseListener

import java.io.IOException

import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.retry
import org.elasticsearch.ExceptionsHelper
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestRequest.Method.DELETE
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.threadpool.ThreadPool
import java.util.concurrent.Callable

/**
 * This class consists of the REST handler to delete monitors.
 * If a monitor is being deleted, all alerts are moved to the [Alert.State.DELETED] state and moved to the alert history index.
 */
class RestDeleteMonitorAction(settings: Settings, controller: RestController, val threadPool: ThreadPool, val alertIndices: AlertIndices) : BaseRestHandler(settings) {

    init {
        controller.registerHandler(DELETE, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this) // Delete a monitor
    }
    private val RETRY_POLICY = BackoffPolicy.constantBackoff(TimeValue.timeValueMillis(50), 2)
    private val BULK_TIMEOUT = Setting.positiveTimeSetting("aes.monitoring.bulk_timeout", TimeValue.timeValueSeconds(120))
    private val DELETE_TIMEOUT = Setting.positiveTimeSetting("aes.monitoring.request_timeout", TimeValue.timeValueSeconds(120))

    override fun getName(): String {
        return "delete_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val monitorId = request.param("monitorID")
        if (monitorId.isNullOrEmpty()) {
            throw IllegalArgumentException("missing monitor id to delete")
        }

        threadPool.generic().submit(moveAlerts(client, monitorId)).get()
        return RestChannelConsumer { channel -> client.delete(
                DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE, monitorId),
                deleteMonitorResponse(channel)) }
    }

    private fun moveAlerts(client: NodeClient, monitorId: String): Callable<Unit> {
        return Callable {
            val activeAlertsQuery = SearchSourceBuilder.searchSource()
                    .query(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitorId))
            val activeAlertsRequest = SearchRequest(AlertIndices.ALERT_INDEX)
                    .routing(monitorId)
                    .source(activeAlertsQuery)
            val searchResponse: SearchResponse = try {
                client.search(activeAlertsRequest).actionGet(DELETE_TIMEOUT.get(settings))
            } catch (e: IndexNotFoundException) {
                return@Callable
            }
            var requestsToRetry = searchResponse.hits.flatMap { hit ->
                listOf<DocWriteRequest<*>>(
                        DeleteRequest(AlertIndices.ALERT_INDEX, AlertIndices.MAPPING_TYPE, hit.id).routing(monitorId),
                        IndexRequest(AlertIndices.HISTORY_WRITE_INDEX, AlertIndices.MAPPING_TYPE)
                                .routing(monitorId)
                                .source(Alert.parse(alertContentParser(hit.sourceRef), hit.id, hit.version).copy(state = Alert.State.DELETED)
                                        .toXContent(jsonBuilder(), ToXContent.EMPTY_PARAMS))
                                .id(hit.id))
            }
            if (requestsToRetry.isNotEmpty()) {
                var successfulResponses = mutableListOf<BulkItemResponse>()
                var failedResponses: List<BulkItemResponse>
                var bulkRequest = BulkRequest().add(requestsToRetry).setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL)

                RETRY_POLICY.retry {
                    RETRY_POLICY.iterator().forEach { delay ->
                        val responses = client.bulk(bulkRequest).actionGet(BULK_TIMEOUT.get(settings)).items
                                ?: arrayOf()
                        successfulResponses.addAll(responses.filterNot { it.isFailed })
                        failedResponses = responses.filter { it.isFailed }

                        requestsToRetry = failedResponses
                                // retry only if this is a EsRejectedExecutionException (i.e. 429 TOO MANY REQUESTs)
                                .filter { ExceptionsHelper.unwrapCause(it.failure.cause) is EsRejectedExecutionException }
                                .map {
                                    when (bulkRequest.requests()[it.itemId]) {
                                        is DeleteRequest -> bulkRequest.requests()[it.itemId] as DocWriteRequest<DeleteRequest>
                                        is IndexRequest -> bulkRequest.requests()[it.itemId] as DocWriteRequest<IndexRequest>
                                        else -> throw IllegalArgumentException("Illegal request found.")
                                    }
                                }

                        bulkRequest = BulkRequest().add(requestsToRetry).setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL)
                        if (requestsToRetry.isEmpty()) {
                            return@retry
                        } else {
                            Thread.sleep(delay.millis)
                        }
                    }
                }
            }
        }
    }

    private fun deleteMonitorResponse(channel: RestChannel): RestResponseListener<DeleteResponse> {
        return object : RestResponseListener<DeleteResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: DeleteResponse): RestResponse {
                return BytesRestResponse(response.status(), channel.newBuilder())
            }
        }
    }

    private fun alertContentParser(bytesReference: BytesReference): XContentParser {
        var xcp = ElasticAPI.INSTANCE.jsonParser(NamedXContentRegistry.EMPTY, bytesReference)
        ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        return xcp
    }

}

