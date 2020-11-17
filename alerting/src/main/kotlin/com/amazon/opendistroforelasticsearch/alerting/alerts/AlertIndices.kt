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

package com.amazon.opendistroforelasticsearch.alerting.alerts

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices.Companion.ALERT_INDEX
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices.Companion.HISTORY_WRITE_INDEX
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_HISTORY_INDEX_MAX_AGE
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_HISTORY_MAX_DOCS
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_HISTORY_ROLLOVER_PERIOD
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.REQUEST_TIMEOUT
import org.apache.logging.log4j.LogManager
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.suspendUntil
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.ALERT_HISTORY_ENABLED
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import org.apache.lucene.index.IndexNotFoundException
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest
import org.elasticsearch.action.admin.indices.alias.Alias
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest
import org.elasticsearch.action.support.IndicesOptions
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.ClusterChangedEvent
import org.elasticsearch.cluster.ClusterStateListener
import org.elasticsearch.cluster.LocalNodeMasterListener
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.threadpool.Scheduler.Cancellable
import org.elasticsearch.threadpool.ThreadPool
import java.time.Instant

/**
 * Class to manage the creation and rollover of alert indices and alert history indices.  In progress alerts are stored
 * in [ALERT_INDEX].  Completed alerts are written to [HISTORY_WRITE_INDEX] which is an alias that points at the
 * current index to which completed alerts are written. [HISTORY_WRITE_INDEX] is periodically rolled over to a new
 * date based index. The frequency of rolling over indices is controlled by the `opendistro.alerting.alert_rollover_period` setting.
 *
 * These indexes are created when first used and are then rolled over every `alert_rollover_period`. The rollover is
 * initiated on the master node to ensure only a single node tries to roll it over.  Once we have a curator functionality
 * in Scheduled Jobs we can migrate to using that to rollover the index.
 */
