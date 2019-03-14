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

import com.amazon.opendistroforelasticsearch.alerting.MonitorRunner
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices.Companion.ALERT_INDEX
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices.Companion.HISTORY_WRITE_INDEX
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import org.apache.logging.log4j.Logger
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.VersionType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.threadpool.ThreadPool

/**
 * Class to manage the moving of active alerts when a monitor or trigger is deleted.
 *
 * The logic for moving alerts consists of:
 * 1. Find active alerts:
 *      a. matching monitorId if no monitor is provided (postDelete)
 *      b. matching monitorId and no triggerIds if monitor is provided (postIndex)
 * 2. Move alerts over to [HISTORY_WRITE_INDEX] as DELETED
 * 3. Delete alerts from [ALERT_INDEX]
 * 4. Schedule a retry if there were any failures
 */
class AlertMover(
    private val client: Client,
    private val threadPool: ThreadPool,
    private val monitorRunner: MonitorRunner,
    private val alertIndices: AlertIndices,
    private val backoff: Iterator<TimeValue>,
    private val logger: Logger,
    private val monitorId: String,
    private val monitor: Monitor? = null
) {

    private var hasFailures: Boolean = false

    fun run() {
        if (alertIndices.isInitialized()) {
            findActiveAlerts()
        }
    }

    private fun findActiveAlerts() {
        val boolQuery = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, monitorId))

        if (monitor != null) {
            boolQuery.mustNot(QueryBuilders.termsQuery(Alert.TRIGGER_ID_FIELD, monitor.triggers.map { it.id }))
        }

        val activeAlertsQuery = SearchSourceBuilder.searchSource()
                .query(boolQuery)
                .version(true)

        val activeAlertsRequest = SearchRequest(AlertIndices.ALERT_INDEX)
                .routing(monitorId)
                .source(activeAlertsQuery)
        client.search(activeAlertsRequest, ActionListener.wrap(::onSearchResponse, ::onFailure))
    }

    private fun onSearchResponse(response: SearchResponse) {
        // If no alerts are found, simply return
        if (response.hits.totalHits == 0L) return
        val indexRequests = response.hits.map { hit ->
            IndexRequest(AlertIndices.HISTORY_WRITE_INDEX, AlertIndices.MAPPING_TYPE)
                    .routing(monitorId)
                    .source(Alert.parse(alertContentParser(hit.sourceRef), hit.id, hit.version)
                            .copy(state = Alert.State.DELETED)
                            .toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                    .version(hit.version)
                    .versionType(VersionType.EXTERNAL_GTE)
                    .id(hit.id)
        }
        val copyRequest = BulkRequest().add(indexRequests)
        client.bulk(copyRequest, ActionListener.wrap(::onCopyResponse, ::onFailure))
    }

    private fun onCopyResponse(response: BulkResponse) {
        val deleteRequests = response.items.filterNot { it.isFailed }.map {
            DeleteRequest(AlertIndices.ALERT_INDEX, AlertIndices.MAPPING_TYPE, it.id)
                    .routing(monitorId)
                    .version(it.version)
        }
        if (response.hasFailures()) {
            hasFailures = true
            for (it in response.items) {
                logger.error("Failed to move deleted alert to alert history index: ${it.id}",
                        it.failure.cause)
            }
        }

        val bulkRequest = BulkRequest().add(deleteRequests)
        client.bulk(bulkRequest, ActionListener.wrap(::onDeleteResponse, ::onFailure))
    }

    private fun onDeleteResponse(response: BulkResponse) {
        if (response.hasFailures()) {
            hasFailures = true
            for (it in response.items) {
                logger.error("Failed to delete active alert from alert index: ${it.id}",
                        it.failure.cause)
            }
        }
        if (hasFailures) reschedule()
    }

    private fun onFailure(e: Exception) {
        logger.error("Failed to move alerts for ${monitorIdTriggerIdsTuple()}", e)
        reschedule()
    }

    private fun reschedule() {
        if (backoff.hasNext()) {
            logger.warn("Rescheduling AlertMover due to failure for ${monitorIdTriggerIdsTuple()}")
            val wait = backoff.next()
            val runnable = Runnable {
                monitorRunner.rescheduleAlertMover(monitorId, monitor, backoff)
            }
            threadPool.schedule(wait, ThreadPool.Names.SAME, runnable)
        } else {
            logger.warn("Retries exhausted for ${monitorIdTriggerIdsTuple()}")
        }
    }

    private fun alertContentParser(bytesReference: BytesReference): XContentParser {
        val xcp = XContentHelper.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE,
                bytesReference, XContentType.JSON)
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        return xcp
    }

    private fun monitorIdTriggerIdsTuple(): String {
        return "[$monitorId, ${monitor?.triggers?.map { it.id }}]"
    }
}
