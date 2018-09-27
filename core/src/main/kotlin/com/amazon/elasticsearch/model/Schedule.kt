package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.time.temporal.ChronoUnit

sealed class Schedule: ToXContentObject {
    enum class TYPE  { CRON, INTERVAL }
    companion object {
        const val CRON_FIELD = "cron"
        const val EXPRESSION_FIELD = "expression"
        const val TIMEZONE_FIELD = "timezone"
        const val PERIOD_FIELD = "period"
        const val INTERVAL_FIELD = "interval"
        const val UNIT_FIELD = "unit"

        @JvmStatic @Throws(IOException::class)
        fun parse(xcp: XContentParser) : Schedule {
            var expression: String? = null
            var timezone = "UTC"
            var interval: Int? = null
            var unit: ChronoUnit? = null
            var schedule: Schedule? = null
            var type: TYPE? = null
            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldname = xcp.currentName()
                xcp.nextToken()
                // If the type field has already been set the customer has provide more than one type of schedule.
                if (type != null) {
                    throw IllegalArgumentException("You can only specify one type of schedule.")
                }
                when (fieldname) {
                    CRON_FIELD -> {
                        type = TYPE.CRON
                        while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                            val cronFieldName = xcp.currentName()
                            xcp.nextToken()
                            when (cronFieldName) {
                                EXPRESSION_FIELD -> expression = xcp.textOrNull()
                                TIMEZONE_FIELD -> timezone = xcp.text()
                            }
                        }
                    }
                    PERIOD_FIELD -> {
                        type = TYPE.INTERVAL
                        while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                            val cronFieldName = xcp.currentName()
                            xcp.nextToken()
                            when (cronFieldName) {
                                INTERVAL_FIELD -> interval = xcp.intValue()
                                UNIT_FIELD -> unit = ChronoUnit.valueOf(xcp.text())
                            }
                        }
                    } else -> {
                        throw IllegalArgumentException("Invalid field: [$fieldname] found in schedule.")
                    }
                }
            }
            if (type == TYPE.CRON) {
                schedule = CronSchedule(requireNotNull(expression) { "Expression in cron schedule is null." },
                        requireNotNull(timezone) { "Timezone in cron schedule is null." })
            } else if (type == TYPE.INTERVAL) {
                schedule = IntervalSchedule(requireNotNull(interval) { "Interval in period schedule is null." },
                        requireNotNull(unit) { "Unit in period schedule is null." })
            }
            return requireNotNull(schedule) { "Schedule is null." }
        }
    }
}

data class CronSchedule(val expression: String, val timezone: String) : Schedule() {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .startObject(CRON_FIELD)
                .field(EXPRESSION_FIELD, expression)
                .field(TIMEZONE_FIELD, timezone)
                .endObject()
                .endObject()
        return builder
    }
}

data class IntervalSchedule(val interval: Int, val unit: ChronoUnit) : Schedule() {
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .startObject(PERIOD_FIELD)
                .field(INTERVAL_FIELD, interval)
                .field(UNIT_FIELD, unit.name)
                .endObject()
                .endObject()
        return builder
    }
}