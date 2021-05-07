package com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext

import org.elasticsearch.common.ParseField
import org.elasticsearch.common.ParsingException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent.Params
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude
import java.io.IOException

class BucketSelectorExtFilter : ToXContentObject, Writeable {
    val filtersMap // used for composite aggregations
            : HashMap<String, IncludeExclude>?
    val filters // used for filtering string term aggregation
            : IncludeExclude?

    constructor(filters: IncludeExclude?) {
        filtersMap = null
        this.filters = filters
    }

    constructor(filtersMap: HashMap<String, IncludeExclude>?) {
        this.filtersMap = filtersMap
        filters = null
    }

    constructor(sin: StreamInput) {
        if (sin.readBoolean()) {
            val size: Int = sin.readVInt()
            filtersMap = java.util.HashMap()
            for (i in 0 until size) {
                filtersMap[sin.readString()] = IncludeExclude(sin)
            }
            filters = null
        } else {
            filters = IncludeExclude(sin)
            filtersMap = null
        }
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        val isCompAgg = isCompositeAggregation
        out.writeBoolean(isCompAgg)
        if (isCompAgg) {
            out.writeVInt(filtersMap!!.size)
            for ((key, value) in filtersMap) {
                out.writeString(key)
                value.writeTo(out)
            }
        } else {
            filters!!.writeTo(out)
        }
    }

    @Throws(IOException::class)
    override fun toXContent(builder: XContentBuilder, params: Params): XContentBuilder {
        if (isCompositeAggregation) {
            for ((key, filter) in filtersMap!!) {
                builder.startObject(key)
                filter.toXContent(builder, params)
                builder.endObject()
            }
        } else {
            filters!!.toXContent(builder, params)
        }
        return builder
    }

    val isCompositeAggregation: Boolean
        get() = if (filtersMap != null && filters == null) {
            true
        } else if (filtersMap == null && filters != null) {
            false
        } else {
            throw IllegalStateException("Type of selector cannot be determined")
        }

    companion object {
        const val NAME = "filter"
        var BUCKET_SELECTOR_FILTER = ParseField("filter")
        var BUCKET_SELECTOR_COMPOSITE_AGG_FILTER = ParseField("composite_agg_filter")

        @Throws(IOException::class)
        fun parse(
            reducerName: String, isCompositeAggregation: Boolean,
            parser: XContentParser
        ): BucketSelectorExtFilter {
            var token: XContentParser.Token
            return if (isCompositeAggregation) {
                val filtersMap = HashMap<String, IncludeExclude>()
                while (parser.nextToken().also { token = it } !== XContentParser.Token.END_OBJECT) {
                    if (token === XContentParser.Token.FIELD_NAME) {
                        val sourceKey = parser.currentName()
                        token = parser.nextToken()
                        filtersMap[sourceKey] = parseIncludeExclude(reducerName, parser)
                    } else {
                        throw ParsingException(
                            parser.tokenLocation,
                            "Unknown key for a " + token + " in [" + reducerName + "]: [" + parser.currentName() + "]."
                        )
                    }
                }
                BucketSelectorExtFilter(filtersMap)
            } else {
                BucketSelectorExtFilter(parseIncludeExclude(reducerName, parser))
            }
        }

        @Throws(IOException::class)
        private fun parseIncludeExclude(reducerName: String, parser: XContentParser): IncludeExclude {
            var token: XContentParser.Token
            var include: IncludeExclude? = null
            var exclude: IncludeExclude? = null
            while (parser.nextToken().also { token = it } !== XContentParser.Token.END_OBJECT) {
                val fieldName = parser.currentName()
                when {
                    IncludeExclude.INCLUDE_FIELD.match(fieldName, parser.deprecationHandler) -> {
                        parser.nextToken()
                        include = IncludeExclude.parseInclude(parser)
                    }
                    IncludeExclude.EXCLUDE_FIELD.match(fieldName, parser.deprecationHandler) -> {
                        parser.nextToken()
                        exclude = IncludeExclude.parseExclude(parser)
                    }
                    else -> {
                        throw ParsingException(
                            parser.tokenLocation,
                            "Unknown key for a $token in [$reducerName]: [$fieldName]."
                        )
                    }
                }
            }
            return IncludeExclude.merge(include, exclude)
        }
    }
}
