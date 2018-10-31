/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.JobRunner
import com.amazon.elasticsearch.model.Action
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.ActionRunResult
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.MonitorRunResult
import com.amazon.elasticsearch.monitoring.model.Trigger
import com.amazon.elasticsearch.monitoring.model.TriggerRunResult
import com.amazon.elasticsearch.monitoring.script.TriggerExecutionContext
import com.amazon.elasticsearch.monitoring.script.TriggerScript
import com.amazon.elasticsearch.util.convertToMap
import com.amazon.elasticsearch.util.firstFailureOrNull
import com.amazon.elasticsearch.util.retry
import com.amazon.elasticsearch.util.stackTraceString
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
import org.elasticsearch.common.logging.ServerLoggers
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
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

class MonitorRunner(private val settings: Settings,
                    private val client: Client,
                    private val threadPool: ThreadPool,
                    private val scriptService: ScriptService,
                    private val xContentRegistry: NamedXContentRegistry,
                    private val alertIndices: AlertIndices) : JobRunner {

    private val logger = ServerLoggers.getLogger(MonitorRunner::class.java, settings)

    private val searchTimeout = SEARCH_TIMEOUT.get(settings)
    private val indexTimeout = INDEX_TIMEOUT.get(settings)
    private val bulkTimeout = BULK_TIMEOUT.get(settings)

    companion object {

        // Same default timeouts as elastic: https://www.elastic.co/guide/en/watcher/current/configuring-default-internal-ops-timeouts.html
        private val SEARCH_TIMEOUT = Setting.positiveTimeSetting("aes.monitoring.search_timeout", TimeValue.timeValueSeconds(30))
        private val INDEX_TIMEOUT = Setting.positiveTimeSetting("aes.monitoring.index_timeout", TimeValue.timeValueSeconds(60))
        private val BULK_TIMEOUT = Setting.positiveTimeSetting("aes.monitoring.bulk_timeout", TimeValue.timeValueSeconds(120))

        // TODO: Needs to be tuned
        private val RETRY_POLICY = BackoffPolicy.constantBackoff(TimeValue.timeValueMillis(50), 2)
    }

    override fun runJob(job: ScheduledJob, periodStart: Instant, periodEnd: Instant) {
        if (job is Monitor) {
            threadPool.generic().submit { runMonitor(job, periodStart, periodEnd) }
        } else {
            throw IllegalArgumentException("Invalid job type")
        }
    }

    fun runMonitor(monitor: Monitor, periodStart: Instant, periodEnd: Instant, dryrun: Boolean = false)
            : MonitorRunResult {
        val monitorResponse = MonitorRunResult(monitor.name, periodStart, periodEnd)
        val currentTime = Instant.ofEpochMilli(threadPool.absoluteTimeInMillis())

        val currentAlerts = try {
            alertIndices.createAlertIndex()
            alertIndices.createInitialHistoryIndex()
            loadCurrentAlerts(monitor)
        } catch (e: Exception) {
            // We can't save ERROR alerts to the index here as we don't know if there are existing ACTIVE alerts
            logger.error("Error loading alerts for monitor: ${monitor.id}", e)
            monitor.triggers.forEach { trigger ->
                monitorResponse.triggerResults[trigger.name] = TriggerRunResult(trigger.name, false, e.stackTraceString())
            }
            return monitorResponse
        }

        var inputError: Exception? = null
        val results = try {
            collectInputResults(monitor, periodStart, periodEnd)
        } catch (e: Exception) {
            logger.info("Error collecting inputs for monitor: ${monitor.id}", e)
            inputError = e
            emptyList<Map<String, Any>>()
        }

        val updatedAlerts = mutableListOf<Alert>()
        for (trigger in monitor.triggers) {
            val alert = currentAlerts[trigger]
            var ctx = TriggerExecutionContext(monitor, trigger, results, periodStart, periodEnd, alert, inputError)
            var scriptError: Exception? = null

            val triggered = try {
                scriptService.compile(trigger.condition, TriggerScript.CONTEXT)
                        .newInstance(trigger.condition.params)
                        .execute(ctx)
            } catch (e: Exception) {
                logger.info("Error running script for monitor ${monitor.id}, trigger: ${trigger.id}", e)
                scriptError = e
                ctx = ctx.copy(error = inputError ?: scriptError)
                true // if the script fails we need to send an alert so set triggered = true
            }

            val triggerResult = TriggerRunResult(trigger.name, triggered, (inputError ?: scriptError)?.stackTraceString())
            monitorResponse.triggerResults[trigger.id] = triggerResult
            if (!triggered) {
                if (alert != null) {
                    updatedAlerts += alert.copy(state = Alert.State.COMPLETED, endTime = currentTime)
                }
                continue
            }
            if (alert != null && alert.state == Alert.State.ACKNOWLEDGED) {
                continue
            }

            // TODO: Add time based rate limiting
            for (action in trigger.actions) {
                var actionError : Exception? = null
                val actionOutput = try {
                    runAction(action, ctx, dryrun)
                } catch (e: Exception) {
                    actionError = e
                    null
                }

                val errorMessage = (inputError ?: scriptError ?: actionError)?.stackTraceString()
                val actionResult = ActionRunResult(action.name, actionOutput, false, errorMessage)
                triggerResult.actionResults[action.name] = actionResult

                val newState = if (errorMessage != null) Alert.State.ERROR else Alert.State.ACTIVE
                updatedAlerts += if (alert != null) {
                    alert.copy(state = newState, lastNotificationTime = currentTime, errorMessage = errorMessage)
                } else {
                    Alert(monitor, trigger, currentTime, currentTime, newState, errorMessage)
                }
            }
        }

        if (!dryrun) {
            saveAlerts(updatedAlerts)
        }
        return monitorResponse
    }

    private fun collectInputResults(monitor: Monitor, periodStart: Instant, periodEnd: Instant): List<Map<String, Any>> {
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
                    XContentType.JSON.xContent().createParser(xContentRegistry, searchSource).use {
                        searchRequest.source(SearchSourceBuilder.fromXContent(it))
                    }
                    results += client.search(searchRequest).actionGet(searchTimeout).convertToMap()
                }
                else -> {
                    throw IllegalArgumentException("Unsupported input type: ${input.name()}.")
                }
            }
        }
        return results.toList()
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
        var xcp = XContentType.JSON.xContent().createParser(xContentRegistry, bytesReference)
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
                Alert.State.ACTIVE, Alert.State.ERROR -> {
                    listOf<DocWriteRequest<*>>(IndexRequest(AlertIndices.ALERT_INDEX, AlertIndices.MAPPING_TYPE)
                            .routing(alert.monitorId)
                            .source(alert.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                            .id(if (alert.id != Alert.NO_ID) alert.id else null))
                }
                Alert.State.ACKNOWLEDGED -> {
                    throw IllegalStateException("Unexpected attempt to save ACKNOWLEDGED alert $alert")
                }
                Alert.State.COMPLETED -> {
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

    private fun runAction(action: Action, ctx: TriggerExecutionContext, dryrun: Boolean) : String {
        when (action) {
            is SNSAction -> {
                // Todo: integrate with notification
                val subject = scriptService.compile(action.subjectTemplate, TemplateScript.CONTEXT)
                        .newInstance(action.subjectTemplate.params + mapOf("_ctx" to ctx.asTemplateArg()))
                        .execute()
                val message = scriptService.compile(action.messageTemplate, TemplateScript.CONTEXT)
                        .newInstance(action.messageTemplate.params + mapOf("_ctx" to ctx.asTemplateArg()))
                        .execute()
                return "Subject: $subject\nMessage: $message"
            }
            else -> {
                logger.info("Unknown action type: ${action.type}")
                return ""
            }
        }
    }
}
