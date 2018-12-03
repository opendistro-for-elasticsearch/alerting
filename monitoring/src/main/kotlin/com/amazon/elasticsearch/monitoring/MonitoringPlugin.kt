/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.ScheduledJobIndices
import com.amazon.elasticsearch.JobSweeper
import com.amazon.elasticsearch.Settings.REQUEST_TIMEOUT
import com.amazon.elasticsearch.Settings.SWEEP_BACKOFF_MILLIS
import com.amazon.elasticsearch.Settings.SWEEP_BACKOFF_RETRY_COUNT
import com.amazon.elasticsearch.Settings.SWEEP_PAGE_SIZE
import com.amazon.elasticsearch.Settings.SWEEP_PERIOD
import com.amazon.elasticsearch.action.node.ScheduledJobsStatsTransportAction
import com.amazon.elasticsearch.action.node.ScheduledJobsStatsAction
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.TestAction
import com.amazon.elasticsearch.monitoring.resthandler.RestDeleteMonitorAction
import com.amazon.elasticsearch.monitoring.resthandler.RestGetMonitorAction
import com.amazon.elasticsearch.monitoring.resthandler.RestIndexMonitorAction
import com.amazon.elasticsearch.monitoring.resthandler.RestSearchMonitorAction
import com.amazon.elasticsearch.monitoring.script.TriggerScript
import com.amazon.elasticsearch.monitoring.resthandler.RestAcknowledgeAlertAction
import com.amazon.elasticsearch.monitoring.resthandler.RestDisableMonitoringAction
import com.amazon.elasticsearch.monitoring.settings.MonitoringSettings
import com.amazon.elasticsearch.monitoring.resthandler.RestExecuteMonitorAction
import com.amazon.elasticsearch.schedule.JobScheduler
import com.amazon.elasticsearch.resthandler.RestScheduledJobStatsHandler
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
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.util.concurrent.EsExecutors
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
import org.elasticsearch.threadpool.FixedExecutorBuilder
import org.elasticsearch.threadpool.ScalingExecutorBuilder
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
        val whitelist = WhitelistLoader.loadFromResourceFiles(javaClass, "com.amazon.elasticsearch.monitoring.txt")
        return mapOf(TriggerScript.CONTEXT to listOf(whitelist))
    }

    companion object {
        @JvmField val KIBANA_USER_AGENT = "Kibana"
        @JvmField val UI_METADATA_EXCLUDE = arrayOf("monitor.${Monitor.UI_METADATA_FIELD}")
        @JvmField val MONITOR_BASE_URI = "/_awses/monitors/"
        @JvmField val MONITOR_RUNNER_THREAD_POOL_NAME = "aes_monitor_runner"
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
                RestDeleteMonitorAction(settings, restController, threadPool, alertIndices),
                RestIndexMonitorAction(settings, restController, scheduledJobIndices),
                RestSearchMonitorAction(settings, restController),
                RestExecuteMonitorAction(settings, restController, runner),
                RestAcknowledgeAlertAction(settings, restController),
                RestDisableMonitoringAction(settings, restController),
                RestScheduledJobStatsHandler(settings, restController, "_monitors"))
    }

    override fun getActions(): List<ActionPlugin.ActionHandler<out ActionRequest, out ActionResponse>> {
        return listOf(ActionPlugin.ActionHandler(ScheduledJobsStatsAction.INSTANCE, ScheduledJobsStatsTransportAction::class.java))
    }

    override fun getNamedXContent(): List<NamedXContentRegistry.Entry> {
        return listOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY,
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
        scheduledJobIndices = ScheduledJobIndices(client.admin(), settings, clusterService)
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

    override fun getExecutorBuilders(settings: Settings): MutableList<ExecutorBuilder<*>> {
        val availableProcessors = EsExecutors.numberOfProcessors(settings)
        // Use the same setting as ES GENERIC Executor builder.
        val genericThreadPoolMax = Math.min(512, Math.max(128, 4 * availableProcessors))
        val monitorRunnerExecutor = ScalingExecutorBuilder(MONITOR_RUNNER_THREAD_POOL_NAME, 4, genericThreadPoolMax, TimeValue.timeValueSeconds(30L))
        return mutableListOf(monitorRunnerExecutor)
    }
}