class AlertIndices(
    settings: Settings,
    private val client: Client,
    private val threadPool: ThreadPool,
    private val clusterService: ClusterService
) : LocalNodeMasterListener, ClusterStateListener {

    init {
        clusterService.addListener(this)
        clusterService.addLocalNodeMasterListener(this)
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERT_HISTORY_ENABLED) { historyEnabled = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERT_HISTORY_MAX_DOCS) { historyMaxDocs = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERT_HISTORY_INDEX_MAX_AGE) { historyMaxAge = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALERT_HISTORY_ROLLOVER_PERIOD) {
            historyRolloverPeriod = it
            rescheduleRollover()
        }
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.ALERT_HISTORY_RETENTION_PERIOD) {
            historyRetentionPeriod = it
        }
        clusterService.clusterSettings.addSettingsUpdateConsumer(REQUEST_TIMEOUT) { requestTimeout = it }
    }

    companion object {

        /** The in progress alert history index. */
        const val ALERT_INDEX = ".opendistro-alerting-alerts"

        /** The Elastic mapping type */
        const val MAPPING_TYPE = "_doc"

        /** The alias of the index in which to write alert history */
        const val HISTORY_WRITE_INDEX = ".opendistro-alerting-alert-history-write"

        /** The index name pattern referring to all alert history indices */
        const val HISTORY_ALL = ".opendistro-alerting-alert-history*"

        /** The index name pattern to create alert history indices */
        const val HISTORY_INDEX_PATTERN = "<.opendistro-alerting-alert-history-{now/d}-1>"

        /** The index name pattern to query all alerts, history and current alerts. */
        const val ALL_INDEX_PATTERN = ".opendistro-alerting-alert*"

        @JvmStatic
        fun alertMapping() =
                AlertIndices::class.java.getResource("alert_mapping.json").readText()

        private val logger = LogManager.getLogger(AlertIndices::class.java)
    }

    @Volatile private var historyEnabled = AlertingSettings.ALERT_HISTORY_ENABLED.get(settings)

    @Volatile private var historyMaxDocs = AlertingSettings.ALERT_HISTORY_MAX_DOCS.get(settings)

    @Volatile private var historyMaxAge = AlertingSettings.ALERT_HISTORY_INDEX_MAX_AGE.get(settings)

    @Volatile private var historyRolloverPeriod = AlertingSettings.ALERT_HISTORY_ROLLOVER_PERIOD.get(settings)

    @Volatile private var historyRetentionPeriod = AlertingSettings.ALERT_HISTORY_RETENTION_PERIOD.get(settings)

    @Volatile private var requestTimeout = AlertingSettings.REQUEST_TIMEOUT.get(settings)

    // for JobsMonitor to report
    var lastRolloverTime: TimeValue? = null

    private var historyIndexInitialized: Boolean = false

    private var alertIndexInitialized: Boolean = false

    private var scheduledRollover: Cancellable? = null

    override fun onMaster() {
        try {
            // TODO: Change current actionGet requests within rolloverHistoryIndex() rolloverAndDeleteHistoryIndices() to use suspendUntil
            // try to rollover immediately as we might be restarting the cluster
            rolloverHistoryIndex()
            // schedule the next rollover for approx MAX_AGE later
            scheduledRollover = threadPool
                    .scheduleWithFixedDelay({ rolloverAndDeleteHistoryIndices() }, historyRolloverPeriod, executorName())
        } catch (e: Exception) {
            // This should be run on cluster startup
            logger.error("Error creating alert indices. " +
                    "Alerts can't be recorded until master node is restarted.", e)
        }
    }

    override fun offMaster() {
        scheduledRollover?.cancel()
    }

    override fun executorName(): String {
        return ThreadPool.Names.MANAGEMENT
    }

    override fun clusterChanged(event: ClusterChangedEvent) {
        // if the indexes have been deleted they need to be reinitalized
        alertIndexInitialized = event.state().routingTable().hasIndex(ALERT_INDEX)
        historyIndexInitialized = event.state().metadata().hasAlias(HISTORY_WRITE_INDEX)
    }

    private fun rescheduleRollover() {
        if (clusterService.state().nodes.isLocalNodeElectedMaster) {
            scheduledRollover?.cancel()
            scheduledRollover = threadPool
                    .scheduleWithFixedDelay({ rolloverAndDeleteHistoryIndices() }, historyRolloverPeriod, executorName())
        }
    }

    fun isInitialized(): Boolean {
        return alertIndexInitialized && historyIndexInitialized
    }

    fun isHistoryEnabled(): Boolean = historyEnabled

    suspend fun createOrUpdateAlertIndex() {
        if (!alertIndexInitialized) {
            alertIndexInitialized = createIndex(ALERT_INDEX)
            if (alertIndexInitialized) IndexUtils.alertIndexUpdated()
        } else {
            if (!IndexUtils.alertIndexUpdated) updateIndexMapping(ALERT_INDEX)
        }
        alertIndexInitialized
    }

    suspend fun createOrUpdateInitialHistoryIndex() {
        if (!historyIndexInitialized) {
            historyIndexInitialized = createIndex(HISTORY_INDEX_PATTERN, HISTORY_WRITE_INDEX)
            if (historyIndexInitialized)
                IndexUtils.lastUpdatedHistoryIndex = IndexUtils.getIndexNameWithAlias(clusterService.state(), HISTORY_WRITE_INDEX)
        } else {
            updateIndexMapping(HISTORY_WRITE_INDEX, true)
        }
        historyIndexInitialized
    }

    private suspend fun createIndex(index: String, alias: String? = null): Boolean {
        // This should be a fast check of local cluster state. Should be exceedingly rare that the local cluster
        // state does not contain the index and multiple nodes concurrently try to create the index.
        // If it does happen that error is handled we catch the ResourceAlreadyExistsException
        val existsResponse: IndicesExistsResponse = client.admin().indices().suspendUntil {
            exists(IndicesExistsRequest(index).local(true), it)
        }
        if (existsResponse.isExists) return true

        val request = CreateIndexRequest(index)
                .mapping(MAPPING_TYPE, alertMapping(), XContentType.JSON)
                .settings(Settings.builder().put("index.hidden", true).build())

        if (alias != null) request.alias(Alias(alias))
        return try {
            val createIndexResponse: CreateIndexResponse = client.admin().indices().suspendUntil { create(request, it) }
            createIndexResponse.isAcknowledged
        } catch (e: ResourceAlreadyExistsException) {
            true
        }
    }

    private suspend fun updateIndexMapping(index: String, alias: Boolean = false) {
        val clusterState = clusterService.state()
        val mapping = alertMapping()
        var targetIndex = index
        if (alias) {
            targetIndex = IndexUtils.getIndexNameWithAlias(clusterState, index)
        }

        if (targetIndex == IndexUtils.lastUpdatedHistoryIndex) {
            return
        }

        var putMappingRequest: PutMappingRequest = PutMappingRequest(targetIndex).type(MAPPING_TYPE)
                .source(mapping, XContentType.JSON)
        val updateResponse: AcknowledgedResponse = client.admin().indices().suspendUntil { putMapping(putMappingRequest, it) }
        if (updateResponse.isAcknowledged) {
            logger.info("Index mapping of $targetIndex is updated")
            setIndexUpdateFlag(index, targetIndex)
        } else {
            logger.info("Failed to update index mapping of $targetIndex")
        }
    }

    private fun setIndexUpdateFlag(index: String, targetIndex: String) {
        when (index) {
            ALERT_INDEX -> IndexUtils.alertIndexUpdated()
            HISTORY_WRITE_INDEX -> IndexUtils.lastUpdatedHistoryIndex = targetIndex
        }
    }

    private fun rolloverAndDeleteHistoryIndices() {
        if (historyEnabled) rolloverHistoryIndex()
        deleteOldHistoryIndices()
    }

    private fun rolloverHistoryIndex(): Boolean {
        if (!historyIndexInitialized) {
            return false
        }

        // We have to pass null for newIndexName in order to get Elastic to increment the index count.
        val request = RolloverRequest(HISTORY_WRITE_INDEX, null)
        request.createIndexRequest.index(HISTORY_INDEX_PATTERN)
                .mapping(MAPPING_TYPE, alertMapping(), XContentType.JSON)
                .settings(Settings.builder().put("index.hidden", true).build())
        request.addMaxIndexDocsCondition(historyMaxDocs)
        request.addMaxIndexAgeCondition(historyMaxAge)
        val response = client.admin().indices().rolloverIndex(request).actionGet(requestTimeout)
        if (!response.isRolledOver) {
            logger.info("$HISTORY_WRITE_INDEX not rolled over. Conditions were: ${response.conditionStatus}")
        } else {
            lastRolloverTime = TimeValue.timeValueMillis(threadPool.absoluteTimeInMillis())
        }
        return response.isRolledOver
    }

    private fun deleteOldHistoryIndices() {
        val indicesToDelete = mutableListOf<String>()

        val clusterStateRequest = ClusterStateRequest()
                .clear()
                .indices(HISTORY_ALL)
                .metadata(true)
                .local(true)
                .indicesOptions(IndicesOptions.strictExpand())

        val clusterStateResponse = client.admin().cluster().state(clusterStateRequest).actionGet()

        for (entry in clusterStateResponse.state.metadata.indices) {
            val indexMetaData = entry.value
            val creationTime = indexMetaData.creationDate

            if ((Instant.now().toEpochMilli() - creationTime) > historyRetentionPeriod.millis) {
                val alias = indexMetaData.aliases.firstOrNull { HISTORY_WRITE_INDEX == it.value.alias }
                if (alias != null) {
                    if (historyEnabled) {
                        // If the index has the write alias and history is enabled, don't delete the index
                        continue
                    } else {
                        // Otherwise reset historyIndexInitialized since index will be deleted
                        historyIndexInitialized = false
                    }
                }

                indicesToDelete.add(indexMetaData.index.name)
            }
        }

        if (indicesToDelete.isNotEmpty()) {
            val deleteIndexRequest = DeleteIndexRequest(*indicesToDelete.toTypedArray())
            val deleteIndexResponse = client.admin().indices().delete(deleteIndexRequest).actionGet()

            if (!deleteIndexResponse.isAcknowledged) {
                logger.error("Could not delete one or more Alerting history indices: $indicesToDelete. Retrying one by one.")
                for (index in indicesToDelete) {
                    try {
                        val singleDeleteRequest = DeleteIndexRequest(*indicesToDelete.toTypedArray())
                        val singleDeleteResponse = client.admin().indices().delete(singleDeleteRequest).actionGet()

                        if (!singleDeleteResponse.isAcknowledged) {
                            logger.error("Could not delete one or more Alerting history indices: $index")
                        }
                    } catch (e: IndexNotFoundException) {
                        logger.debug("$index was already deleted. ${e.message}")
                    }
                }
            }
        }
    }
}
