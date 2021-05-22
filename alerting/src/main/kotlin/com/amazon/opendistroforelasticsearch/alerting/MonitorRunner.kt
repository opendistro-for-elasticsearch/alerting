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

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.alerts.moveAlerts
import com.amazon.opendistroforelasticsearch.alerting.core.JobRunner
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.InjectorContextElement
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.retry
import com.amazon.opendistroforelasticsearch.alerting.model.ActionRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.AlertingConfigAccessor
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.MonitorRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action.Companion.MESSAGE
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action.Companion.MESSAGE_ID
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action.Companion.SUBJECT
import com.amazon.opendistroforelasticsearch.alerting.model.destination.DestinationContextFactory
import com.amazon.opendistroforelasticsearch.alerting.script.TraditionalTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_BACKOFF_COUNT
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_BACKOFF_MILLIS
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.MOVE_ALERTS_BACKOFF_COUNT
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.MOVE_ALERTS_BACKOFF_MILLIS
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings.Companion.ALLOW_LIST
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings.Companion.ALLOW_LIST_NONE
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings.Companion.HOST_DENY_LIST
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings.Companion.HOST_DENY_LIST_NONE
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings.Companion.loadDestinationSettings
import com.amazon.opendistroforelasticsearch.alerting.util.isADMonitor
import com.amazon.opendistroforelasticsearch.alerting.util.isAggregationMonitor
import com.amazon.opendistroforelasticsearch.alerting.util.isAllowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.Strings
import org.elasticsearch.common.component.AbstractLifecycleComponent
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptService
import org.elasticsearch.script.TemplateScript
import org.elasticsearch.threadpool.ThreadPool
import java.time.Instant
import kotlin.coroutines.CoroutineContext

object MonitorRunner : JobRunner, CoroutineScope, AbstractLifecycleComponent() {

    private val logger = LogManager.getLogger(javaClass)

    private lateinit var clusterService: ClusterService
    private lateinit var client: Client
    private lateinit var xContentRegistry: NamedXContentRegistry
    private lateinit var scriptService: ScriptService
    private lateinit var settings: Settings
    private lateinit var threadPool: ThreadPool
    private lateinit var alertIndices: AlertIndices
    private lateinit var inputService: InputService
    private lateinit var triggerService: TriggerService
    private lateinit var alertService: AlertService

    @Volatile private lateinit var retryPolicy: BackoffPolicy
    @Volatile private lateinit var moveAlertsRetryPolicy: BackoffPolicy

    @Volatile private var allowList = ALLOW_LIST_NONE
    @Volatile private var hostDenyList = HOST_DENY_LIST_NONE

    @Volatile private lateinit var destinationSettings: Map<String, DestinationSettings.Companion.SecureDestinationSettings>
    @Volatile private lateinit var destinationContextFactory: DestinationContextFactory

    private lateinit var runnerSupervisor: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + runnerSupervisor

    fun registerClusterService(clusterService: ClusterService): MonitorRunner {
        this.clusterService = clusterService
        return this
    }

    fun registerClient(client: Client): MonitorRunner {
        this.client = client
        return this
    }

    fun registerNamedXContentRegistry(xContentRegistry: NamedXContentRegistry): MonitorRunner {
        this.xContentRegistry = xContentRegistry
        return this
    }

    fun registerScriptService(scriptService: ScriptService): MonitorRunner {
        this.scriptService = scriptService
        return this
    }

    fun registerSettings(settings: Settings): MonitorRunner {
        this.settings = settings
        return this
    }

    fun registerThreadPool(threadPool: ThreadPool): MonitorRunner {
        this.threadPool = threadPool
        return this
    }

    fun registerAlertIndices(alertIndices: AlertIndices): MonitorRunner {
        this.alertIndices = alertIndices
        return this
    }

    fun registerInputService(inputService: InputService): MonitorRunner {
        this.inputService = inputService
        return this
    }

    fun registerTriggerService(triggerService: TriggerService): MonitorRunner {
        this.triggerService = triggerService
        return this
    }

    fun registerAlertService(alertService: AlertService): MonitorRunner {
        this.alertService = alertService
        return this
    }

    // Must be called after registerClusterService and registerSettings in AlertingPlugin
    fun registerConsumers(): MonitorRunner {
        retryPolicy = BackoffPolicy.constantBackoff(ALERT_BACKOFF_MILLIS.get(settings), ALERT_BACKOFF_COUNT.get(settings))
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERT_BACKOFF_MILLIS, ALERT_BACKOFF_COUNT) {
            millis, count -> retryPolicy = BackoffPolicy.constantBackoff(millis, count)
        }

