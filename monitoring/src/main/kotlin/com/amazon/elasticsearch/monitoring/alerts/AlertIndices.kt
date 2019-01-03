/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.alerts

import com.amazon.elasticsearch.Settings.REQUEST_TIMEOUT
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices.Companion.ALERT_INDEX
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices.Companion.HISTORY_WRITE_INDEX
import com.amazon.elasticsearch.monitoring.settings.MonitoringSettings
import com.amazon.elasticsearch.util.ElasticAPI
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.action.admin.indices.alias.Alias
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest
import org.elasticsearch.client.IndicesAdminClient
import org.elasticsearch.cluster.ClusterChangedEvent
import org.elasticsearch.cluster.ClusterStateListener
import org.elasticsearch.cluster.LocalNodeMasterListener
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.threadpool.Scheduler.Cancellable
import org.elasticsearch.threadpool.ThreadPool

/**
 * Class to manage the creation and rollover of alert indices and alert history indices.  In progress alerts are stored
 * in [ALERT_INDEX].  Completed alerts are written to [HISTORY_WRITE_INDEX] which is an alias that points at the
 * current index to which completed alerts are written. [HISTORY_WRITE_INDEX] is periodically rolled over to a new
 * date based index. The frequency of rolling over indices is controlled by the `aes.monitoring.alert_rollover_period` setting.
 *
 * These indexes are created when first used and are then rolled over every `alert_rollover_period`. The rollover is
 * initiated on the master node to ensure only a single node tries to roll it over.  Once we have a curator functionality
 * in Scheduled Jobs we can migrate to using that to rollover the index.
 */
class AlertIndices(private val settings : Settings, private val client: IndicesAdminClient,
                   private val threadPool: ThreadPool) : LocalNodeMasterListener, ClusterStateListener {

    private val logger = ElasticAPI.INSTANCE.getLogger(AlertIndices::class.java, settings)

    companion object {

        /** The in progress alert history index. */
        const val ALERT_INDEX = ".aes-alerts"

        /** The Elastic mapping type */
        const val MAPPING_TYPE = "_doc"

        /** The alias of the index in which to write alert history */
        const val HISTORY_WRITE_INDEX = ".aes-alert-history-write"

        /** The index name pattern to query all the alert history indices */
        const val HISTORY_INDEX_PATTERN = "<.aes-alert-history-{now/d}-1>"

        /** The index name pattern to query all alerts, history and current alerts. */
        const val ALL_INDEX_PATTERN = ".aes-alert*"

        @JvmStatic
        fun alertMapping() =
                AlertIndices::class.java.getResource("alert_mapping.json").readText()
    }

    val HISTORY_INDEX_MAX_DOCS_SETTING = MonitoringSettings.ALERT_HISTORY_MAX_DOCS.get(settings)

    val HISTORY_INDEX_MAX_AGE_SETTING = MonitoringSettings.ALERT_HISTORY_INDEX_MAX_AGE.get(settings)

    val HISTORY_INDEX_ROLLOVER_PERIOD_SETTING = MonitoringSettings.ALERT_HISTORY_ROLLOVER_PERIOD.get(settings)

    private val DEFAULT_TIMEOUT = REQUEST_TIMEOUT.get(settings)

    // for JobsMonitor to report
    var lastRolloverTime : TimeValue? = null

    private var historyIndexInitialized: Boolean = false

    private var alertIndexInitialized : Boolean = false

    private var scheduledRollover : Cancellable? = null

    override fun onMaster() {
        try {
            // try to rollover immediately as we might be restarting the cluster
            rolloverHistoryIndex()
            // schedule the next rollover for approx MAX_AGE later
            scheduledRollover = threadPool.scheduleWithFixedDelay({ rolloverHistoryIndex() },
                    HISTORY_INDEX_ROLLOVER_PERIOD_SETTING, executorName())
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
        historyIndexInitialized = event.state().metaData().hasAlias(HISTORY_WRITE_INDEX)
    }

    fun isInitialized() : Boolean {
        return alertIndexInitialized && historyIndexInitialized
    }

    fun createAlertIndex() {
        if (!alertIndexInitialized) {
            alertIndexInitialized = createIndex(ALERT_INDEX)
        }
        alertIndexInitialized
    }

    fun createInitialHistoryIndex() {
        if (!historyIndexInitialized) {
            historyIndexInitialized = createIndex(HISTORY_INDEX_PATTERN, HISTORY_WRITE_INDEX)
        }
        historyIndexInitialized
    }

    private fun createIndex(index: String, alias: String? = null) : Boolean {
        // This should be a fast check of local cluster state. Should be exceedingly rare that the local cluster
        // state does not contain the index and multiple nodes concurrently try to create the index.
        // If it does happen that error is handled we catch the ResourceAlreadyExistsException
        val exists = client.exists(IndicesExistsRequest(index).local(true)).actionGet(DEFAULT_TIMEOUT).isExists
        if (exists) return true

        val request = CreateIndexRequest(index).mapping(MAPPING_TYPE, alertMapping(), XContentType.JSON)
        if (alias != null) request.alias(Alias(alias))
        return try {
            client.create(request).actionGet(DEFAULT_TIMEOUT).isAcknowledged
        } catch (e : ResourceAlreadyExistsException) {
            true
        }
    }

    fun rolloverHistoryIndex(): Boolean {
        if (!historyIndexInitialized) {
            return false
        }

        // We have to pass null for newIndexName in order to get Elastic to increment the index count.
        val request = RolloverRequest(HISTORY_WRITE_INDEX, null)
        ElasticAPI.INSTANCE.getCreateIndexRequest(request).index(HISTORY_INDEX_PATTERN)
                .mapping(MAPPING_TYPE, alertMapping(), XContentType.JSON)
        request.addMaxIndexDocsCondition(HISTORY_INDEX_MAX_DOCS_SETTING)
        request.addMaxIndexAgeCondition(HISTORY_INDEX_MAX_AGE_SETTING)
        val response = client.rolloversIndex(request).actionGet(DEFAULT_TIMEOUT)
        if (!response.isRolledOver) {
            logger.info("$HISTORY_WRITE_INDEX not rolled over. Conditions were: ${response.conditionStatus}")
        } else {
            lastRolloverTime = TimeValue.timeValueMillis(threadPool.absoluteTimeInMillis())
        }
        return response.isRolledOver
    }

}
