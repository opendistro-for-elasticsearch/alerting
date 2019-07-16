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

package com.amazon.opendistroforelasticsearch.alerting

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertError
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.alerts.moveAlerts
import com.amazon.opendistroforelasticsearch.alerting.client.HttpInputClient
import com.amazon.opendistroforelasticsearch.alerting.core.JobRunner
import com.amazon.opendistroforelasticsearch.alerting.core.httpapi.suspendUntil
import com.amazon.opendistroforelasticsearch.alerting.core.httpapi.toGetRequest
import com.amazon.opendistroforelasticsearch.alerting.core.httpapi.toMap
import com.amazon.opendistroforelasticsearch.alerting.core.model.HttpInput
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.convertToMap
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.firstFailureOrNull
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.retry
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.suspendUntil
import com.amazon.opendistroforelasticsearch.alerting.model.ActionExecutionResult
import com.amazon.opendistroforelasticsearch.alerting.model.ActionRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ACKNOWLEDGED
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ACTIVE
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.COMPLETED
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.DELETED
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ERROR
import com.amazon.opendistroforelasticsearch.alerting.model.InputRunResults
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.MonitorRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.alerting.model.TriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action.Companion.MESSAGE
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action.Companion.MESSAGE_ID
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action.Companion.SUBJECT
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.script.TriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.script.TriggerScript
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_BACKOFF_COUNT
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_BACKOFF_MILLIS
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.MOVE_ALERTS_BACKOFF_COUNT
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.MOVE_ALERTS_BACKOFF_MILLIS
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.http.HttpResponse
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ExceptionsHelper
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.Strings
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.component.AbstractLifecycleComponent
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptService
import org.elasticsearch.script.ScriptType
import org.elasticsearch.script.TemplateScript
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.threadpool.ThreadPool
import java.time.Instant
import kotlin.coroutines.CoroutineContext

