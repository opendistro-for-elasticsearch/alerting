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
import com.amazon.opendistroforelasticsearch.alerting.model.ActionRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationResultBucket
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.alerting.model.action.AlertCategory
import com.amazon.opendistroforelasticsearch.alerting.script.TraditionalTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import com.amazon.opendistroforelasticsearch.alerting.util.getBucketKeysHash
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

    suspend fun loadCurrentAlertsForTraditionalMonitor(monitor: Monitor): Map<Trigger, Alert?> {
        val searchAlertsResponse: SearchResponse = searchAlerts(
            monitorId = monitor.id,
            size = monitor.triggers.size * 2 // We expect there to be only a single in-progress alert so fetch 2 to check
        )

        val foundAlerts = searchAlertsResponse.hits.map { Alert.parse(contentParser(it.sourceRef), it.id, it.version) }
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

    suspend fun loadCurrentAlertsForAggregationMonitor(monitor: Monitor): Map<Trigger, Map<String, Alert>?> {
        val searchAlertsResponse: SearchResponse = searchAlerts(
            monitorId = monitor.id,
            size = 500 // TODO: This should be a constant and limited based on the circuit breaker that limits Alerts
        )

        val foundAlerts = searchAlertsResponse.hits.map { Alert.parse(contentParser(it.sourceRef), it.id, it.version) }
            .groupBy { it.triggerId }

        return monitor.triggers.associateWith { trigger ->
            foundAlerts[trigger.id]?.mapNotNull { alert ->
                alert.aggregationResultBucket?.let { it.getBucketKeysHash() to alert }
            }?.toMap()
        }
    }

    fun composeTraditionalAlert(
        ctx: TraditionalTriggerExecutionContext,
        result: TraditionalTriggerRunResult,
        alertError: AlertError?
    ): Alert? {
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
            updatedActionExecutionResults.addAll(result.actionResults.filter { !currentActionIds.contains(it.key) }
                .map { ActionExecutionResult(it.key, it.value.executionTime, if (it.value.throttled) 1 else 0) })
        } else {
            updatedActionExecutionResults.addAll(result.actionResults.map {
                ActionExecutionResult(it.key, it.value.executionTime, if (it.value.throttled) 1 else 0) })
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

    fun updateActionResultsForAggregationAlert(
        currentAlert: Alert,
        actionResults: Map<String, ActionRunResult>,
        alertError: AlertError?
    ): Alert {
        val updatedActionExecutionResults = mutableListOf<ActionExecutionResult>()
        val currentActionIds = mutableSetOf<String>()
        // Update alert's existing action execution results
        for (actionExecutionResult in currentAlert.actionExecutionResults) {
            val actionId = actionExecutionResult.actionId
            currentActionIds.add(actionId)
            val actionRunResult = actionResults[actionId]
            when {
                actionRunResult == null -> updatedActionExecutionResults.add(actionExecutionResult)
                actionRunResult.throttled ->
                    updatedActionExecutionResults.add(actionExecutionResult.copy(
                        throttledCount = actionExecutionResult.throttledCount + 1))
                else -> updatedActionExecutionResults.add(actionExecutionResult.copy(lastExecutionTime = actionRunResult.executionTime))
            }
        }

        // Add action execution results not currently present in the alert
        updatedActionExecutionResults.addAll(
            actionResults.filter { !currentActionIds.contains(it.key) }
                .map { ActionExecutionResult(it.key, it.value.executionTime, if (it.value.throttled) 1 else 0) }
        )

        val updatedErrorHistory = currentAlert.errorHistory.update(alertError)
        return if (alertError == null) {
            currentAlert.copy(errorHistory = updatedErrorHistory, actionExecutionResults = updatedActionExecutionResults)
        } else {
            currentAlert.copy(
                state = Alert.State.ERROR,
                errorMessage = alertError.message,
                errorHistory = updatedErrorHistory,
                actionExecutionResults = updatedActionExecutionResults
            )
        }
    }

    // TODO: Can change the parameters to use ctx: AggregationTriggerExecutionContext instead of monitor/trigger and
    //  result: AggTriggerRunResult for aggResultBuckets
    // TODO: Can refactor this method to use Sets instead which can cleanup some of the categorization logic (like getting completed alerts)
    fun getCategorizedAlertsForAggregationMonitor(
        monitor: Monitor,
        trigger: AggregationTrigger,
        currentAlerts: Map<String, Alert>?,
        aggResultBuckets: List<AggregationResultBucket>
    ): Map<AlertCategory, List<Alert>> {
        val dedupedAlerts = mutableListOf<Alert>()
        val newAlerts = mutableListOf<Alert>()
        var completedAlerts = listOf<Alert>()
        val currentTime = Instant.now()

        // TODO: Need to update errorHistory and actionExecutionResults after Actions are run for these alerts (likely in MonitorRunner)
        aggResultBuckets.forEach { aggAlertBucket ->
            val currentAlert = currentAlerts?.get(aggAlertBucket.getBucketKeysHash())
            if (currentAlert != null) {
                // De-duped Alert
                dedupedAlerts.add(currentAlert.copy(lastNotificationTime = currentTime, aggregationResultBucket = aggAlertBucket))
            } else {
                // New Alert
                // TODO: Setting lastNotificationTime is deceiving since the actions haven't run yet, maybe it should be null here
                val newAlert = Alert(monitor = monitor, trigger = trigger, startTime = currentTime,
                    lastNotificationTime = currentTime, state = Alert.State.ACTIVE, errorMessage = null,
                    errorHistory = mutableListOf(), actionExecutionResults = mutableListOf(),
                    schemaVersion = IndexUtils.alertIndexSchemaVersion, aggregationResultBucket = aggAlertBucket)
                newAlerts.add(newAlert)
            }
        }

        // The completed alerts can be determined by getting the difference of the current alerts and the de-duped alerts
        if (currentAlerts != null) {
            // Creating a set of the deduped Alert bucketKeys hashes for easy checking against currentAlerts
            // These Alerts should always contain an aggregationResultBucket
            val dedupedAlertsKeys = dedupedAlerts.map { it.aggregationResultBucket!!.getBucketKeysHash() }.toSet()
            // Take all Alerts from currentAlerts that do not contain their bucketKeys hashes in dedupedAlerts to get the difference between
            // the two
            completedAlerts = currentAlerts.filterNot { dedupedAlertsKeys.contains(it.key) }
                .map {
                    // TODO: errorHistory and actionExecutionResults will need to be updated after Action execution when sending
                    //  messages on COMPLETED alerts is supported
                    it.value.copy(state = Alert.State.COMPLETED, endTime = currentTime, errorMessage = null,
                        schemaVersion = IndexUtils.alertIndexSchemaVersion)
                }
        }

        return mapOf(
            AlertCategory.DEDUPED to dedupedAlerts,
            AlertCategory.NEW to newAlerts,
            AlertCategory.COMPLETED to completedAlerts
        )
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

    /**
     * This is a separate method created specifically for saving new Alerts during the Aggregation Monitor run.
     * Alerts are saved in two batches during the execution of an Aggregation Monitor, once before the Actions are executed
     * and once afterwards. This method saves Alerts to the [AlertIndices.ALERT_INDEX] but returns the same Alerts with their document IDs.
     *
     * The Alerts are required with their indexed ID so that when the new Alerts are updated after the Action execution,
     * the ID is available for the index request so that the existing Alert can be updated, instead of creating a duplicate Alert document.
     */
    suspend fun saveNewAlerts(alerts: List<Alert>, retryPolicy: BackoffPolicy): List<Alert> {
        val savedAlerts = mutableListOf<Alert>()
        var alertsBeingIndexed = alerts
        var requestsToRetry: MutableList<IndexRequest> = alerts.map { alert ->
            if (alert.state != Alert.State.ACTIVE) {
                throw IllegalStateException("Unexpected attempt to save new alert [$alert] with state [${alert.state}]")
            }
            if (alert.id != Alert.NO_ID) {
                throw IllegalStateException("Unexpected attempt to save new alert [$alert] with an existing alert ID [${alert.id}]")
            }
            IndexRequest(AlertIndices.ALERT_INDEX)
                .routing(alert.monitorId)
                .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
        }.toMutableList()

        if (requestsToRetry.isEmpty()) return listOf()

        // Retry Bulk requests if there was any 429 response.
        // The responses of a bulk request will be in the same order as the individual requests.
        // If the index request succeeded for an Alert, the document ID from the response is taken and saved in the Alert.
        // If the index request is to be retried, the Alert is saved separately as well so that its relative ordering is maintained in
        // relation to index request in the retried bulk request for when it eventually succeeds.
        retryPolicy.retry(logger, listOf(RestStatus.TOO_MANY_REQUESTS)) {
            val bulkRequest = BulkRequest().add(requestsToRetry)
            val bulkResponse: BulkResponse = client.suspendUntil { client.bulk(bulkRequest, it) }
            // TODO: This is only used to retrieve the retryCause, could instead fetch it from the bulkResponse iteration below
            val failedResponses = (bulkResponse.items ?: arrayOf()).filter { it.isFailed }

            requestsToRetry = mutableListOf()
            val alertsBeingRetried = mutableListOf<Alert>()
            bulkResponse.items.forEach { item ->
                if (item.isFailed) {
                    // TODO: What if the failure cause was not TOO_MANY_REQUESTS, should these be saved and logged?
                    if (item.status() == RestStatus.TOO_MANY_REQUESTS) {
                        requestsToRetry.add(bulkRequest.requests()[item.itemId] as IndexRequest)
                        alertsBeingRetried.add(alertsBeingIndexed[item.itemId])
                    }
                } else {
                    // The ID of the BulkItemResponse in this case is the document ID resulting from the DocWriteRequest operation
                    savedAlerts.add(alertsBeingIndexed[item.itemId].copy(id = item.id))
                }
            }

            alertsBeingIndexed = alertsBeingRetried

            if (requestsToRetry.isNotEmpty()) {
                val retryCause = failedResponses.first { it.status() == RestStatus.TOO_MANY_REQUESTS }.failure.cause
                throw ExceptionsHelper.convertToElastic(retryCause)
            }
        }

        return savedAlerts
    }

    private fun contentParser(bytesReference: BytesReference): XContentParser {
        val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
            bytesReference, XContentType.JSON)
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp)
        return xcp
    }

    /**
     * Searches for Alerts in the [AlertIndices.ALERT_INDEX].
     *
     * @param monitorId The Monitor to get Alerts for
     * @param size The number of search hits (Alerts) to return
     */
    private suspend fun searchAlerts(monitorId: String, size: Int): SearchResponse {
        val queryBuilder = QueryBuilders.boolQuery()
            .filter(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitorId))

        val searchSourceBuilder = SearchSourceBuilder()
            .size(size)
            .query(queryBuilder)

        val searchRequest = SearchRequest(AlertIndices.ALERT_INDEX)
            .routing(monitorId)
            .source(searchSourceBuilder)
        val searchResponse: SearchResponse = client.suspendUntil { client.search(searchRequest, it) }
        if (searchResponse.status() != RestStatus.OK) {
            throw (searchResponse.firstFailureOrNull()?.cause ?: RuntimeException("Unknown error loading alerts"))
        }

        return searchResponse
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