        moveAlertsRetryPolicy =
            BackoffPolicy.exponentialBackoff(MOVE_ALERTS_BACKOFF_MILLIS.get(settings), MOVE_ALERTS_BACKOFF_COUNT.get(settings))
        clusterService.clusterSettings.addSettingsUpdateConsumer(MOVE_ALERTS_BACKOFF_MILLIS, MOVE_ALERTS_BACKOFF_COUNT) {
            millis, count -> moveAlertsRetryPolicy = BackoffPolicy.exponentialBackoff(millis, count)
        }

        allowList = ALLOW_LIST.get(settings)
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALLOW_LIST) {
            allowList = it
        }

        // Host deny list is not a dynamic setting so no consumer is registered but the variable is set here
        hostDenyList = HOST_DENY_LIST.get(settings)

        return this
    }

    // To be safe, call this last as it depends on a number of other components being registered beforehand (client, settings, etc.)
    fun registerDestinationSettings(): MonitorRunner {
        destinationSettings = loadDestinationSettings(settings)
        destinationContextFactory = DestinationContextFactory(client, xContentRegistry, destinationSettings)
        return this
    }

    // Updates destination settings when the reload API is called so that new keystore values are visible
    fun reloadDestinationSettings(settings: Settings) {
        destinationSettings = loadDestinationSettings(settings)

        // Update destinationContextFactory as well since destinationSettings has been updated
        destinationContextFactory.updateDestinationSettings(destinationSettings)
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

        launch {
            if (job.isAggregationMonitor()) {
                runAggregationMonitor(job, periodStart, periodEnd)
            } else {
                runMonitor(job, periodStart, periodEnd)
            }
        }
    }

    suspend fun runMonitor(monitor: Monitor, periodStart: Instant, periodEnd: Instant, dryrun: Boolean = false):
            MonitorRunResult<TraditionalTriggerRunResult> {
        val roles = getRolesForMonitor(monitor)
        logger.debug("Running monitor: ${monitor.name} with roles: $roles Thread: ${Thread.currentThread().name}")

        if (periodStart == periodEnd) {
            logger.warn("Start and end time are the same: $periodStart. This monitor will probably only run once.")
        }

        var monitorResult = MonitorRunResult<TraditionalTriggerRunResult>(monitor.name, periodStart, periodEnd)
        val currentAlerts = try {
            alertIndices.createOrUpdateAlertIndex()
            alertIndices.createOrUpdateInitialHistoryIndex()
            alertService.loadCurrentAlerts(monitor)
        } catch (e: Exception) {
            // We can't save ERROR alerts to the index here as we don't know if there are existing ACTIVE alerts
            val id = if (monitor.id.trim().isEmpty()) "_na_" else monitor.id
            logger.error("Error loading alerts for monitor: $id", e)
            return monitorResult.copy(error = e)
        }
        if (!isADMonitor(monitor)) {
            runBlocking(InjectorContextElement(monitor.id, settings, threadPool.threadContext, roles)) {
                monitorResult = monitorResult.copy(inputResults = inputService.collectInputResults(monitor, periodStart, periodEnd))
            }
        } else {
            monitorResult = monitorResult.copy(inputResults = inputService.collectInputResultsForADMonitor(monitor, periodStart, periodEnd))
        }

        val updatedAlerts = mutableListOf<Alert>()
        val triggerResults = mutableMapOf<String, TraditionalTriggerRunResult>()
        for (trigger in monitor.triggers) {
            val currentAlert = currentAlerts[trigger]
            val triggerCtx = TraditionalTriggerExecutionContext(monitor, trigger as TraditionalTrigger, monitorResult, currentAlert)
            val triggerResult = triggerService.runTraditionalTrigger(monitor, trigger, triggerCtx)
            triggerResults[trigger.id] = triggerResult

            if (triggerService.isTraditionalTriggerActionable(triggerCtx, triggerResult)) {
                val actionCtx = triggerCtx.copy(error = monitorResult.error ?: triggerResult.error)
                for (action in trigger.actions) {
                    triggerResult.actionResults[action.id] = runAction(action, actionCtx, dryrun)
                }
            }

            val updatedAlert = alertService.composeTraditionalAlert(triggerCtx, triggerResult,
                monitorResult.alertError() ?: triggerResult.alertError())
            if (updatedAlert != null) updatedAlerts += updatedAlert
        }

        // Don't save alerts if this is a test monitor
        if (!dryrun && monitor.id != Monitor.NO_ID) {
            alertService.saveAlerts(updatedAlerts, retryPolicy)
        }
        return monitorResult.copy(triggerResults = triggerResults)
    }

    suspend fun runAggregationMonitor(
        monitor: Monitor,
        periodStart: Instant,
        periodEnd: Instant,
        dryrun: Boolean = false
    ): MonitorRunResult<AggregationTriggerRunResult> {
        val roles = getRolesForMonitor(monitor)
        logger.debug("Running monitor: ${monitor.name} with roles: $roles Thread: ${Thread.currentThread().name}")

        if (periodStart == periodEnd) {
            logger.warn("Start and end time are the same: $periodStart. This monitor will probably only run once.")
        }

        // TODO: Should we use MonitorRunResult for both Monitor types or create an AggregationMonitorRunResult
        //  to store InternalComposite instead of Map<String, Any>?
        var monitorResult = MonitorRunResult<AggregationTriggerRunResult>(monitor.name, periodStart, periodEnd)
        val currentAlerts = try {
            alertIndices.createOrUpdateAlertIndex()
            alertIndices.createOrUpdateInitialHistoryIndex()
            alertService.loadCurrentAlertsForAggregationMonitor(monitor)
        } catch (e: Exception) {
            // We can't save ERROR alerts to the index here as we don't know if there are existing ACTIVE alerts
            val id = if (monitor.id.trim().isEmpty()) "_na_" else monitor.id
            logger.error("Error loading alerts for monitor: $id", e)
            return monitorResult.copy(error = e)
        }

        // TODO: Since a composite aggregation is being used for the input query, the total bucket count cannot be determined.
        //  If a setting is imposed that limits buckets that can be processed for Aggregation Monitors, we'd need to iterate over
        //  the buckets until we hit that threshold. In that case, we'd want to exit the execution without creating any alerts since the
        //  buckets we iterate over before hitting the limit is not deterministic. Is there a better way to fail faster in this case?
        runBlocking(InjectorContextElement(monitor.id, settings, threadPool.threadContext, roles)) {
            monitorResult = monitorResult.copy(inputResults = inputService.collectInputResults(monitor, periodStart, periodEnd))
        }

        // TODO: Iterate through buckets and execute Trigger scripts against each bucket creating a Trigger -> buckets mappings for alert
        //  creation.

        // TODO: Separate buckets/alerts into categories so that the alerts can be created/updated accordingly:
        //  * De-duped alerts = currentAlerts U filteredBuckets
        //  * Completed alerts = currentAlerts - de-duped alerts
        //  * New alerts = filteredBuckets - de-duped alerts

        // TODO: Run Actions for a Trigger and compose alerts

        // TODO: Update alerts in alerting config index

        return monitorResult
    }

    private fun getRolesForMonitor(monitor: Monitor): List<String> {
        /*
         * We need to handle 3 cases:
         * 1. Monitors created by older versions and never updated. These monitors wont have User details in the
         * monitor object. `monitor.user` will be null. Insert `all_access, AmazonES_all_access` role.
         * 2. Monitors are created when security plugin is disabled, these will have empty User object.
         * (`monitor.user.name`, `monitor.user.roles` are empty )
         * 3. Monitors are created when security plugin is enabled, these will have an User object.
         */
        return if (monitor.user == null) {
            // fixme: discuss and remove hardcoded to settings?
            // TODO: Remove "AmazonES_all_access" role?
            settings.getAsList("", listOf("all_access", "AmazonES_all_access"))
        } else {
            monitor.user.roles
        }
    }

    // TODO: Can this be updated to just use 'Instant.now()'?
    //  'threadPool.absoluteTimeInMillis()' is referring to a cached value of System.currentTimeMillis() that by default updates every 200ms
    private fun currentTime() = Instant.ofEpochMilli(threadPool.absoluteTimeInMillis())

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

    private suspend fun runAction(action: Action, ctx: TraditionalTriggerExecutionContext, dryrun: Boolean): ActionRunResult {
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
                    val destination = AlertingConfigAccessor.getDestinationInfo(client, xContentRegistry, action.destinationId)
                    if (!destination.isAllowed(allowList)) {
                        throw IllegalStateException("Monitor contains a Destination type that is not allowed: ${destination.type}")
                    }

                    val destinationCtx = destinationContextFactory.getDestinationContext(destination)
                    actionOutput[MESSAGE_ID] = destination.publish(
                        actionOutput[SUBJECT],
                        actionOutput[MESSAGE]!!,
                        destinationCtx,
                        hostDenyList
                    )
                }
            }
            ActionRunResult(action.id, action.name, actionOutput, false, currentTime(), null)
        } catch (e: Exception) {
            ActionRunResult(action.id, action.name, mapOf(), false, currentTime(), e)
        }
    }

    private fun compileTemplate(template: Script, ctx: TraditionalTriggerExecutionContext): String {
        return scriptService.compile(template, TemplateScript.CONTEXT)
                .newInstance(template.params + mapOf("ctx" to ctx.asTemplateArg()))
                .execute()
    }
}
