/*
 *   Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.convertToMap
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.suspendUntil
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.InputRunResults
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.alerting.util.addUserBackendRolesFilter
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptService
import org.elasticsearch.script.ScriptType
import org.elasticsearch.script.TemplateScript
import org.elasticsearch.search.aggregations.AggregationBuilder
import org.elasticsearch.search.aggregations.AggregatorFactories
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder
import org.elasticsearch.search.aggregations.bucket.composite.InternalComposite
import org.elasticsearch.search.aggregations.support.AggregationPath
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.time.Instant

/** Service that handles the collection of input results for Monitor executions */
class InputService(
    val client: Client,
    val scriptService: ScriptService,
    val xContentRegistry: NamedXContentRegistry
) {

    private val logger = LogManager.getLogger(InputService::class.java)

    suspend fun collectInputResults(monitor: Monitor, periodStart: Instant, periodEnd: Instant, prevResult: InputRunResults? = null): InputRunResults {
        return try {
            val results = mutableListOf<Map<String, Any>>()
            val aggTriggerAfterKeys: MutableMap<String, Map<String, Any>?> = mutableMapOf()

            monitor.inputs.forEach { input ->
                when (input) {
                    is SearchInput -> {
                        // TODO: Figure out a way to use SearchTemplateRequest without bringing in the entire TransportClient
                        val searchParams = mapOf("period_start" to periodStart.toEpochMilli(),
                            "period_end" to periodEnd.toEpochMilli())
                        rewriteQuery(input.query, prevResult, monitor.triggers)

                        val searchSource = scriptService.compile(Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG,
                            input.query.toString(), searchParams), TemplateScript.CONTEXT)
                            .newInstance(searchParams)
                            .execute()

                        val searchRequest = SearchRequest().indices(*input.indices.toTypedArray())
                        XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, searchSource).use {
                            searchRequest.source(SearchSourceBuilder.fromXContent(it))
                        }
                        val searchResponse: SearchResponse = client.suspendUntil { client.search(searchRequest, it) }
                        aggTriggerAfterKeys += getAfterKeysFromSearchResponse(searchResponse, monitor.triggers)
                        results += searchResponse.convertToMap()
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported input type: ${input.name()}.")
                    }
                }
            }
            InputRunResults(results.toList(), aggTriggersAfterKey = aggTriggerAfterKeys)
        } catch (e: Exception) {
            logger.info("Error collecting inputs for monitor: ${monitor.id}", e)
            InputRunResults(emptyList(), e)
        }
    }

    /**
     * We moved anomaly result index to system index list. So common user could not directly query
     * this index any more. This method will stash current thread context to pass security check.
     * So monitor job can access anomaly result index. We will add monitor user roles filter in
     * search query to only return documents the monitor user can access.
     *
     * On alerting Kibana, monitor users can only see detectors that they have read access. So they
     * can't create monitor on other user's detector which they have no read access. Even they know
     * other user's detector id and use it to create monitor, this method will only return anomaly
     * results they can read.
     */
    suspend fun collectInputResultsForADMonitor(monitor: Monitor, periodStart: Instant, periodEnd: Instant): InputRunResults {
        return try {
            val results = mutableListOf<Map<String, Any>>()
            val input = monitor.inputs[0] as SearchInput

            val searchParams = mapOf("period_start" to periodStart.toEpochMilli(), "period_end" to periodEnd.toEpochMilli())
            val searchSource = scriptService.compile(Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG,
                input.query.toString(), searchParams), TemplateScript.CONTEXT)
                .newInstance(searchParams)
                .execute()

            val searchRequest = SearchRequest().indices(*input.indices.toTypedArray())
            XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, searchSource).use {
                searchRequest.source(SearchSourceBuilder.fromXContent(it))
            }

            // Add user role filter for AD result
            client.threadPool().threadContext.stashContext().use {
                // Currently we have no way to verify if user has AD read permission or not. So we always add user
                // role filter here no matter AD backend role filter enabled or not. If we don't add user role filter
                // when AD backend filter disabled, user can run monitor on any detector and get anomaly data even
                // they have no AD read permission. So if domain disabled AD backend role filter, monitor runner
                // still can't get AD result with different user backend role, even the monitor user has permission
                // to read AD result. This is a short term solution to trade off between user experience and security.
                //
                // Possible long term solution:
                // 1.Use secure rest client to send request to AD search result API. If no permission exception,
                // that mean user has read access on AD result. Then don't need to add user role filter when query
                // AD result if AD backend role filter is disabled.
                // 2.Security provide some transport action to verify if user has permission to search AD result.
                // Monitor runner will send transport request to check permission first. If security plugin response
                // is yes, user has permission to query AD result. If AD role filter enabled, we will add user role
                // filter to protect data at user role level; otherwise, user can query any AD result.
                addUserBackendRolesFilter(monitor.user, searchRequest.source())
                val searchResponse: SearchResponse = client.suspendUntil { client.search(searchRequest, it) }
                results += searchResponse.convertToMap()
            }
            InputRunResults(results.toList())
        } catch (e: Exception) {
            logger.info("Error collecting anomaly result inputs for monitor: ${monitor.id}", e)
            InputRunResults(emptyList(), e)
        }
    }
}

fun rewriteQuery(query: SearchSourceBuilder, prevResult: InputRunResults?, triggers: List<Trigger>) {
    triggers.forEach { trigger ->
        if (trigger is AggregationTrigger) {
            query.aggregation(trigger.bucketSelector)
            if (prevResult?.aggTriggersAfterKey?.keys?.contains(trigger.id) == true) {
                val parentBucketPath = AggregationPath.parse(trigger.bucketSelector.parentBucketPath)
                var aggBuilders = (query.aggregations() as AggregatorFactories.Builder).aggregatorFactories
                var factory: AggregationBuilder? = null
                for (i in 0 until parentBucketPath.pathElements.size) {
                    factory = null
                    for (aggFactory in aggBuilders) {
                        if (aggFactory.name.equals(parentBucketPath.pathElements[i].name)) {
                            aggBuilders = aggFactory.subAggregations
                            factory = aggFactory
                            break
                        }
                    }
                    if (factory == null) {
                        throw IllegalArgumentException("ParentBucketPath: $parentBucketPath not found in input query results")
                    }
                }
                if (factory is CompositeAggregationBuilder) {
                    // if the afterKey from previous result is null, what does it signify?
                    // A) result set exhausted OR  B) first page ?
                    val afterKey = prevResult.aggTriggersAfterKey[trigger.id]
                    factory.aggregateAfter(afterKey)
                }
            }
        }
    }
}

fun getAfterKeysFromSearchResponse(searchResponse: SearchResponse, triggers: List<Trigger>): Map<String, Map<String, Any>?> {
    val aggTriggerAfterKeys = mutableMapOf<String, Map<String, Any>?>()
    triggers.forEach { trigger ->
        if (trigger is AggregationTrigger) {
            val parentBucketPath = AggregationPath.parse(trigger.bucketSelector.parentBucketPath)
            var aggs = searchResponse.aggregations
            for (i in 0 until parentBucketPath.pathElements.size - 1) {
                aggs = (aggs.asMap()[parentBucketPath.pathElements[i].name] as SingleBucketAggregation).aggregations
            }
            val lastAgg = aggs.asMap[parentBucketPath.pathElements.last().name]
            if (lastAgg is InternalComposite) {
                aggTriggerAfterKeys[trigger.id] = lastAgg.afterKey()
            }
        }
    }
    return aggTriggerAfterKeys
}
