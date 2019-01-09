/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.JobRunner
import com.amazon.elasticsearch.model.Action
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertError
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.ActionRunResult
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Alert.State.ACKNOWLEDGED
import com.amazon.elasticsearch.monitoring.model.Alert.State.ACTIVE
import com.amazon.elasticsearch.monitoring.model.Alert.State.COMPLETED
import com.amazon.elasticsearch.monitoring.model.Alert.State.DELETED
import com.amazon.elasticsearch.monitoring.model.Alert.State.ERROR
import com.amazon.elasticsearch.monitoring.model.InputRunResults
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.MonitorRunResult
import com.amazon.elasticsearch.monitoring.model.TestAction
import com.amazon.elasticsearch.monitoring.model.Trigger
import com.amazon.elasticsearch.monitoring.model.TriggerRunResult
import com.amazon.elasticsearch.monitoring.script.TriggerExecutionContext
import com.amazon.elasticsearch.monitoring.script.TriggerScript
import com.amazon.elasticsearch.monitoring.settings.MonitoringSettings
import com.amazon.elasticsearch.notification.Notification
import com.amazon.elasticsearch.notification.message.SNSMessage
import com.amazon.elasticsearch.notification.response.SNSResponse
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.convertToMap
import com.amazon.elasticsearch.util.firstFailureOrNull
import com.amazon.elasticsearch.util.retry
import org.elasticsearch.ExceptionsHelper
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.Client
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.util.concurrent.EsExecutors
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptService
import org.elasticsearch.script.ScriptType
import org.elasticsearch.script.TemplateScript
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.threadpool.ScalingExecutorBuilder
import org.elasticsearch.threadpool.ThreadPool
import java.time.Instant

