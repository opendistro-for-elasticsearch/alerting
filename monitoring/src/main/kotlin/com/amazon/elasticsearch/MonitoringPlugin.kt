/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch

import com.amazon.elasticsearch.resthandler.RestDeleteMonitorAction
import com.amazon.elasticsearch.resthandler.RestGetMonitorAction
import com.amazon.elasticsearch.resthandler.RestIndexMonitorAction
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.common.settings.ClusterSettings
import org.elasticsearch.common.settings.IndexScopedSettings
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.settings.SettingsFilter
import org.elasticsearch.plugins.ActionPlugin
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestHandler
import java.util.function.Supplier

/**
 * The entry point for the Monitoring plugin.
 * This class registers the following handlers:
 * 1. Get Monitors(s)
 * 2. Create (put) Monitor.
 * 3. Delete Monitor.
 * 4. Test Monitor.
 */
class MonitoringPlugin : ActionPlugin, Plugin() {

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
}