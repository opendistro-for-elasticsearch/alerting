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

import com.amazon.opendistroforelasticsearch.alerting.action.AcknowledgeAlertAction
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.ExecuteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetDestinationsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.SearchEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.SearchEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.SearchMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext.BucketSelectorExtAggregationBuilder
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.core.JobSweeper
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.action.node.ScheduledJobsStatsAction
import com.amazon.opendistroforelasticsearch.alerting.core.action.node.ScheduledJobsStatsTransportAction
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.core.resthandler.RestScheduledJobStatsHandler
import com.amazon.opendistroforelasticsearch.alerting.core.schedule.JobScheduler
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTrigger
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestAcknowledgeAlertAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestDeleteDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestDeleteEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestDeleteEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestDeleteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestExecuteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestGetAlertsAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestGetDestinationsAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestGetEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestGetEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestGetMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestIndexDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestIndexEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestIndexEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestIndexMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestSearchEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestSearchEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.resthandler.RestSearchMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.script.TriggerScript
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportAcknowledgeAlertAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportDeleteDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportDeleteEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportDeleteEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportDeleteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportExecuteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportGetAlertsAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportGetDestinationsAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportGetEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportGetEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportGetMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportIndexDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportIndexEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportIndexEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportIndexMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportSearchEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportSearchEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.transport.TransportSearchMonitorAction
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.io.stream.NamedWriteableRegistry
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.settings.ClusterSettings
import org.elasticsearch.common.settings.IndexScopedSettings
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.settings.SettingsFilter
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.env.Environment
import org.elasticsearch.env.NodeEnvironment
import org.elasticsearch.index.IndexModule
import org.elasticsearch.painless.spi.PainlessExtension
import org.elasticsearch.painless.spi.Whitelist
import org.elasticsearch.painless.spi.WhitelistLoader
import org.elasticsearch.plugins.ActionPlugin
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.plugins.ReloadablePlugin
import org.elasticsearch.plugins.ScriptPlugin
import org.elasticsearch.plugins.SearchPlugin
import org.elasticsearch.repositories.RepositoriesService
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestHandler
import org.elasticsearch.script.ScriptContext
import org.elasticsearch.script.ScriptService
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.watcher.ResourceWatcherService
import java.util.function.Supplier

/**
 * Entry point of the OpenDistro for Elasticsearch alerting plugin
 * This class initializes the [RestGetMonitorAction], [RestDeleteMonitorAction], [RestIndexMonitorAction] rest handlers.
 * It also adds [Monitor.XCONTENT_REGISTRY], [SearchInput.XCONTENT_REGISTRY], [TraditionalTrigger.XCONTENT_REGISTRY],
 * [AggregationTrigger.XCONTENT_REGISTRY] to the [NamedXContentRegistry] so that we are able to deserialize the custom named objects.
 */
internal class AlertingPlugin : PainlessExtension, ActionPlugin, ScriptPlugin, ReloadablePlugin, SearchPlugin, Plugin() {
    override fun getContextWhitelists(): Map<ScriptContext<*>, List<Whitelist>> {
        val whitelist = WhitelistLoader.loadFromResourceFiles(javaClass, "com.amazon.opendistroforelasticsearch.alerting.txt")
        return mapOf(TriggerScript.CONTEXT to listOf(whitelist))
    }

    companion object {
        @JvmField val KIBANA_USER_AGENT = "Kibana"
        @JvmField val UI_METADATA_EXCLUDE = arrayOf("monitor.${Monitor.UI_METADATA_FIELD}")
        @JvmField val MONITOR_BASE_URI = "/_opendistro/_alerting/monitors"
        @JvmField val DESTINATION_BASE_URI = "/_opendistro/_alerting/destinations"
        @JvmField val EMAIL_ACCOUNT_BASE_URI = "$DESTINATION_BASE_URI/email_accounts"
        @JvmField val EMAIL_GROUP_BASE_URI = "$DESTINATION_BASE_URI/email_groups"
        @JvmField val ALERTING_JOB_TYPES = listOf("monitor")
    }

    lateinit var runner: MonitorRunner
    lateinit var scheduler: JobScheduler
    lateinit var sweeper: JobSweeper
    lateinit var scheduledJobIndices: ScheduledJobIndices
    lateinit var threadPool: ThreadPool
    lateinit var alertIndices: AlertIndices
    lateinit var clusterService: ClusterService