class MonitorRunner(private val settings: Settings,
                    private val client: Client,
                    private val threadPool: ThreadPool,
                    private val scriptService: ScriptService,
                    private val xContentRegistry: NamedXContentRegistry,
                    private val alertIndices: AlertIndices) : JobRunner {

    private val logger = ElasticAPI.INSTANCE.getLogger(MonitorRunner::class.java, settings)

    private val searchTimeout = MonitoringSettings.INPUT_TIMEOUT.get(settings)
    private val indexTimeout = MonitoringSettings.INDEX_TIMEOUT.get(settings)
    private val bulkTimeout = MonitoringSettings.BULK_TIMEOUT.get(settings)
    private val RETRY_POLICY = BackoffPolicy.constantBackoff(
            MonitoringSettings.ALERT_BACKOFF_MILLIS.get(settings),
            MonitoringSettings.ALERT_BACKOFF_COUNT.get(settings))

    companion object {
        private const val THREAD_POOL_NAME = "aes_monitor_runner"

        fun executorBuilder(settings: Settings): ScalingExecutorBuilder {
            val availableProcessors = EsExecutors.numberOfProcessors(settings)
            // Use the same setting as ES GENERIC Executor builder.
            val genericThreadPoolMax = Math.min(512, Math.max(128, 4 * availableProcessors))
            return ScalingExecutorBuilder(THREAD_POOL_NAME, 4, genericThreadPoolMax, TimeValue.timeValueSeconds(30L))
        }
    }

    fun executor() = threadPool.executor(THREAD_POOL_NAME)!!

    override fun runJob(job: ScheduledJob, periodStart: Instant, periodEnd: Instant) {
        if (job is Monitor) {
            executor().submit{ runMonitor(job, periodStart, periodEnd) }
        } else {
            throw IllegalArgumentException("Invalid job type")
        }
    }

    fun runMonitor(monitor: Monitor, periodStart: Instant, periodEnd: Instant, dryrun: Boolean = false) : MonitorRunResult {
        if (periodStart == periodEnd) {
            logger.warn("Start and end time are the same: $periodStart. This monitor will probably only run once.")
        }

        var monitorResult = MonitorRunResult(monitor.name, periodStart, periodEnd)
        val currentAlerts = try {
            alertIndices.createAlertIndex()
            alertIndices.createInitialHistoryIndex()
            loadCurrentAlerts(monitor)
        } catch (e: Exception) {
            // We can't save ERROR alerts to the index here as we don't know if there are existing ACTIVE alerts
            val id = if (monitor.id.isBlank()) "_na_" else monitor.id
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
                    triggerResult.actionResults[action.name] = runAction(action, actionCtx, dryrun)
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
        // Merge the alert's error message to the current alert's history
        val updatedHistory = currentAlert?.errorHistory.update(alertError)
        return if (alertError == null && !result.triggered) {
            currentAlert?.copy(state = COMPLETED, endTime = currentTime, errorMessage = null,
                    errorHistory = updatedHistory)
        } else if (alertError == null && currentAlert?.isAcknowledged() == true) {
            null
        } else if (currentAlert != null) {
            val alertState = if (alertError == null) ACTIVE else ERROR
            currentAlert.copy(state = alertState, lastNotificationTime = currentTime, errorMessage = alertError?.message,
                    errorHistory = updatedHistory)
        } else {
            val alertState = if (alertError == null) ACTIVE else ERROR
            Alert(monitor = ctx.monitor, trigger = ctx.trigger, startTime = currentTime,
                    lastNotificationTime = currentTime, state = alertState, errorMessage = alertError?.message,
                    errorHistory = updatedHistory)
        }
    }

    private fun collectInputResults(monitor: Monitor, periodStart: Instant, periodEnd: Instant): InputRunResults {
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
                        ElasticAPI.INSTANCE.jsonParser(xContentRegistry, searchSource).use {
                            searchRequest.source(SearchSourceBuilder.fromXContent(it))
                        }
                        results += client.search(searchRequest).actionGet(searchTimeout).convertToMap()
                    }
                    else -> {
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

    private fun runTrigger(monitor: Monitor, trigger: Trigger, ctx: TriggerExecutionContext) : TriggerRunResult {
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

    private fun loadCurrentAlerts(monitor: Monitor) : Map<Trigger, Alert?> {
        val request = SearchRequest(AlertIndices.ALERT_INDEX)
                .routing(monitor.id)
                .source(alertQuery(monitor))
        val response = client.search(request).actionGet(searchTimeout)
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

    private fun contentParser(bytesReference: BytesReference) : XContentParser {
        val xcp = ElasticAPI.INSTANCE.jsonParser(xContentRegistry, bytesReference)
        ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        return xcp
    }

    private fun alertQuery(monitor: Monitor) : SearchSourceBuilder {
        return SearchSourceBuilder.searchSource()
                .size(monitor.triggers.size * 2) // We expect there to be only a single in-progress alert so fetch 2 to check
                .query(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitor.id))
    }

    private fun saveAlerts(alerts: List<Alert>) {
        var requestsToRetry = alerts.flatMap { alert ->
            // we don't want to set the version when saving alerts because the Runner has first priority when writing alerts.
            // In the rare event that a user acknowledges an alert between when it's read and when it's written
            // back we're ok if that acknowledgement is lost. It's easier to get the user to retry than for the runner to
            // spend time reloading the alert and writing it back.
            when (alert.state) {
                ACTIVE, ERROR -> {
                    listOf<DocWriteRequest<*>>(IndexRequest(AlertIndices.ALERT_INDEX, AlertIndices.MAPPING_TYPE)
                            .routing(alert.monitorId)
                            .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                            .id(if (alert.id != Alert.NO_ID) alert.id else null))
                }
                ACKNOWLEDGED, DELETED -> {
                    throw IllegalStateException("Unexpected attempt to save ${alert.state} alert: $alert")
                }
                COMPLETED -> {
                    listOf<DocWriteRequest<*>>(
                            DeleteRequest(AlertIndices.ALERT_INDEX, AlertIndices.MAPPING_TYPE, alert.id)
                                    .routing(alert.monitorId),
                            IndexRequest(AlertIndices.HISTORY_WRITE_INDEX, AlertIndices.MAPPING_TYPE)
                                    .routing(alert.monitorId)
                                    .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                                    .id(alert.id))
                }
            }
        }

        if (requestsToRetry.isEmpty()) return
        var bulkRequest = BulkRequest().add(requestsToRetry)
        val successfulResponses = mutableListOf<BulkItemResponse>()
        var failedResponses = listOf<BulkItemResponse>()
        RETRY_POLICY.retry { // Handles 502, 503, 504 responses for the bulk request.
            RETRY_POLICY.iterator().forEach { delay ->  // Handles partial failures
                val responses = client.bulk(bulkRequest).actionGet(bulkTimeout).items ?: arrayOf()
                successfulResponses += responses.filterNot { it.isFailed }
                failedResponses = responses.filter { it.isFailed }
                // retry only if this is a EsRejectedExecutionException (i.e. 429 TOO MANY REQUESTs)
                requestsToRetry = failedResponses
                        .filter { ExceptionsHelper.unwrapCause(it.failure.cause) is EsRejectedExecutionException }
                        .map { bulkRequest.requests()[it.itemId] as IndexRequest }

                bulkRequest = BulkRequest().add(requestsToRetry)
                if (requestsToRetry.isEmpty()) {
                    return@retry
                } else {
                    Thread.sleep(delay.millis)
                }
            }
        }

        for (it in failedResponses) {
            logger.error("Failed to write alert: ${it.id}", it.failure.cause)
        }
    }

    private fun isTriggerActionable(ctx: TriggerExecutionContext, result: TriggerRunResult) : Boolean {
        // Suppress actions if the current alert is acknowledged and there are no errors.
        val suppress = ctx.alert?.state == ACKNOWLEDGED && result.error == null && ctx.error == null
        return result.triggered && !suppress
    }

    private fun runAction(action: Action, ctx: TriggerExecutionContext, dryrun: Boolean) : ActionRunResult {
        return try {
            val actionOutput = when (action) {
                is SNSAction -> action.run(ctx, dryrun)
                is TestAction -> action.run(ctx)
                else -> throw IllegalArgumentException("Unknown action type: ${action.type}")
            }
            ActionRunResult(action.name, actionOutput, false, null)
        } catch (e: Exception) {
            ActionRunResult(action.name, mapOf(), false, e)
        }
    }

    private fun SNSAction.run(ctx: TriggerExecutionContext, dryrun: Boolean) : Map<String, String> {
        val actionOutput = mutableMapOf<String, String>()

        actionOutput["subject"] = scriptService.compile(subjectTemplate, TemplateScript.CONTEXT)
                .newInstance(subjectTemplate.params + mapOf("_ctx" to ctx.asTemplateArg()))
                .execute()

        actionOutput["message"] = scriptService.compile(messageTemplate, TemplateScript.CONTEXT)
                .newInstance(messageTemplate.params + mapOf("_ctx" to ctx.asTemplateArg()))
                .execute()

        // channel name will be replaced with actual name once we start supporting dedicated channels page
        if (!dryrun) {
            val snsMessage = SNSMessage.Builder("default").withRole(roleARN).withTopicArn(topicARN)
                    .withMessage(actionOutput["message"]).withSubject(actionOutput["subject"]).build()
            val response = Notification.publish(snsMessage) as SNSResponse
            actionOutput["messageId"] = response.messageId
            logger.info("Message published for action name: ${name}, sns messageid: ${response.messageId}, statuscode: ${response.statusCode}")
        }
        return actionOutput.toMap()
    }

    private fun TestAction.run(ctx: TriggerExecutionContext) : Map<String, String> {
        val actionOutput = mutableMapOf<String, String>()
        actionOutput["message"] = scriptService.compile(messageTemplate, TemplateScript.CONTEXT)
                .newInstance(messageTemplate.params + mapOf("_ctx" to ctx.asTemplateArg()))
                .execute()
        return actionOutput.toMap()
    }

    private fun List<AlertError>?.update(alertError: AlertError?) : List<AlertError> {
        return when {
            this == null && alertError == null -> emptyList()
            this != null && alertError == null -> this
            this == null && alertError != null -> listOf(alertError)
            this != null && alertError != null -> (listOf(alertError) + this).take(10)
            else -> throw IllegalStateException("Unreachable code reached!")
        }
    }
}
