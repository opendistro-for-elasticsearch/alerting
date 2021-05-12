/*
 *   Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertError
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.firstFailureOrNull
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.retry
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.suspendUntil
import com.amazon.opendistroforelasticsearch.alerting.model.ActionExecutionResult
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.alerting.model.TriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.script.TraditionalTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ExceptionsHelper
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.time.Instant

/** Service that handles CRUD operations for alerts */
class AlertService(
    val client: Client,
    val xContentRegistry: NamedXContentRegistry,
    val alertIndices: AlertIndices
) {

    private val logger = LogManager.getLogger(AlertService::class.java)

    suspend fun loadCurrentAlerts(monitor: Monitor): Map<Trigger, Alert?> {
        val request = SearchRequest(AlertIndices.ALERT_INDEX)
            .routing(monitor.id)
            .source(alertQuery(monitor))
        val response: SearchResponse = client.suspendUntil { client.search(request, it) }
        if (response.status() != RestStatus.OK) {
            throw (response.firstFailureOrNull()?.cause ?: RuntimeException("Unknown error loading alerts"))
        }

        val foundAlerts = response.hits.map { Alert.parse(contentParser(it.sourceRef), it.id, it.version) }
            .groupBy { it.triggerId }
        foundAlerts.values.forEach { alerts ->
            if (alerts.size > 1) {
                logger.warn("Found multiple alerts for same trigger: $alerts")
            }
        }

        return monitor.triggers.associate { trigger ->
            trigger to (foundAlerts[trigger.id]?.firstOrNull())
        }
    }

    // TODO: For now this is largely a duplicate of the regular loadCurrentAlerts()
    //  The original method could be refactored to support Set if this is the final usage
    suspend fun loadCurrentAlertsForAggregationMonitor(monitor: Monitor): Map<Trigger, Set<Alert>?> {
        val request = SearchRequest(AlertIndices.ALERT_INDEX)
            .routing(monitor.id)
            .source(alertQuery(monitor))
        val response: SearchResponse = client.suspendUntil { client.search(request, it) }
        if (response.status() != RestStatus.OK) {
            throw (response.firstFailureOrNull()?.cause ?: RuntimeException("Unknown error loading alerts"))
        }

        val foundAlerts = response.hits.map { Alert.parse(contentParser(it.sourceRef), it.id, it.version) }
            .groupBy { it.triggerId }

        return monitor.triggers.associate { trigger ->
            trigger to (foundAlerts[trigger.id]?.toSet())
        }
    }

    fun composeTraditionalAlert(ctx: TraditionalTriggerExecutionContext, result: TraditionalTriggerRunResult, alertError: AlertError?): Alert? {
        val currentTime = Instant.now()
        val currentAlert = ctx.alert

        val updatedActionExecutionResults = mutableListOf<ActionExecutionResult>()
        val currentActionIds = mutableSetOf<String>()
        if (currentAlert != null) {
            // update current alert's action execution results
            for (actionExecutionResult in currentAlert.actionExecutionResults) {
                val actionId = actionExecutionResult.actionId
                currentActionIds.add(actionId)
                val actionRunResult = result.actionResults[actionId]
                when {
                    actionRunResult == null -> updatedActionExecutionResults.add(actionExecutionResult)
                    actionRunResult.throttled ->
                        updatedActionExecutionResults.add(actionExecutionResult.copy(
                            throttledCount = actionExecutionResult.throttledCount + 1))
                    else -> updatedActionExecutionResults.add(actionExecutionResult.copy(lastExecutionTime = actionRunResult.executionTime))
                }
            }
            // add action execution results which not exist in current alert
            updatedActionExecutionResults.addAll(result.actionResults.filter { it -> !currentActionIds.contains(it.key) }
                .map { it -> ActionExecutionResult(it.key, it.value.executionTime, if (it.value.throttled) 1 else 0) })
        } else {
            updatedActionExecutionResults.addAll(result.actionResults.map { it -> ActionExecutionResult(it.key, it.value.executionTime,
                if (it.value.throttled) 1 else 0) })
        }

        // Merge the alert's error message to the current alert's history
        val updatedHistory = currentAlert?.errorHistory.update(alertError)
        return if (alertError == null && !result.triggered) {
            currentAlert?.copy(state = Alert.State.COMPLETED, endTime = currentTime, errorMessage = null,
                errorHistory = updatedHistory, actionExecutionResults = updatedActionExecutionResults,
                schemaVersion = IndexUtils.alertIndexSchemaVersion)
        } else if (alertError == null && currentAlert?.isAcknowledged() == true) {
            null
        } else if (currentAlert != null) {
            val alertState = if (alertError == null) Alert.State.ACTIVE else Alert.State.ERROR
            currentAlert.copy(state = alertState, lastNotificationTime = currentTime, errorMessage = alertError?.message,
                errorHistory = updatedHistory, actionExecutionResults = updatedActionExecutionResults,
                schemaVersion = IndexUtils.alertIndexSchemaVersion)
        } else {
            val alertState = if (alertError == null) Alert.State.ACTIVE else Alert.State.ERROR
            Alert(monitor = ctx.monitor, trigger = ctx.trigger, startTime = currentTime,
                lastNotificationTime = currentTime, state = alertState, errorMessage = alertError?.message,
                errorHistory = updatedHistory, actionExecutionResults = updatedActionExecutionResults,
                schemaVersion = IndexUtils.alertIndexSchemaVersion)
        }
    }

    suspend fun saveAlerts(alerts: List<Alert>, retryPolicy: BackoffPolicy) {
        var requestsToRetry = alerts.flatMap { alert ->
            // We don't want to set the version when saving alerts because the MonitorRunner has first priority when writing alerts.
            // In the rare event that a user acknowledges an alert between when it's read and when it's written
            // back we're ok if that acknowledgement is lost. It's easier to get the user to retry than for the runner to
            // spend time reloading the alert and writing it back.
            when (alert.state) {
                Alert.State.ACTIVE, Alert.State.ERROR -> {
                    listOf<DocWriteRequest<*>>(IndexRequest(AlertIndices.ALERT_INDEX)
                        .routing(alert.monitorId)
                        .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                        .id(if (alert.id != Alert.NO_ID) alert.id else null))
                }
                Alert.State.ACKNOWLEDGED, Alert.State.DELETED -> {
                    throw IllegalStateException("Unexpected attempt to save ${alert.state} alert: $alert")
                }
                Alert.State.COMPLETED -> {
                    listOfNotNull<DocWriteRequest<*>>(
                        DeleteRequest(AlertIndices.ALERT_INDEX, alert.id)
                            .routing(alert.monitorId),
                        // Only add completed alert to history index if history is enabled
                        if (alertIndices.isHistoryEnabled()) {
                            IndexRequest(AlertIndices.HISTORY_WRITE_INDEX)
                                .routing(alert.monitorId)
                                .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                                .id(alert.id)
                        } else null
                    )
                }
            }
        }

        if (requestsToRetry.isEmpty()) return
        // Retry Bulk requests if there was any 429 response
        retryPolicy.retry(logger, listOf(RestStatus.TOO_MANY_REQUESTS)) {
            val bulkRequest = BulkRequest().add(requestsToRetry)
            val bulkResponse: BulkResponse = client.suspendUntil { client.bulk(bulkRequest, it) }
            val failedResponses = (bulkResponse.items ?: arrayOf()).filter { it.isFailed }
            requestsToRetry = failedResponses.filter { it.status() == RestStatus.TOO_MANY_REQUESTS }
                .map { bulkRequest.requests()[it.itemId] as IndexRequest }

            if (requestsToRetry.isNotEmpty()) {
                val retryCause = failedResponses.first { it.status() == RestStatus.TOO_MANY_REQUESTS }.failure.cause
                throw ExceptionsHelper.convertToElastic(retryCause)
            }
        }
    }

    private fun contentParser(bytesReference: BytesReference): XContentParser {
        val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
            bytesReference, XContentType.JSON)
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp)
        return xcp
    }

    private fun alertQuery(monitor: Monitor): SearchSourceBuilder {
        return SearchSourceBuilder.searchSource()
            .size(monitor.triggers.size * 2) // We expect there to be only a single in-progress alert so fetch 2 to check
            .query(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitor.id))
    }

    private fun List<AlertError>?.update(alertError: AlertError?): List<AlertError> {
        return when {
            this == null && alertError == null -> emptyList()
            this != null && alertError == null -> this
            this == null && alertError != null -> listOf(alertError)
            this != null && alertError != null -> (listOf(alertError) + this).take(10)
            else -> throw IllegalStateException("Unreachable code reached!")
        }
    }
}
