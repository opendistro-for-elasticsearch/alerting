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

import com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext.BucketSelectorIndices.Fields.BUCKET_INDICES
import com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext.BucketSelectorIndices.Fields.PARENT_BUCKET_PATH
import com.amazon.opendistroforelasticsearch.alerting.model.AggregationResultBucket
import com.amazon.opendistroforelasticsearch.alerting.model.BucketLevelTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.BucketLevelTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.QueryLevelTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.QueryLevelTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.script.BucketLevelTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.script.QueryLevelTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.script.TriggerScript
import com.amazon.opendistroforelasticsearch.alerting.util.getBucketKeysHash
import org.apache.logging.log4j.LogManager
import org.elasticsearch.client.Client
import org.elasticsearch.script.ScriptService
import org.elasticsearch.search.aggregations.Aggregation
import org.elasticsearch.search.aggregations.Aggregations
import org.elasticsearch.search.aggregations.support.AggregationPath
import java.lang.IllegalArgumentException

/** Service that handles executing Triggers */
class TriggerService(val client: Client, val scriptService: ScriptService) {

    private val logger = LogManager.getLogger(TriggerService::class.java)

    fun isQueryLevelTriggerActionable(ctx: QueryLevelTriggerExecutionContext, result: QueryLevelTriggerRunResult): Boolean {
        // Suppress actions if the current alert is acknowledged and there are no errors.
        val suppress = ctx.alert?.state == Alert.State.ACKNOWLEDGED && result.error == null && ctx.error == null
        return result.triggered && !suppress
    }

    fun runQueryLevelTrigger(
        monitor: Monitor,
        trigger: QueryLevelTrigger,
        ctx: QueryLevelTriggerExecutionContext
    ): QueryLevelTriggerRunResult {
        return try {
            val triggered = scriptService.compile(trigger.condition, TriggerScript.CONTEXT)
                .newInstance(trigger.condition.params)
                .execute(ctx)
            QueryLevelTriggerRunResult(trigger.name, triggered, null)
        } catch (e: Exception) {
            logger.info("Error running script for monitor ${monitor.id}, trigger: ${trigger.id}", e)
            // if the script fails we need to send an alert so set triggered = true
            QueryLevelTriggerRunResult(trigger.name, true, e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun runBucketLevelTrigger(
        monitor: Monitor,
        trigger: BucketLevelTrigger,
        ctx: BucketLevelTriggerExecutionContext
    ): BucketLevelTriggerRunResult {
        return try {
            val bucketIndices =
                ((ctx.results[0][Aggregations.AGGREGATIONS_FIELD] as HashMap<*, *>)[trigger.id] as HashMap<*, *>)[BUCKET_INDICES] as List<*>
            val parentBucketPath = ((ctx.results[0][Aggregations.AGGREGATIONS_FIELD] as HashMap<*, *>)
                .get(trigger.id) as HashMap<*, *>)[PARENT_BUCKET_PATH] as String
            val aggregationPath = AggregationPath.parse(parentBucketPath)
            // TODO test this part by passing sub-aggregation path
            var parentAgg = (ctx.results[0][Aggregations.AGGREGATIONS_FIELD] as HashMap<*, *>)
            aggregationPath.pathElementsAsStringList.forEach { sub_agg ->
                parentAgg = (parentAgg[sub_agg] as HashMap<*, *>)
            }
            val buckets = parentAgg[Aggregation.CommonFields.BUCKETS.preferredName] as List<*>
            val selectedBuckets = mutableMapOf<String, AggregationResultBucket>()
            for (bucketIndex in bucketIndices) {
                val bucketDict = buckets[bucketIndex as Int] as Map<String, Any>
                val bucketKeyValuesList = getBucketKeyValuesList(bucketDict)
                val aggResultBucket = AggregationResultBucket(parentBucketPath, bucketKeyValuesList, bucketDict)
                selectedBuckets[aggResultBucket.getBucketKeysHash()] = aggResultBucket
            }
            BucketLevelTriggerRunResult(trigger.name, null, selectedBuckets)
        } catch (e: Exception) {
            logger.info("Error running script for monitor ${monitor.id}, trigger: ${trigger.id}", e)
            // TODO empty map here with error should be treated in the same way as QueryLevelTrigger with error running script
            BucketLevelTriggerRunResult(trigger.name, e, emptyMap())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getBucketKeyValuesList(bucket: Map<String, Any>): List<String> {
        val keyField = Aggregation.CommonFields.KEY.preferredName
        val keyValuesList = mutableListOf<String>()
        when {
            bucket[keyField] is String -> keyValuesList.add(bucket[keyField] as String)
            bucket[keyField] is Map<*, *> -> (bucket[keyField] as Map<String, Any>).values.map { keyValuesList.add(it as String) }
            else -> throw IllegalArgumentException("Unexpected format for key in bucket [$bucket]")
        }

        return keyValuesList
    }
}