class MonitorRunner(
    settings: Settings,
    private val client: Client,
    private val threadPool: ThreadPool,
    private val scriptService: ScriptService,
    private val xContentRegistry: NamedXContentRegistry,
    private val alertIndices: AlertIndices,
    clusterService: ClusterService
) : JobRunner, CoroutineScope, AbstractLifecycleComponent() {

    private val logger = LogManager.getLogger(MonitorRunner::class.java)

    private lateinit var runnerSupervisor: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + runnerSupervisor

    @Volatile private var retryPolicy =
        BackoffPolicy.constantBackoff(ALERT_BACKOFF_MILLIS.get(settings), ALERT_BACKOFF_COUNT.get(settings))
    @Volatile private var moveAlertsRetryPolicy =
        BackoffPolicy.exponentialBackoff(MOVE_ALERTS_BACKOFF_MILLIS.get(settings), MOVE_ALERTS_BACKOFF_COUNT.get(settings))

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERT_BACKOFF_MILLIS, ALERT_BACKOFF_COUNT) {
            millis, count -> retryPolicy = BackoffPolicy.constantBackoff(millis, count)
        }
        clusterService.clusterSettings.addSettingsUpdateConsumer(MOVE_ALERTS_BACKOFF_MILLIS, MOVE_ALERTS_BACKOFF_COUNT) {
            millis, count -> moveAlertsRetryPolicy = BackoffPolicy.exponentialBackoff(millis, count)
        }
    }

    override fun doStart() {
        runnerSupervisor = SupervisorJob()
    }

    override fun doStop() {
        runnerSupervisor.cancel()
    }

    override fun doClose() { }

    override fun postIndex(job: ScheduledJob) {
        if (job !is Monitor) {
            throw IllegalArgumentException("Invalid job type")
        }

        launch {
            try {
                moveAlertsRetryPolicy.retry(logger) {
                    if (alertIndices.isInitialized()) {
                        moveAlerts(client, job.id, job)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to move active alerts for monitor [${job.id}].", e)
            }
        }
    }

    override fun postDelete(jobId: String) {
        launch {
            try {
                moveAlertsRetryPolicy.retry(logger) {
                    if (alertIndices.isInitialized()) {
                        moveAlerts(client, jobId, null)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to move active alerts for monitor [$jobId].", e)
            }
        }
    }

    override fun runJob(job: ScheduledJob, periodStart: Instant, periodEnd: Instant) {
        if (job !is Monitor) {
            throw IllegalArgumentException("Invalid job type")
        }

        launch { runMonitor(job, periodStart, periodEnd) }
    }

    suspend fun runMonitor(monitor: Monitor, periodStart: Instant, periodEnd: Instant, dryrun: Boolean = false): MonitorRunResult {
        if (periodStart == periodEnd) {
            logger.warn("Start and end time are the same: $periodStart. This monitor will probably only run once.")
        }

        var monitorResult = MonitorRunResult(monitor.name, periodStart, periodEnd)
        val currentAlerts = try {
            alertIndices.createOrUpdateAlertIndex()
            alertIndices.createOrUpdateInitialHistoryIndex()
            loadCurrentAlerts(monitor)
        } catch (e: Exception) {
            // We can't save ERROR alerts to the index here as we don't know if there are existing ACTIVE alerts
            val id = if (monitor.id.trim().isEmpty()) "_na_" else monitor.id
            logger.error("Error loading alerts for monitor: $id", e)
            return monitorResult.copy(error = e)
        }

        monitorResult = monitorResult.copy(inputResults = collectInputResults(monitor, periodStart, periodEnd))

        val updatedAlerts = mutableListOf<Alert>()
        val triggerResults = mutableMapOf<String, TriggerRunResult>()
        for (trigger in monitor.triggers) {
            val currentAlert = currentAlerts[trigger]
            val triggerCtx = TriggerExecutionContext(monitor, trigger, monitorResult, currentAlert)
            val triggerResult = runTrigger(monitor, trigger, triggerCtx)
            triggerResults[trigger.id] = triggerResult

            if (isTriggerActionable(triggerCtx, triggerResult)) {
                val actionCtx = triggerCtx.copy(error = monitorResult.error ?: triggerResult.error)
                for (action in trigger.actions) {
                    triggerResult.actionResults[action.id] = runAction(action, actionCtx, dryrun)
                }
            }

            val updatedAlert = composeAlert(triggerCtx, triggerResult, monitorResult.alertError() ?: triggerResult.alertError())
            if (updatedAlert != null) updatedAlerts += updatedAlert
        }

        // Don't save alerts if this is a test monitor
        if (!dryrun && monitor.id != Monitor.NO_ID) {
            saveAlerts(updatedAlerts)
        }
        return monitorResult.copy(triggerResults = triggerResults)
    }

    private fun currentTime() = Instant.ofEpochMilli(threadPool.absoluteTimeInMillis())

    private fun composeAlert(ctx: TriggerExecutionContext, result: TriggerRunResult, alertError: AlertError?): Alert? {
        val currentTime = currentTime()
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
            currentAlert?.copy(state = COMPLETED, endTime = currentTime, errorMessage = null,
                    errorHistory = updatedHistory, actionExecutionResults = updatedActionExecutionResults,
                    schemaVersion = IndexUtils.alertIndexSchemaVersion)
        } else if (alertError == null && currentAlert?.isAcknowledged() == true) {
            null
        } else if (currentAlert != null) {
            val alertState = if (alertError == null) ACTIVE else ERROR
            currentAlert.copy(state = alertState, lastNotificationTime = currentTime, errorMessage = alertError?.message,
                    errorHistory = updatedHistory, actionExecutionResults = updatedActionExecutionResults,
                    schemaVersion = IndexUtils.alertIndexSchemaVersion)
        } else {
            val alertState = if (alertError == null) ACTIVE else ERROR
            Alert(monitor = ctx.monitor, trigger = ctx.trigger, startTime = currentTime,
                    lastNotificationTime = currentTime, state = alertState, errorMessage = alertError?.message,
                    errorHistory = updatedHistory, actionExecutionResults = updatedActionExecutionResults,
                    schemaVersion = IndexUtils.alertIndexSchemaVersion)
        }
    }

    private suspend fun collectInputResults(monitor: Monitor, periodStart: Instant, periodEnd: Instant): InputRunResults {
        return try {
            val results = mutableListOf<Map<String, Any>>()
            monitor.inputs.forEach { input ->
                when (input) {
                    is SearchInput -> {
                        // TODO: Figure out a way to use SearchTemplateRequest without bringing in the entire TransportClient
                        val searchParams = mapOf("period_start" to periodStart.toEpochMilli(),
                                "period_end" to periodEnd.toEpochMilli())
                        val searchSource = scriptService.compile(Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG,
                                input.query.toString(), searchParams), TemplateScript.CONTEXT)
                                .newInstance(searchParams)
                                .execute()

                        val searchRequest = SearchRequest().indices(*input.indices.toTypedArray())
                        XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, searchSource).use {
                            searchRequest.source(SearchSourceBuilder.fromXContent(it))
                        }
                        val searchResponse: SearchResponse = client.suspendUntil { client.search(searchRequest, it) }
                        results += searchResponse.convertToMap()
                    }
                    is HttpInput -> {
                        val httpClient = HttpInputClient()
                        httpClient.client.start()
                        val response: HttpResponse = httpClient.client.suspendUntil {
                            httpClient.client.execute(input.toGetRequest(), it) }
                        httpClient.client.close()
                        results += response.toMap()
                    } else -> {
                        throw IllegalArgumentException("Unsupported input type: ${input.name()}.")
                    }
                }
            }
            InputRunResults(results.toList())
        } catch (e: Exception) {
            logger.info("Error collecting inputs for monitor: ${monitor.id}", e)
            InputRunResults(emptyList(), e)
        }
    }

    private fun runTrigger(monitor: Monitor, trigger: Trigger, ctx: TriggerExecutionContext): TriggerRunResult {
        return try {
            val triggered = scriptService.compile(trigger.condition, TriggerScript.CONTEXT)
                    .newInstance(trigger.condition.params)
                    .execute(ctx)
            TriggerRunResult(trigger.name, triggered, null)
        } catch (e: Exception) {
            logger.info("Error running script for monitor ${monitor.id}, trigger: ${trigger.id}", e)
            // if the script fails we need to send an alert so set triggered = true
            TriggerRunResult(trigger.name, true, e)
        }
    }

    private suspend fun loadCurrentAlerts(monitor: Monitor): Map<Trigger, Alert?> {
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

    private fun contentParser(bytesReference: BytesReference): XContentParser {
        val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                bytesReference, XContentType.JSON)
        ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        return xcp
    }

    private fun alertQuery(monitor: Monitor): SearchSourceBuilder {
        return SearchSourceBuilder.searchSource()
                .size(monitor.triggers.size * 2) // We expect there to be only a single in-progress alert so fetch 2 to check
                .query(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitor.id))
    }

    private suspend fun saveAlerts(alerts: List<Alert>) {
        var requestsToRetry = alerts.flatMap { alert ->
            // we don't want to set the version when saving alerts because the Runner has first priority when writing alerts.
            // In the rare event that a user acknowledges an alert between when it's read and when it's written
            // back we're ok if that acknowledgement is lost. It's easier to get the user to retry than for the runner to
            // spend time reloading the alert and writing it back.
            when (alert.state) {
                ACTIVE, ERROR -> {
                    listOf<DocWriteRequest<*>>(IndexRequest(AlertIndices.ALERT_INDEX)
                            .routing(alert.monitorId)
                            .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                            .id(if (alert.id != Alert.NO_ID) alert.id else null))
                }
                ACKNOWLEDGED, DELETED -> {
                    throw IllegalStateException("Unexpected attempt to save ${alert.state} alert: $alert")
                }
                COMPLETED -> {
                    listOf<DocWriteRequest<*>>(
                            DeleteRequest(AlertIndices.ALERT_INDEX, alert.id)
                                    .routing(alert.monitorId),
                            IndexRequest(AlertIndices.HISTORY_WRITE_INDEX)
                                    .routing(alert.monitorId)
                                    .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                                    .id(alert.id))
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

    private fun isTriggerActionable(ctx: TriggerExecutionContext, result: TriggerRunResult): Boolean {
        // Suppress actions if the current alert is acknowledged and there are no errors.
        val suppress = ctx.alert?.state == ACKNOWLEDGED && result.error == null && ctx.error == null
        return result.triggered && !suppress
    }

    private fun isActionActionable(action: Action, alert: Alert?): Boolean {
        if (alert == null || action.throttle == null) {
            return true
        }
        if (action.throttleEnabled) {
            val result = alert.actionExecutionResults.firstOrNull { r -> r.actionId == action.id }
            val lastExecutionTime: Instant? = result?.lastExecutionTime
            val throttledTimeBound = currentTime().minus(action.throttle.value.toLong(), action.throttle.unit)
            return (lastExecutionTime == null || lastExecutionTime.isBefore(throttledTimeBound))
        }
        return true
    }

    private suspend fun runAction(action: Action, ctx: TriggerExecutionContext, dryrun: Boolean): ActionRunResult {
        return try {
            if (!isActionActionable(action, ctx.alert)) {
                return ActionRunResult(action.id, action.name, mapOf(), true, null, null)
            }
            val actionOutput = mutableMapOf<String, String>()
            actionOutput[SUBJECT] = if (action.subjectTemplate != null) compileTemplate(action.subjectTemplate, ctx) else ""
            actionOutput[MESSAGE] = compileTemplate(action.messageTemplate, ctx)
            if (Strings.isNullOrEmpty(actionOutput[MESSAGE])) {
                throw IllegalStateException("Message content missing in the Destination with id: ${action.destinationId}")
            }
            if (!dryrun) {
                withContext(Dispatchers.IO) {
                    val destination = getDestinationInfo(action.destinationId)
                    actionOutput[MESSAGE_ID] = destination.publish(actionOutput[SUBJECT], actionOutput[MESSAGE]!!)
                }
            }
            ActionRunResult(action.id, action.name, actionOutput, false, currentTime(), null)
        } catch (e: Exception) {
            ActionRunResult(action.id, action.name, mapOf(), false, currentTime(), e)
        }
    }

    private fun compileTemplate(template: Script, ctx: TriggerExecutionContext): String {
        return scriptService.compile(template, TemplateScript.CONTEXT)
                .newInstance(template.params + mapOf("ctx" to ctx.asTemplateArg()))
                .execute()
    }

    private suspend fun getDestinationInfo(destinationId: String): Destination {
        val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, destinationId).routing(destinationId)
        val getResponse: GetResponse = client.suspendUntil { client.get(getRequest, it) }
        if (!getResponse.isExists || getResponse.isSourceEmpty) {
            throw IllegalStateException("Destination document with id $destinationId not found or source is empty")
        }

        val jobSource = getResponse.sourceAsBytesRef
        return withContext(Dispatchers.IO) {
            val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                jobSource, XContentType.JSON)
            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(XContentParser.Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            val destination = Destination.parse(xcp)
            ensureExpectedToken(XContentParser.Token.END_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            destination
        }
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
