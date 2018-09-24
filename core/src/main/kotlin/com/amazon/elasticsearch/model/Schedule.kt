package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class Schedule: ToXContentObject {

    companion object {
        const val CRON_EXPRESSION = "cron_expression"

        @JvmStatic @Throws(IOException::class)
        fun parse(xcp: XContentParser) : Schedule {
            var expression: String? = null

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
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
            return CronSchedule(requireNotNull(expression) { "cron_expression is null" })
            // TODO: Implement interval handling
        }
    }
}

data class CronSchedule(val expression : String) : Schedule() {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        builder.field(CRON_EXPRESSION, expression)
        return builder.endObject()
    }

}

data class IntervalSchedule(val period: Int, val unit: TimeUnit) : Schedule() {
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}