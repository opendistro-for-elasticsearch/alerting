package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.model.AggregationTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.InputRunResults
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.aggregations.AggregationBuilder
import org.elasticsearch.search.aggregations.AggregatorFactories
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder
import org.elasticsearch.search.aggregations.support.AggregationPath
import org.elasticsearch.search.builder.SearchSourceBuilder

class AggregationQueryRewriter {

    companion object {
        /**
         * Add the bucket selector conditions for each trigger in input query. It also adds afterKeys from previous result
         * for each trigger.
         */
        fun rewriteQuery(query: SearchSourceBuilder, prevResult: InputRunResults?, triggers: List<Trigger>) {
            triggers.forEach { trigger ->
                if (trigger is AggregationTrigger) {
                    // add bucket selector pipeline aggregation for each trigger in query
                    query.aggregation(trigger.bucketSelector)
                    // if this request is processing the subsequent pages of input query result, then add after key
                    if (prevResult?.aggTriggersAfterKey?.get(trigger.id) != null) {
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
                        } else {
                            throw IllegalStateException("AfterKeys are not expected to be present in non CompositeAggregationBuilder")

                        }
                    }
                }
            }
        }

        /**
         * For each trigger, returns the after keys if present in query result.
         */
        fun getAfterKeysFromSearchResponse(searchResponse: SearchResponse, triggers: List<Trigger>): Map<String, Map<String, Any>?> {
            val aggTriggerAfterKeys = mutableMapOf<String, Map<String, Any>?>()
            triggers.forEach { trigger ->
                if (trigger is AggregationTrigger) {
                    val parentBucketPath = AggregationPath.parse(trigger.bucketSelector.parentBucketPath)
                    var aggs = searchResponse.aggregations
                    // assuming all intermediate aggregations as SingleBucketAggregation
                    for (i in 0 until parentBucketPath.pathElements.size - 1) {
                        aggs = (aggs.asMap()[parentBucketPath.pathElements[i].name] as SingleBucketAggregation).aggregations
                    }
                    val lastAgg = aggs.asMap[parentBucketPath.pathElements.last().name]
                    // if leaf is CompositeAggregation, then fetch afterKey if present
                    if (lastAgg is CompositeAggregation) {
                        aggTriggerAfterKeys[trigger.id] = lastAgg.afterKey()
                    }
                }
            }
            return aggTriggerAfterKeys
        }
    }
}
