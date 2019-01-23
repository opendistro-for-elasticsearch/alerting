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
package com.amazon.opendistro.alerting

import com.amazon.opendistro.model.action.ChimeAction
import com.amazon.opendistro.model.action.CustomWebhookAction
import com.amazon.opendistro.model.action.SlackAction
import com.amazon.opendistro.JobSweeper
import com.amazon.opendistro.ScheduledJobIndices
import com.amazon.opendistro.Settings.REQUEST_TIMEOUT
import com.amazon.opendistro.Settings.SWEEP_BACKOFF_MILLIS
import com.amazon.opendistro.Settings.SWEEP_BACKOFF_RETRY_COUNT
import com.amazon.opendistro.Settings.SWEEP_PAGE_SIZE
import com.amazon.opendistro.Settings.SWEEP_PERIOD
import com.amazon.opendistro.action.node.ScheduledJobsStatsAction
import com.amazon.opendistro.action.node.ScheduledJobsStatsTransportAction
import com.amazon.opendistro.model.SNSAction
import com.amazon.opendistro.model.ScheduledJob
import com.amazon.opendistro.model.SearchInput
import com.amazon.opendistro.alerting.alerts.AlertIndices
import com.amazon.opendistro.alerting.model.Monitor
import com.amazon.opendistro.alerting.model.TestAction
import com.amazon.opendistro.alerting.resthandler.RestAcknowledgeAlertAction
import com.amazon.opendistro.alerting.resthandler.RestDeleteDestinationAction
import com.amazon.opendistro.alerting.resthandler.RestDeleteMonitorAction
import com.amazon.opendistro.alerting.resthandler.RestDisableMonitoringAction
import com.amazon.opendistro.alerting.resthandler.RestExecuteMonitorAction
import com.amazon.opendistro.alerting.resthandler.RestGetMonitorAction
import com.amazon.opendistro.alerting.resthandler.RestIndexDestinationAction
import com.amazon.opendistro.alerting.resthandler.RestIndexMonitorAction
import com.amazon.opendistro.alerting.resthandler.RestSearchMonitorAction
import com.amazon.opendistro.alerting.script.TriggerScript
import com.amazon.opendistro.alerting.settings.MonitoringSettings
import com.amazon.opendistro.resthandler.RestScheduledJobStatsHandler
import com.amazon.opendistro.schedule.JobScheduler
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.io.stream.NamedWriteableRegistry
import org.elasticsearch.common.settings.ClusterSettings
import org.elasticsearch.common.settings.IndexScopedSettings
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.settings.SettingsFilter
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.env.Environment
import org.elasticsearch.env.NodeEnvironment
import org.elasticsearch.index.IndexModule
import org.elasticsearch.painless.spi.PainlessExtension
import org.elasticsearch.painless.spi.Whitelist
import org.elasticsearch.painless.spi.WhitelistLoader
import org.elasticsearch.plugins.ActionPlugin
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.plugins.ScriptPlugin
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestHandler
import org.elasticsearch.script.ScriptContext
import org.elasticsearch.script.ScriptService
import org.elasticsearch.threadpool.ExecutorBuilder
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.watcher.ResourceWatcherService
import java.util.function.Supplier
/**
 * Entry point of the Amazon Elasticsearch monitoring plugin
 * This class initializes the [RestGetMonitorAction], [RestDeleteMonitorAction], [RestIndexMonitorAction] rest handlers.
 * It also adds [Monitor.XCONTENT_REGISTRY], [SearchInput.XCONTENT_REGISTRY], [SNSAction.XCONTENT_REGISTRY] to the
 * [NamedXContentRegistry] so that we are able to deserialize the custom named objects.
 */
internal class MonitoringPlugin : PainlessExtension, ActionPlugin, ScriptPlugin, Plugin() {
    override fun getContextWhitelists(): Map<ScriptContext<*>, List<Whitelist>> {
        val whitelist = WhitelistLoader.loadFromResourceFiles(javaClass, "com.amazon.opendistro.alerting.txt")
        return mapOf(TriggerScript.CONTEXT to listOf(whitelist))
    }

    companion object {
        @JvmField val KIBANA_USER_AGENT = "Kibana"
        @JvmField val UI_METADATA_EXCLUDE = arrayOf("monitor.${Monitor.UI_METADATA_FIELD}")
        @JvmField val MONITOR_BASE_URI = "/_alerting/monitors"
        @JvmField val DESTINATION_BASE_URI = "/_alerting/destination"
    }

    lateinit var runner: MonitorRunner
    lateinit var scheduler: JobScheduler
    lateinit var sweeper: JobSweeper
    lateinit var scheduledJobIndices: ScheduledJobIndices
    lateinit var threadPool: ThreadPool
    lateinit var alertIndices: AlertIndices

