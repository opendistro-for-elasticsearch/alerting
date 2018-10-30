/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.ScheduledJobIndices
import com.amazon.elasticsearch.JobSweeper
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.resthandler.RestDeleteMonitorAction
import com.amazon.elasticsearch.monitoring.resthandler.RestExecuteMonitorAction
import com.amazon.elasticsearch.monitoring.resthandler.RestGetMonitorAction
import com.amazon.elasticsearch.monitoring.resthandler.RestIndexMonitorAction
import com.amazon.elasticsearch.monitoring.resthandler.RestSearchMonitorAction
import com.amazon.elasticsearch.monitoring.script.TriggerScript
import com.amazon.elasticsearch.monitoring.resthandler.RestAcknowledgeAlertAction
import com.amazon.elasticsearch.schedule.JobScheduler
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.io.stream.NamedWriteableRegistry
import org.elasticsearch.common.settings.ClusterSettings
import org.elasticsearch.common.settings.IndexScopedSettings
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
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.watcher.ResourceWatcherService
import java.util.function.Supplier

/**
 * Entry point of the Amazon Elasticsearch monitoring plugin
 * This class initializes the [RestGetMonitorAction], [RestDeleteMonitorAction], [RestIndexMonitorAction] rest handlers.
 * It also adds [Monitor.XCONTENT_REGISTRY], [SearchInput.XCONTENT_REGISTRY], [SNSAction.XCONTENT_REGISTRY] to the
 * [NamedXContentRegistry] so that we are able to deserialize the custom named objects.
 */
class MonitoringPlugin : PainlessExtension, ActionPlugin, ScriptPlugin, Plugin() {

    override fun getContextWhitelists(): Map<ScriptContext<*>, List<Whitelist>> {
        val whitelist = WhitelistLoader.loadFromResourceFiles(javaClass, "com.amazon.elasticsearch.monitoring.txt")
        return mapOf(TriggerScript.CONTEXT to listOf(whitelist))
    }

    companion object {
        @JvmField val KIBANA_USER_AGENT = "Kibana"
        @JvmField val UI_METADATA_EXCLUDE = arrayOf("monitor.${Monitor.UI_METADATA_FIELD}")
        @JvmField val MONITOR_BASE_URI = "/_awses/monitors/"
    }

    lateinit var runner: MonitorRunner
    lateinit var scheduler: JobScheduler
    lateinit var sweeper: JobSweeper
    lateinit var scheduledJobIndices: ScheduledJobIndices

    override fun getRestHandlers(settings: Settings,
                                 restController: RestController,
                                 clusterSettings: ClusterSettings,
                                 indexScopedSettings: IndexScopedSettings,
                                 settingsFilter: SettingsFilter,
                                 indexNameExpressionResolver: IndexNameExpressionResolver?,
                                 nodesInCluster: Supplier<DiscoveryNodes>): List<RestHandler> {
        return listOf(RestGetMonitorAction(settings, restController),
                RestDeleteMonitorAction(settings, restController),
                RestIndexMonitorAction(settings, restController, scheduledJobIndices),
                RestSearchMonitorAction(settings, restController),
                RestExecuteMonitorAction(settings, restController, runner),
                RestAcknowledgeAlertAction(settings, restController))
    }

    override fun getNamedXContent(): List<NamedXContentRegistry.Entry> {
        return listOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY,
                SNSAction.XCONTENT_REGISTRY)
    }

    override fun createComponents(client: Client, clusterService: ClusterService, threadPool: ThreadPool,
                                  resourceWatcherService: ResourceWatcherService, scriptService: ScriptService,
                                  xContentRegistry: NamedXContentRegistry, environment: Environment,
                                  nodeEnvironment: NodeEnvironment,
                                  namedWriteableRegistry: NamedWriteableRegistry): Collection<Any> {
        // Need to figure out how to use the Elasticsearch DI classes rather than handwiring things here.
        val settings = environment.settings()
        val alertIndices = AlertIndices(settings, client.admin().indices(), threadPool)
        runner = MonitorRunner(settings, client, threadPool, scriptService, xContentRegistry, alertIndices)
        scheduler = JobScheduler(threadPool, runner)
        sweeper = JobSweeper(environment.settings(), client, clusterService, threadPool, xContentRegistry, scheduler)
        scheduledJobIndices = ScheduledJobIndices(client.admin(), settings, clusterService)
        return listOf(sweeper, scheduler, runner)
    }

    override fun onIndexModule(indexModule: IndexModule) {
        if (indexModule.index.name == ScheduledJob.SCHEDULED_JOBS_INDEX) {
            indexModule.addIndexOperationListener(sweeper)
        }
    }

    override fun getContexts(): List<ScriptContext<*>> {
        return listOf(TriggerScript.CONTEXT)
    }
}
