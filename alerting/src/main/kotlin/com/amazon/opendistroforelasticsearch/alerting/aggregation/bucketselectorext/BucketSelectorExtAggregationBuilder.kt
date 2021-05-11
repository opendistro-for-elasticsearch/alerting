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
package com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext

import com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext.BucketSelectorExtFilter.Companion.BUCKET_SELECTOR_COMPOSITE_AGG_FILTER
import com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext.BucketSelectorExtFilter.Companion.BUCKET_SELECTOR_FILTER
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.ParsingException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent.Params
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.script.Script
import org.elasticsearch.search.aggregations.pipeline.AbstractPipelineAggregationBuilder
import org.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator
import java.io.IOException
import java.util.Objects

class BucketSelectorExtAggregationBuilder :
    AbstractPipelineAggregationBuilder<BucketSelectorExtAggregationBuilder> {
    private val bucketsPathsMap: MutableMap<String, String>
    val parentBucketPath: String
    val script: Script
    val filter: BucketSelectorExtFilter?
    private var gapPolicy = GapPolicy.SKIP

    constructor(
        name: String?,
        bucketsPathsMap: MutableMap<String, String>,
        script: Script,
        parentBucketPath: String,
        filter: BucketSelectorExtFilter?
    ) : super(name, NAME.preferredName, listOf<String>(parentBucketPath).toTypedArray<String>()) {
        this.bucketsPathsMap = bucketsPathsMap
        this.script = script
        this.parentBucketPath = parentBucketPath
        this.filter = filter
    }

    @Throws(IOException::class)
    @Suppress("UNCHECKED_CAST")
    constructor(sin: StreamInput) : super(sin, NAME.preferredName) {
        bucketsPathsMap = sin.readMap() as MutableMap<String, String>
        script = Script(sin)
        gapPolicy = GapPolicy.readFrom(sin)
        parentBucketPath = sin.readString()
        filter = if (sin.readBoolean()) {
            BucketSelectorExtFilter(sin)
        } else {
            null
        }
    }

    @Throws(IOException::class)
    override fun doWriteTo(out: StreamOutput) {
        out.writeMap(bucketsPathsMap as Map<String, String>)
        script.writeTo(out)
        gapPolicy.writeTo(out)
        out.writeString(parentBucketPath)
        if (filter != null) {
            out.writeBoolean(true)
            filter.writeTo(out)
        } else {
            out.writeBoolean(false)
        }
    }

    /**
     * Sets the gap policy to use for this aggregation.
     */
    fun gapPolicy(gapPolicy: GapPolicy?): BucketSelectorExtAggregationBuilder {
        requireNotNull(gapPolicy) { "[gapPolicy] must not be null: [$name]" }
        this.gapPolicy = gapPolicy
        return this
    }

    override fun createInternal(metaData: Map<String, Any>?): PipelineAggregator {
        return BucketSelectorExtAggregator(name, bucketsPathsMap, parentBucketPath, script, gapPolicy, filter, metaData)
    }

    @Throws(IOException::class)
    public override fun internalXContent(builder: XContentBuilder, params: Params): XContentBuilder {
        builder.field(PipelineAggregator.Parser.BUCKETS_PATH.preferredName, bucketsPathsMap as Map<String, Any>?)
            .field(PARENT_BUCKET_PATH.preferredName, parentBucketPath)
            .field(Script.SCRIPT_PARSE_FIELD.preferredName, script)
            .field(PipelineAggregator.Parser.GAP_POLICY.preferredName, gapPolicy.getName())
        if (filter != null) {
            if (filter.isCompositeAggregation) {
                builder.startObject(BUCKET_SELECTOR_COMPOSITE_AGG_FILTER.preferredName)
                    .value(filter)
                    .endObject()
            } else {
                builder.startObject(BUCKET_SELECTOR_FILTER.preferredName)
                    .value(filter)
                    .endObject()
            }
        }
        return builder
    }

    override fun overrideBucketsPath(): Boolean {
        return true
    }

    override fun validate(context: ValidationContext) {
        // Nothing to check
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), bucketsPathsMap, script, gapPolicy)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        if (!super.equals(other)) return false
        val otherCast = other as BucketSelectorExtAggregationBuilder
        return (bucketsPathsMap == otherCast.bucketsPathsMap
                && script == otherCast.script
                && gapPolicy == otherCast.gapPolicy)
    }

    override fun getWriteableName(): String {
        return NAME.preferredName
    }

    companion object {
        val NAME = ParseField("bucket_selector_ext")
        val PARENT_BUCKET_PATH = ParseField("parent_bucket_path")

        @Throws(IOException::class)
        fun parse(reducerName: String, parser: XContentParser): BucketSelectorExtAggregationBuilder {
            var token: XContentParser.Token
            var script: Script? = null
            var currentFieldName: String? = null
            var bucketsPathsMap: MutableMap<String, String>? = null
            var gapPolicy: GapPolicy? = null
            var parentBucketPath: String? = null
            var filter: BucketSelectorExtFilter? = null
            while (parser.nextToken().also { token = it } !== XContentParser.Token.END_OBJECT) {
                if (token === XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName()
                } else if (token === XContentParser.Token.VALUE_STRING) {
                    when {
                        PipelineAggregator.Parser.BUCKETS_PATH.match(currentFieldName, parser.deprecationHandler) -> {
                            bucketsPathsMap = HashMap()
                            bucketsPathsMap["_value"] = parser.text()
                        }
                        PipelineAggregator.Parser.GAP_POLICY.match(currentFieldName, parser.deprecationHandler) -> {
                            gapPolicy = GapPolicy.parse(parser.text(), parser.tokenLocation)
                        }
                        Script.SCRIPT_PARSE_FIELD.match(currentFieldName, parser.deprecationHandler) -> {
                            script = Script.parse(parser)
                        }
                        PARENT_BUCKET_PATH.match(currentFieldName, parser.deprecationHandler) -> {
                            parentBucketPath = parser.text()
                        }
                        else -> {
                            throw ParsingException(
                                parser.tokenLocation,
                                "Unknown key for a $token in [$reducerName]: [$currentFieldName]."
                            )
                        }
                    }
                } else if (token === XContentParser.Token.START_ARRAY) {
                    if (PipelineAggregator.Parser.BUCKETS_PATH.match(currentFieldName, parser.deprecationHandler)) {
                        val paths: MutableList<String> = ArrayList()
                        while (parser.nextToken().also { token = it } !== XContentParser.Token.END_ARRAY) {
                            val path = parser.text()
                            paths.add(path)
                        }
                        bucketsPathsMap = HashMap()
                        for (i in paths.indices) {
                            bucketsPathsMap["_value$i"] = paths[i]
                        }
                    } else {
                        throw ParsingException(
                            parser.tokenLocation,
                            "Unknown key for a $token in [$reducerName]: [$currentFieldName]."
                        )
                    }
                } else if (token === XContentParser.Token.START_OBJECT) {
                    when {
                        Script.SCRIPT_PARSE_FIELD.match(currentFieldName, parser.deprecationHandler) -> {
                            script = Script.parse(parser)
                        }
                        PipelineAggregator.Parser.BUCKETS_PATH.match(currentFieldName, parser.deprecationHandler) -> {
                            val map = parser.map()
                            bucketsPathsMap = HashMap()
                            for ((key, value) in map) {
                                bucketsPathsMap[key] = value.toString()
                            }
                        }
                        BUCKET_SELECTOR_FILTER.match(currentFieldName, parser.deprecationHandler) -> {
                            filter = BucketSelectorExtFilter.parse(reducerName, false, parser)
                        }
                        BUCKET_SELECTOR_COMPOSITE_AGG_FILTER.match(
                            currentFieldName,
                            parser.deprecationHandler
                        ) -> {
                            filter = BucketSelectorExtFilter.parse(reducerName, true, parser)
                        }
                        else -> {
                            throw ParsingException(
                                parser.tokenLocation,
                                "Unknown key for a $token in [$reducerName]: [$currentFieldName]."
                            )
                        }
                    }
                } else {
                    throw ParsingException(parser.tokenLocation, "Unexpected token $token in [$reducerName].")
                }
            }
            if (bucketsPathsMap == null) {
                throw ParsingException(
                    parser.tokenLocation, "Missing required field [" + PipelineAggregator.Parser.BUCKETS_PATH.preferredName
                            + "] for bucket_selector aggregation [" + reducerName + "]"
                )
            }
            if (script == null) {
                throw ParsingException(
                    parser.tokenLocation, "Missing required field [" + Script.SCRIPT_PARSE_FIELD.preferredName
                            + "] for bucket_selector aggregation [" + reducerName + "]"
                )
            }

            if (parentBucketPath == null) {
                throw ParsingException(
                    parser.tokenLocation, "Missing required field [" + PARENT_BUCKET_PATH
                            + "] for bucket_selector aggregation [" + reducerName + "]"
                )
            }
            val factory = BucketSelectorExtAggregationBuilder(reducerName, bucketsPathsMap, script, parentBucketPath, filter)
            if (gapPolicy != null) {
                factory.gapPolicy(gapPolicy)
            }
            return factory
        }
    }

}
