package com.amazon.elasticsearch.model

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.zone.ZoneRulesException

sealed class Schedule : ToXContentObject {
    enum class TYPE { CRON, INTERVAL }
    companion object {
        const val CRON_FIELD = "cron"
        const val EXPRESSION_FIELD = "expression"
        const val TIMEZONE_FIELD = "timezone"
        const val PERIOD_FIELD = "period"
        const val INTERVAL_FIELD = "interval"
        const val UNIT_FIELD = "unit"

        val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))

        @JvmStatic @Throws(IOException::class)
        fun parse(xcp: XContentParser): Schedule {
            var expression: String? = null
            var timezone: ZoneId? = null
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
                                TIMEZONE_FIELD -> timezone = getTimeZone(xcp.text())
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
                    }
                    else -> {
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

        @JvmStatic @Throws(IllegalArgumentException::class)
        private fun getTimeZone(timeZone: String): ZoneId {
            try {
                return ZoneId.of(timeZone)
            } catch (zre: ZoneRulesException) {
                throw IllegalArgumentException("Timezone $timeZone is not supported")
            } catch (dte: DateTimeException) {
                throw IllegalArgumentException("Timezone $timeZone is not supported")
            }
        }
    }

    abstract fun nextTimeToExecute() : Duration?

    /**
     * Returns the start and end time for the next run of the this schedule based on the last endTime of the cron.
     * If this is a schedule that runs only once this function will return [Instant.now] for both start and end time.
     */
    abstract fun getPeriodStartEnd(previousEndTime: Instant?) : Pair<Instant, Instant>

    abstract fun runningOnTime(lastExecutionTime: Instant?): Boolean
}

/**
 * @param testInstant Normally this not be set and it should only be used in unit test to control time.
 */
data class CronSchedule(val expression: String,
                        val timezone: ZoneId,
                        // visible for testing
                        @Transient val testInstant: Instant? = null) : Schedule() {
    @Transient
    val executionTime = ExecutionTime.forCron(cronParser.parse(expression))

    override fun nextTimeToExecute(): Duration? {
        val zonedDateTime = ZonedDateTime.ofInstant(testInstant ?: Instant.now(), timezone)
        val timeToNextExecution = executionTime.timeToNextExecution(zonedDateTime)
        return timeToNextExecution.orElse(null)
    }

    override fun getPeriodStartEnd(_previousEndTime: Instant?): Pair<Instant, Instant> {
        val previousEndTime = if (_previousEndTime != null) {
            _previousEndTime
        } else {
            // Probably the first time we're running. Try to figure out the last execution time
            val lastExecutionTime = executionTime.lastExecution(ZonedDateTime.now())
            // This shouldn't happen unless the cron is configured to run only once (which we currently don't support).
            if (!lastExecutionTime.isPresent) {
                val currentTime = Instant.now()
                return Pair(currentTime, currentTime)
            }
            lastExecutionTime.get().toInstant()
        }
        val zonedDateTime = ZonedDateTime.ofInstant(previousEndTime, timezone)
        val newEndTime = executionTime.nextExecution(zonedDateTime).orElse(null)
        return Pair(previousEndTime, newEndTime?.toInstant() ?: Instant.now())
    }

    override fun runningOnTime(lastExecutionTime: Instant?): Boolean {
        if (lastExecutionTime == null) {
            return true
        }

        val zonedDateTime = ZonedDateTime.ofInstant(testInstant ?: Instant.now(), timezone)
        val expectedExecutionTime = executionTime.lastExecution(zonedDateTime)

        if (!expectedExecutionTime.isPresent) {
            // At this point we know lastExecutionTime is not null, this should never happen.
            // If expected execution time is null, we shouldn't have executed the ScheduledJob.
            return false
        }
        val actualExecutionTime = ZonedDateTime.ofInstant(lastExecutionTime, timezone)

        return ChronoUnit.SECONDS.between(expectedExecutionTime.get(), actualExecutionTime) == 0L
    }

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

data class IntervalSchedule(val interval: Int,
                            val unit: ChronoUnit,
                            // visible for testing
                            @Transient val testInstant: Instant? = null) : Schedule() {

    @Transient
    private val intervalInMills = Duration.of(interval.toLong(), unit).toMillis()

    override fun nextTimeToExecute(): Duration? {
        // TODO this should really be nextExecutionTime = lastExecutionTime + interval
        // https://issues-pdx.amazon.com/issues/AESAlerting-93
        val intervalDuration = Duration.of(interval.toLong(), unit)
        return intervalDuration
    }

    override fun getPeriodStartEnd(_previousEndTime: Instant?): Pair<Instant, Instant> {
        val previousEndTime = _previousEndTime ?: Instant.now().minusMillis(intervalInMills)
        val newEndTime = previousEndTime.plusMillis(intervalInMills)
        return Pair(previousEndTime, newEndTime)
    }

    override fun runningOnTime(lastExecutionTime: Instant?): Boolean {
        if (lastExecutionTime == null) {
            return true
        }

        // Make sure the lastExecutionTime is less than interval time.
        val delta = ChronoUnit.MILLIS.between(lastExecutionTime, testInstant ?: Instant.now())
        return 0 < delta && delta < intervalInMills
    }

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