    override fun getRestHandlers(
        settings: Settings,
        restController: RestController,
        clusterSettings: ClusterSettings,
        indexScopedSettings: IndexScopedSettings,
        settingsFilter: SettingsFilter,
        indexNameExpressionResolver: IndexNameExpressionResolver?,
        nodesInCluster: Supplier<DiscoveryNodes>
    ): List<RestHandler> {
        return listOf(RestGetMonitorAction(),
                RestDeleteMonitorAction(),
                RestIndexMonitorAction(),
                RestSearchMonitorAction(settings, clusterService),
                RestExecuteMonitorAction(),
                RestAcknowledgeAlertAction(),
                RestScheduledJobStatsHandler("_alerting"),
                RestIndexDestinationAction(),
                RestDeleteDestinationAction(),
                RestIndexEmailAccountAction(),
                RestDeleteEmailAccountAction(),
                RestSearchEmailAccountAction(),
                RestGetEmailAccountAction(),
                RestIndexEmailGroupAction(),
                RestDeleteEmailGroupAction(),
                RestSearchEmailGroupAction(),
                RestGetEmailGroupAction(),
                RestGetDestinationsAction(),
                RestGetAlertsAction()
        )
    }

    override fun getActions(): List<ActionPlugin.ActionHandler<out ActionRequest, out ActionResponse>> {
        return listOf(
            ActionPlugin.ActionHandler(ScheduledJobsStatsAction.INSTANCE, ScheduledJobsStatsTransportAction::class.java),
            ActionPlugin.ActionHandler(IndexDestinationAction.INSTANCE, TransportIndexDestinationAction::class.java),
            ActionPlugin.ActionHandler(IndexMonitorAction.INSTANCE, TransportIndexMonitorAction::class.java),
            ActionPlugin.ActionHandler(GetMonitorAction.INSTANCE, TransportGetMonitorAction::class.java),
            ActionPlugin.ActionHandler(ExecuteMonitorAction.INSTANCE, TransportExecuteMonitorAction::class.java),
            ActionPlugin.ActionHandler(SearchMonitorAction.INSTANCE, TransportSearchMonitorAction::class.java),
            ActionPlugin.ActionHandler(DeleteMonitorAction.INSTANCE, TransportDeleteMonitorAction::class.java),
            ActionPlugin.ActionHandler(DeleteDestinationAction.INSTANCE, TransportDeleteDestinationAction::class.java),
            ActionPlugin.ActionHandler(AcknowledgeAlertAction.INSTANCE, TransportAcknowledgeAlertAction::class.java),
            ActionPlugin.ActionHandler(IndexEmailAccountAction.INSTANCE, TransportIndexEmailAccountAction::class.java),
            ActionPlugin.ActionHandler(GetEmailAccountAction.INSTANCE, TransportGetEmailAccountAction::class.java),
            ActionPlugin.ActionHandler(SearchEmailAccountAction.INSTANCE, TransportSearchEmailAccountAction::class.java),
            ActionPlugin.ActionHandler(DeleteEmailAccountAction.INSTANCE, TransportDeleteEmailAccountAction::class.java),
            ActionPlugin.ActionHandler(IndexEmailGroupAction.INSTANCE, TransportIndexEmailGroupAction::class.java),
            ActionPlugin.ActionHandler(GetEmailGroupAction.INSTANCE, TransportGetEmailGroupAction::class.java),
            ActionPlugin.ActionHandler(SearchEmailGroupAction.INSTANCE, TransportSearchEmailGroupAction::class.java),
            ActionPlugin.ActionHandler(DeleteEmailGroupAction.INSTANCE, TransportDeleteEmailGroupAction::class.java),
            ActionPlugin.ActionHandler(GetDestinationsAction.INSTANCE, TransportGetDestinationsAction::class.java),
            ActionPlugin.ActionHandler(GetAlertsAction.INSTANCE, TransportGetAlertsAction::class.java)
        )
    }

    override fun getNamedXContent(): List<NamedXContentRegistry.Entry> {
        return listOf(
            Monitor.XCONTENT_REGISTRY,
            SearchInput.XCONTENT_REGISTRY,
            TraditionalTrigger.XCONTENT_REGISTRY,
            AggregationTrigger.XCONTENT_REGISTRY)
    }

