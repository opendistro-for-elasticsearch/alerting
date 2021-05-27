package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.model.InputRunResults
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.alerting.randomAggregationTrigger
import com.amazon.opendistroforelasticsearch.alerting.randomTraditionalTrigger
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.cluster.ClusterModule
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.json.JsonXContent
import org.elasticsearch.search.aggregations.Aggregation
import org.elasticsearch.search.aggregations.AggregationBuilder
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.junit.Assert
import java.io.IOException


class AggregationQueryRewriterTests: ESTestCase() {

    fun `test RewriteQuery empty previous result`() {
        val triggers: MutableList<Trigger> = mutableListOf()
        for (i in 0 until 10) {
            triggers.add(randomAggregationTrigger())
        }
        val queryBuilder = SearchSourceBuilder()
        val termAgg: AggregationBuilder = TermsAggregationBuilder("testPath").field("sports")
        queryBuilder.aggregation(termAgg)
        val prevResult = null
        AggregationQueryRewriter.rewriteQuery(queryBuilder, prevResult, triggers)
        Assert.assertEquals(queryBuilder.aggregations().pipelineAggregatorFactories.size, 10)
    }

    fun `test RewriteQuery with non-empty previous result`() {
        val triggers: MutableList<Trigger> = mutableListOf()
        for (i in 0 until 10) {
            triggers.add(randomAggregationTrigger())
        }
        val queryBuilder = SearchSourceBuilder()
        val termAgg: AggregationBuilder = CompositeAggregationBuilder(
            "testPath",
            listOf(TermsValuesSourceBuilder("k1"), TermsValuesSourceBuilder("k2"))
        )
        queryBuilder.aggregation(termAgg)
        val aggTriggersAfterKey = mutableMapOf<String, Map<String, Any>?>()
        for (trigger in triggers) {
            aggTriggersAfterKey[trigger.id] = hashMapOf(Pair("k1", "v1"), Pair("k2", "v2"))
        }
        val prevResult = InputRunResults(emptyList(), null, aggTriggersAfterKey)
        AggregationQueryRewriter.rewriteQuery(queryBuilder, prevResult, triggers)
        Assert.assertEquals(queryBuilder.aggregations().pipelineAggregatorFactories.size, 10)
        queryBuilder.aggregations().aggregatorFactories.forEach {
            if (it.name.equals("testPath")) {
                val compAgg = it as CompositeAggregationBuilder
                val afterField = CompositeAggregationBuilder::class.java.getDeclaredField("after")
                afterField.isAccessible = true
                Assert.assertEquals(afterField.get(compAgg), hashMapOf(Pair("k1", "v1"), Pair("k2", "v2")))
            }
        }
    }

    fun `test RewriteQuery with non aggregation trigger`() {
        val triggers: MutableList<Trigger> = mutableListOf()
        for (i in 0 until 10) {
            triggers.add(randomTraditionalTrigger())
        }
        val queryBuilder = SearchSourceBuilder()
        val termAgg: AggregationBuilder = TermsAggregationBuilder("testPath").field("sports")
        queryBuilder.aggregation(termAgg)
        val prevResult = null
        AggregationQueryRewriter.rewriteQuery(queryBuilder, prevResult, triggers)
        Assert.assertEquals(queryBuilder.aggregations().pipelineAggregatorFactories.size, 0)
    }

    fun `test after keys from search response`() {
        val responseContent = """
        {
          "took" : 97,
          "timed_out" : false,
          "_shards" : {
            "total" : 3,
            "successful" : 3,
            "skipped" : 0,
            "failed" : 0
          },
          "hits" : {
            "total" : {
              "value" : 20,
              "relation" : "eq"
            },
            "max_score" : null,
            "hits" : [ ]
          },
          "aggregations" : {
            "composite#testPath" : {
              "after_key" : {
                "sport" : "Basketball"
              },
              "buckets" : [
                {
                  "key" : {
                    "sport" : "Basketball"
                  },
                  "doc_count" : 5
                }
              ]
            }
          }
        }
        """.trimIndent()

        val aggTriggers: MutableList<Trigger> = mutableListOf(randomAggregationTrigger())
        val tradTriggers: MutableList<Trigger> = mutableListOf(randomTraditionalTrigger())

        val searchResponse = SearchResponse.fromXContent(createParser(JsonXContent.jsonXContent, responseContent))
        val afterKeys = AggregationQueryRewriter.getAfterKeysFromSearchResponse(searchResponse, aggTriggers)
        Assert.assertEquals(afterKeys[aggTriggers[0].id], hashMapOf(Pair("sport", "Basketball")))

        val afterKeys2 = AggregationQueryRewriter.getAfterKeysFromSearchResponse(searchResponse, tradTriggers)
        Assert.assertEquals(afterKeys2.size, 0)
    }

    override fun xContentRegistry(): NamedXContentRegistry {
        val entries = ClusterModule.getNamedXWriteables()
        entries.add(
            NamedXContentRegistry.Entry(
                Aggregation::class.java, ParseField(CompositeAggregationBuilder.NAME),
                CheckedFunction<XContentParser, ParsedComposite, IOException> { parser: XContentParser? ->
                    ParsedComposite.fromXContent(
                        parser, "testPath"
                    )
                }
            )
        )
        return NamedXContentRegistry(entries)
    }
}
