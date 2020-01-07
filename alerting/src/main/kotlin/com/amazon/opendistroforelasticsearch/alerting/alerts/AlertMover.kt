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
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.suspendUntil
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.bytes.BytesReference
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
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder

/**
 * Moves defunct active alerts to the alert history index when the corresponding monitor or trigger is deleted.
 *
 * The logic for moving alerts consists of:
 * 1. Find active alerts:
 *      a. matching monitorId if no monitor is provided (postDelete)
 *      b. matching monitorId and no triggerIds if monitor is provided (postIndex)
 * 2. Move alerts over to [HISTORY_WRITE_INDEX] as DELETED
 * 3. Delete alerts from [ALERT_INDEX]
 * 4. Schedule a retry if there were any failures
 */
suspend fun moveAlerts(client: Client, monitorId: String, monitor: Monitor? = null) {
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
    val response: SearchResponse = client.suspendUntil { search(activeAlertsRequest, it) }

    // If no alerts are found, simply return
    if (response.hits.totalHits?.value == 0L) return
    val indexRequests = response.hits.map { hit ->
        IndexRequest(AlertIndices.HISTORY_WRITE_INDEX)
            .routing(monitorId)
            .source(Alert.parse(alertContentParser(hit.sourceRef), hit.id, hit.version)
                .copy(state = Alert.State.DELETED)
                .toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
            .version(hit.version)
            .versionType(VersionType.EXTERNAL_GTE)
            .id(hit.id)
    }
    val copyRequest = BulkRequest().add(indexRequests)
    val copyResponse: BulkResponse = client.suspendUntil { bulk(copyRequest, it) }

    val deleteRequests = copyResponse.items.filterNot { it.isFailed }.map {
        DeleteRequest(AlertIndices.ALERT_INDEX, it.id)
            .routing(monitorId)
            .version(it.version)
            .versionType(VersionType.EXTERNAL_GTE)
    }
    val deleteResponse: BulkResponse = client.suspendUntil { bulk(BulkRequest().add(deleteRequests), it) }

    if (copyResponse.hasFailures()) {
        val retryCause = copyResponse.items.filter { it.isFailed }
            .firstOrNull { it.status() == RestStatus.TOO_MANY_REQUESTS }
            ?.failure?.cause
        throw RuntimeException("Failed to copy alerts for [$monitorId, ${monitor?.triggers?.map { it.id }}]: " +
            copyResponse.buildFailureMessage(), retryCause)
    }
    if (deleteResponse.hasFailures()) {
        val retryCause = deleteResponse.items.filter { it.isFailed }
            .firstOrNull { it.status() == RestStatus.TOO_MANY_REQUESTS }
            ?.failure?.cause
        throw RuntimeException("Failed to delete alerts for [$monitorId, ${monitor?.triggers?.map { it.id }}]: " +
            deleteResponse.buildFailureMessage(), retryCause)
    }
}

private fun alertContentParser(bytesReference: BytesReference): XContentParser {
    val xcp = XContentHelper.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE,
                bytesReference, XContentType.JSON)
    XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
    return xcp
}