    override fun getRestHandlers(settings: Settings,
                                 restController: RestController,
                                 clusterSettings: ClusterSettings,
                                 indexScopedSettings: IndexScopedSettings,
                                 settingsFilter: SettingsFilter,
                                 indexNameExpressionResolver: IndexNameExpressionResolver?,
                                 nodesInCluster: Supplier<DiscoveryNodes>): List<RestHandler> {
        return listOf(RestGetMonitorAction(settings, restController),
                RestDeleteMonitorAction(settings, restController, alertIndices),
                RestIndexMonitorAction(settings, restController, scheduledJobIndices),
                RestSearchMonitorAction(settings, restController),
                RestExecuteMonitorAction(settings, restController, runner),
                RestAcknowledgeAlertAction(settings, restController),
                RestDisableMonitoringAction(settings, restController),
                RestScheduledJobStatsHandler(settings, restController, "_monitors"),
                RestIndexDestinationAction(settings, restController, scheduledJobIndices),
                RestDeleteDestinationAction(settings, restController))
    }

    override fun getActions(): List<ActionPlugin.ActionHandler<out ActionRequest, out ActionResponse>> {
        return listOf(ActionPlugin.ActionHandler(ScheduledJobsStatsAction.INSTANCE, ScheduledJobsStatsTransportAction::class.java))
    }

    override fun getNamedXContent(): List<NamedXContentRegistry.Entry> {
        return listOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY,
                ChimeAction.XCONTENT_REGISTRY,
                CustomWebhookAction.XCONTENT_REGISTRY,
                SlackAction.XCONTENT_REGISTRY,
                SNSAction.XCONTENT_REGISTRY,
                TestAction.XCONTENT_REGISTRY)
    }

    override fun createComponents(client: Client, clusterService: ClusterService, threadPool: ThreadPool,
                                  resourceWatcherService: ResourceWatcherService, scriptService: ScriptService,
                                  xContentRegistry: NamedXContentRegistry, environment: Environment,
                                  nodeEnvironment: NodeEnvironment,
                                  namedWriteableRegistry: NamedWriteableRegistry): Collection<Any> {
        // Need to figure out how to use the Elasticsearch DI classes rather than handwiring things here.
        val settings = environment.settings()
        alertIndices = AlertIndices(settings, client.admin().indices(), threadPool)
        clusterService.addListener(alertIndices)
        runner = MonitorRunner(settings, client, threadPool, scriptService, xContentRegistry, alertIndices)
        scheduler = JobScheduler(threadPool, runner)
        sweeper = JobSweeper(environment.settings(), client, clusterService, threadPool, xContentRegistry, scheduler)
        clusterService.clusterSettings.addSettingsUpdateConsumer(MonitoringSettings.MONITORING_ENABLED) { enabled ->
            if(!enabled) sweeper.disable() else sweeper.enable()
        }
        scheduledJobIndices = ScheduledJobIndices(client.admin(), clusterService)
        this.threadPool = threadPool
        return listOf(sweeper, scheduler, runner)
    }

    override fun getSettings(): List<Setting<*>> {
        return listOf(
                REQUEST_TIMEOUT,
                SWEEP_BACKOFF_MILLIS,
                SWEEP_BACKOFF_RETRY_COUNT,
                SWEEP_PERIOD,
                SWEEP_PAGE_SIZE,
                MonitoringSettings.MONITORING_ENABLED,
                MonitoringSettings.INPUT_TIMEOUT,
                MonitoringSettings.INDEX_TIMEOUT,
                MonitoringSettings.BULK_TIMEOUT,
                MonitoringSettings.ALERT_BACKOFF_MILLIS,
                MonitoringSettings.ALERT_BACKOFF_COUNT,
                MonitoringSettings.ALERT_HISTORY_ROLLOVER_PERIOD,
                MonitoringSettings.ALERT_HISTORY_INDEX_MAX_AGE,
                MonitoringSettings.ALERT_HISTORY_MAX_DOCS)
    }

    override fun onIndexModule(indexModule: IndexModule) {
        if (indexModule.index.name == ScheduledJob.SCHEDULED_JOBS_INDEX) {
            indexModule.addIndexOperationListener(sweeper)
        }
    }

    override fun getContexts(): List<ScriptContext<*>> {
        return listOf(TriggerScript.CONTEXT)
    }

    override fun getExecutorBuilders(settings: Settings): List<ExecutorBuilder<*>> {
        return listOf(MonitorRunner.executorBuilder(settings))
    }
}
