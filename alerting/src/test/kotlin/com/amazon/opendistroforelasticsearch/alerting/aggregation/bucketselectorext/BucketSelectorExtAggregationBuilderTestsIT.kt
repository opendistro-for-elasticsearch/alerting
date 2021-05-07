package com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import org.elasticsearch.plugins.SearchPlugin
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptType
import org.elasticsearch.search.aggregations.BasePipelineAggregationTestCase
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude
import org.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy

class BucketSelectorExtAggregationBuilderTestsIT : BasePipelineAggregationTestCase<BucketSelectorExtAggregationBuilder>() {
    override fun plugins(): List<SearchPlugin?> {
        return listOf(AlertingPlugin())
    }

    override fun createTestAggregatorFactory(): BucketSelectorExtAggregationBuilder {
        val name = randomAlphaOfLengthBetween(3, 20)
        val bucketsPaths: MutableMap<String, String> = HashMap()
        val numBucketPaths = randomIntBetween(1, 10)
        for (i in 0 until numBucketPaths) {
            bucketsPaths[randomAlphaOfLengthBetween(1, 20)] = randomAlphaOfLengthBetween(1, 40)
        }
        val script: Script
        if (randomBoolean()) {
            script = mockScript("script")
        } else {
            val params: MutableMap<String, Any> = HashMap()
            if (randomBoolean()) {
                params["foo"] = "bar"
            }
            val type = randomFrom(*ScriptType.values())
            script =
                Script(
                    type, if (type == ScriptType.STORED) null else
                        randomFrom("my_lang", Script.DEFAULT_SCRIPT_LANG), "script", params
                )
        }
        val parentBucketPath = randomAlphaOfLengthBetween(3, 20)
        val filter = BucketSelectorExtFilter(IncludeExclude("foo.*", "bar.*"))
        val factory = BucketSelectorExtAggregationBuilder(
            name, bucketsPaths,
            script, parentBucketPath, filter
        )
        if (randomBoolean()) {
            factory.gapPolicy(randomFrom(*GapPolicy.values()))
        }
        return factory
    }
}
