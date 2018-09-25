/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch

import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitor.Monitor
import com.amazon.elasticsearch.resthandler.RestDeleteMonitorAction
import com.amazon.elasticsearch.resthandler.RestGetMonitorAction
import com.amazon.elasticsearch.resthandler.RestIndexMonitorAction
import com.amazon.elasticsearch.schedule.JobSweeper
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
import org.elasticsearch.plugins.ActionPlugin
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestHandler
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
class MonitoringPlugin : ActionPlugin, Plugin() {

    companion object {
        @JvmField val KIBANA_USER_AGENT = "Kibana"
        @JvmField val UI_METADATA_EXCLUDE = arrayOf("monitor.${Monitor.UI_METADATA_FIELD}")
    }

    override fun getRestHandlers(settings: Settings,
                                 restController: RestController,
                                 clusterSettings: ClusterSettings,
                                 indexScopedSettings: IndexScopedSettings,
                                 settingsFilter: SettingsFilter,
                                 indexNameExpressionResolver: IndexNameExpressionResolver?,
                                 nodesInCluster: Supplier<DiscoveryNodes>): List<RestHandler> {
        return listOf(RestGetMonitorAction(settings, restController),
                RestDeleteMonitorAction(settings, restController),
                RestIndexMonitorAction(settings, restController))
    }

    override fun getNamedXContent(): List<NamedXContentRegistry.Entry> {
        return listOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY,
                SNSAction.XCONTENT_REGISTRY)
    }

    override fun createComponents(client: Client, clusterService: ClusterService,
                                  threadPool: ThreadPool, resourceWatcherService: ResourceWatcherService,
                                  scriptService: ScriptService, xContentRegistry: NamedXContentRegistry,
                                  environment: Environment, nodeEnvironment: NodeEnvironment,
                                  namedWriteableRegistry: NamedWriteableRegistry): List<Any> {
        return listOf(JobSweeper(client, threadPool, "MonitorJobSweeper", xContentRegistry))
    }
}