    override fun createComponents(
        client: Client,
        clusterService: ClusterService,
        threadPool: ThreadPool,
        resourceWatcherService: ResourceWatcherService,
        scriptService: ScriptService,
        xContentRegistry: NamedXContentRegistry,
        environment: Environment,
        nodeEnvironment: NodeEnvironment,
        namedWriteableRegistry: NamedWriteableRegistry,
        indexNameExpressionResolver: IndexNameExpressionResolver,
        repositoriesServiceSupplier: Supplier<RepositoriesService>
    ): Collection<Any> {
        // Need to figure out how to use the Elasticsearch DI classes rather than handwiring things here.
        val settings = environment.settings()
        alertIndices = AlertIndices(settings, client, threadPool, clusterService)
        runner = MonitorRunner
            .registerClusterService(clusterService)
            .registerClient(client)
            .registerNamedXContentRegistry(xContentRegistry)
            .registerScriptService(scriptService)
            .registerSettings(settings)
            .registerThreadPool(threadPool)
            .registerAlertIndices(alertIndices)
            .registerInputService(InputService(client, scriptService, xContentRegistry))
            .registerTriggerService(TriggerService(client, scriptService))
            .registerAlertService(AlertService(client, xContentRegistry, alertIndices))
            .registerConsumers()
            .registerDestinationSettings()
        scheduledJobIndices = ScheduledJobIndices(client.admin(), clusterService)
        scheduler = JobScheduler(threadPool, runner)
        sweeper = JobSweeper(environment.settings(), client, clusterService, threadPool, xContentRegistry, scheduler, ALERTING_JOB_TYPES)
        this.threadPool = threadPool
        this.clusterService = clusterService
        return listOf(sweeper, scheduler, runner, scheduledJobIndices)
    }

    override fun getSettings(): List<Setting<*>> {
        return listOf(
                ScheduledJobSettings.REQUEST_TIMEOUT,
                ScheduledJobSettings.SWEEP_BACKOFF_MILLIS,
                ScheduledJobSettings.SWEEP_BACKOFF_RETRY_COUNT,
                ScheduledJobSettings.SWEEP_PERIOD,
                ScheduledJobSettings.SWEEP_PAGE_SIZE,
                ScheduledJobSettings.SWEEPER_ENABLED,
                AlertingSettings.INPUT_TIMEOUT,
                AlertingSettings.INDEX_TIMEOUT,
                AlertingSettings.BULK_TIMEOUT,
                AlertingSettings.ALERT_BACKOFF_MILLIS,
                AlertingSettings.ALERT_BACKOFF_COUNT,
                AlertingSettings.MOVE_ALERTS_BACKOFF_MILLIS,
                AlertingSettings.MOVE_ALERTS_BACKOFF_COUNT,
                AlertingSettings.ALERT_HISTORY_ENABLED,
                AlertingSettings.ALERT_HISTORY_ROLLOVER_PERIOD,
                AlertingSettings.ALERT_HISTORY_INDEX_MAX_AGE,
                AlertingSettings.ALERT_HISTORY_MAX_DOCS,
                AlertingSettings.ALERT_HISTORY_RETENTION_PERIOD,
                AlertingSettings.ALERTING_MAX_MONITORS,
                AlertingSettings.REQUEST_TIMEOUT,
                AlertingSettings.MAX_ACTION_THROTTLE_VALUE,
                AlertingSettings.FILTER_BY_BACKEND_ROLES,
                DestinationSettings.EMAIL_USERNAME,
                DestinationSettings.EMAIL_PASSWORD,
                DestinationSettings.ALLOW_LIST,
                DestinationSettings.HOST_DENY_LIST
            )
    }

    override fun onIndexModule(indexModule: IndexModule) {
        if (indexModule.index.name == ScheduledJob.SCHEDULED_JOBS_INDEX) {
            indexModule.addIndexOperationListener(sweeper)
        }
    }

    override fun getContexts(): List<ScriptContext<*>> {
        return listOf(TriggerScript.CONTEXT)
    }

    override fun reload(settings: Settings) {
        runner.reloadDestinationSettings(settings)
    }

    override fun getPipelineAggregations(): List<SearchPlugin.PipelineAggregationSpec> {
        return listOf(
            SearchPlugin.PipelineAggregationSpec(
                BucketSelectorExtAggregationBuilder.NAME,
                { sin: StreamInput -> BucketSelectorExtAggregationBuilder(sin) },
                { parser: XContentParser, agg_name: String ->
                    BucketSelectorExtAggregationBuilder.parse(agg_name, parser)
                }
            )
        )
    }
}
