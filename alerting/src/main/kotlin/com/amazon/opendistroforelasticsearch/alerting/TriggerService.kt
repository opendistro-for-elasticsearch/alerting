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

import com.amazon.opendistroforelasticsearch.alerting.model.AggregationResultBucket
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.script.AggregationTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.script.TraditionalTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.script.TriggerScript
import com.amazon.opendistroforelasticsearch.alerting.util.getBucketKeysHash
import org.apache.logging.log4j.LogManager
import org.elasticsearch.client.Client
import org.elasticsearch.script.ScriptService
import org.elasticsearch.search.aggregations.support.AggregationPath
import java.lang.IllegalArgumentException

/** Service that handles executing Triggers */
class TriggerService(val client: Client, val scriptService: ScriptService) {

    private val logger = LogManager.getLogger(TriggerService::class.java)

    fun isTraditionalTriggerActionable(ctx: TraditionalTriggerExecutionContext, result: TraditionalTriggerRunResult): Boolean {
        // Suppress actions if the current alert is acknowledged and there are no errors.
        val suppress = ctx.alert?.state == Alert.State.ACKNOWLEDGED && result.error == null && ctx.error == null
        return result.triggered && !suppress
    }

    fun runTraditionalTrigger(
        monitor: Monitor,
        trigger: TraditionalTrigger,
        ctx: TraditionalTriggerExecutionContext
    ): TraditionalTriggerRunResult {
        return try {
            val triggered = scriptService.compile(trigger.condition, TriggerScript.CONTEXT)
                .newInstance(trigger.condition.params)
                .execute(ctx)
            TraditionalTriggerRunResult(trigger.name, triggered, null)
        } catch (e: Exception) {
            logger.info("Error running script for monitor ${monitor.id}, trigger: ${trigger.id}", e)
            // if the script fails we need to send an alert so set triggered = true
            TraditionalTriggerRunResult(trigger.name, true, e)
        }
    }

    // TODO: This is a placeholder to write MonitorRunner logic, it can be replaced with the actual implementation when available
    @Suppress("UNCHECKED_CAST")
    fun runAggregationTrigger(
        monitor: Monitor,
        trigger: AggregationTrigger,
        ctx: AggregationTriggerExecutionContext
    ): AggregationTriggerRunResult {
        return try {
            val bucketIndices =
                ((ctx.results[0]["aggregations"] as HashMap<*, *>)[trigger.id] as HashMap<*, *>)["bucket_indices"] as List<*>
            val parentBucketPath =
                ((ctx.results[0]["aggregations"] as HashMap<*, *>)[trigger.id] as HashMap<*, *>)["parent_bucket_path"] as String
            val aggregationPath = AggregationPath.parse(parentBucketPath)
            // TODO test this part by passing sub-aggregation path
            var parentAgg = (ctx.results[0].get("aggregations") as HashMap<*, *>)
            aggregationPath.pathElementsAsStringList.forEach { sub_agg ->
                parentAgg = (parentAgg[sub_agg] as HashMap<*, *>)
            }
            val buckets = parentAgg["buckets"] as List<*>
            val selectedBuckets = mutableMapOf<String, AggregationResultBucket>()
            for (bucketIndex in bucketIndices) {
                val bucketDict = buckets[bucketIndex as Int] as Map<String, Any>
                val bucketKeyValuesList = getBucketKeyValuesList(bucketDict)
                val aggResultBucket = AggregationResultBucket(parentBucketPath, bucketKeyValuesList, bucketDict)
                selectedBuckets[aggResultBucket.getBucketKeysHash()] = aggResultBucket
            }
            AggregationTriggerRunResult(trigger.name, null, selectedBuckets)
        } catch (e: Exception) {
            logger.info("Error running script for monitor ${monitor.id}, trigger: ${trigger.id}", e)
            // TODO empty map here with error should be treated in the same way as TraditionTrigger with error running script
            AggregationTriggerRunResult(trigger.name, e, emptyMap())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getBucketKeyValuesList(bucket: Map<String, Any>): List<String> {
        val keyValuesList = mutableListOf<String>()
        when {
            bucket["key"] is String -> keyValuesList.add(bucket["key"] as String)
            bucket["key"] is Map<*, *> -> (bucket["key"] as Map<String, Any>).values.map { keyValuesList.add(it as String) }
            else -> throw IllegalArgumentException("Unexpected format for key in bucket [$bucket]")
        }

        return keyValuesList
    }
}
