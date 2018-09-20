package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import java.io.IOException

data class Schedule(val expression: String): ToXContentObject {

    fun toXContent(builder: XContentBuilder) : XContentBuilder {
        return toXContent(builder, ToXContent.EMPTY_PARAMS)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        builder.field(CRON_EXPRESSION, expression)
        return builder.endObject()
    }

    companion object {
        const val CRON_EXPRESSION = "cron_expression"

        @JvmStatic @Throws(IOException::class)
        fun parse(xcp: XContentParser) : Schedule {
            var expression: String? = null
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldname = xcp.currentName()
                xcp.nextToken()
                when (fieldname) {
                    CRON_EXPRESSION -> {
                        expression = xcp.text()
                    } else -> {
                        throw IllegalArgumentException("Invalid field: [$fieldname] found in schedule.")
                    }
                }
            }
            return Schedule(requireNotNull(expression) { "cron_expression is null" })
        }
    }
}