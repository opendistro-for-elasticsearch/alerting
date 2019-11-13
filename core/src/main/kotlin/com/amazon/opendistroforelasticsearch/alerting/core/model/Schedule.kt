/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.core.model

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
                                UNIT_FIELD -> unit = ChronoUnit.valueOf(xcp.text().toUpperCase())
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

    /**
     * @param enabledTime is used in IntervalSchedule to calculate next time to execute the schedule.
     */
    abstract fun nextTimeToExecute(enabledTime: Instant): Duration?

    /**
     * @param expectedPreviousExecutionTime is the calculated previous execution time that should always be correct,
     * the first time this is called the value passed in is the enabledTime which acts as the expectedPreviousExecutionTime
     */
    abstract fun getExpectedNextExecutionTime(enabledTime: Instant, expectedPreviousExecutionTime: Instant?): Instant?

    /**
     * Returns the start and end time for this schedule starting at the given start time (if provided).
     * If not, the start time is assumed to be the last time the Schedule would have executed (if it's a Cron schedule)
     * or [Instant.now] if it's an interval schedule.
     *
     * If this is a schedule that runs only once this function will return [Instant.now] for both start and end time.
     */
    abstract fun getPeriodStartingAt(startTime: Instant?): Pair<Instant, Instant>

    /**
     * Returns the start and end time for this schedule ending at the given end time (if provided).
     * If not, the end time is assumed to be the next time the Schedule would have executed (if it's a Cron schedule)
     * or [Instant.now] if it's an interval schedule.
     *
     * If this is a schedule that runs only once this function will return [Instant.now] for both start and end time.
     */
    abstract fun getPeriodEndingAt(endTime: Instant?): Pair<Instant, Instant>

    abstract fun runningOnTime(lastExecutionTime: Instant?): Boolean
}

/**
 * @param testInstant Normally this not be set and it should only be used in unit test to control time.
 */
data class CronSchedule(
    val expression: String,
    val timezone: ZoneId,
    // visible for testing
    @Transient val testInstant: Instant? = null
) : Schedule() {
    @Transient
    val executionTime: ExecutionTime = ExecutionTime.forCron(cronParser.parse(expression))

    /*
     * @param enabledTime is not used in CronSchedule.
     */
    override fun nextTimeToExecute(enabledTime: Instant): Duration? {
        val zonedDateTime = ZonedDateTime.ofInstant(testInstant ?: Instant.now(), timezone)
        val timeToNextExecution = executionTime.timeToNextExecution(zonedDateTime)
        return timeToNextExecution.orElse(null)
    }

    override fun getExpectedNextExecutionTime(enabledTime: Instant, expectedPreviousExecutionTime: Instant?): Instant? {
        val zonedDateTime = ZonedDateTime.ofInstant(expectedPreviousExecutionTime ?: testInstant ?: Instant.now(), timezone)
        val nextExecution = executionTime.nextExecution(zonedDateTime)
        return nextExecution.orElse(null)?.toInstant()
    }

    override fun getPeriodStartingAt(startTime: Instant?): Pair<Instant, Instant> {
        val realStartTime = if (startTime != null) {
            startTime
        } else {
            // Probably the first time we're running. Try to figure out the last execution time
            val lastExecutionTime = executionTime.lastExecution(ZonedDateTime.now(timezone))
            // This shouldn't happen unless the cron is configured to run only once, which our current cron syntax doesn't support
            if (!lastExecutionTime.isPresent) {
                val currentTime = Instant.now()
                return Pair(currentTime, currentTime)
            }
            lastExecutionTime.get().toInstant()
        }
        val zonedDateTime = ZonedDateTime.ofInstant(realStartTime, timezone)
        val newEndTime = executionTime.nextExecution(zonedDateTime).orElse(null)
        return Pair(realStartTime, newEndTime?.toInstant() ?: realStartTime)
    }

    override fun getPeriodEndingAt(endTime: Instant?): Pair<Instant, Instant> {
        val realEndTime = if (endTime != null) {
            endTime
        } else {
            val nextExecutionTime = executionTime.nextExecution(ZonedDateTime.now(timezone))
            // This shouldn't happen unless the cron is configured to run only once which our current cron syntax doesn't support
            if (!nextExecutionTime.isPresent) {
                val currentTime = Instant.now()
                return Pair(currentTime, currentTime)
            }
            nextExecutionTime.get().toInstant()
        }
        val zonedDateTime = ZonedDateTime.ofInstant(realEndTime, timezone)
        val newStartTime = executionTime.lastExecution(zonedDateTime).orElse(null)
        return Pair(newStartTime?.toInstant() ?: realEndTime, realEndTime)
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
                .field(TIMEZONE_FIELD, timezone.id)
                .endObject()
                .endObject()
        return builder
    }
}

data class IntervalSchedule(
    val interval: Int,
    val unit: ChronoUnit,
    // visible for testing
    @Transient val testInstant: Instant? = null
) : Schedule() {
    companion object {
        @Transient
        private val SUPPORTED_UNIT = listOf(ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS)
    }

    init {
        if (!SUPPORTED_UNIT.contains(unit)) {
            throw IllegalArgumentException("Timezone $unit is not supported expected $SUPPORTED_UNIT")
        }

        if (interval <= 0) {
            throw IllegalArgumentException("Interval is not allowed to be 0 or negative")
        }
    }

    @Transient
    private val intervalInMills = Duration.of(interval.toLong(), unit).toMillis()

    override fun nextTimeToExecute(enabledTime: Instant): Duration? {
        val enabledTimeEpochMillis = enabledTime.toEpochMilli()

        val currentTime = testInstant ?: Instant.now()
        val delta = currentTime.toEpochMilli() - enabledTimeEpochMillis
        // Remainder of the Delta time is how much we have already spent waiting.
        // We need to subtract remainder of that time from the interval time to get remaining schedule time to wait.
        val remainingScheduleTime = intervalInMills - delta.rem(intervalInMills)
        return Duration.of(remainingScheduleTime, ChronoUnit.MILLIS)
    }

    override fun getExpectedNextExecutionTime(enabledTime: Instant, expectedPreviousExecutionTime: Instant?): Instant? {
        val expectedPreviousExecutionTimeEpochMillis = (expectedPreviousExecutionTime ?: enabledTime).toEpochMilli()
        // We still need to calculate the delta even when using expectedPreviousExecutionTime because the initial value passed in
        // is the enabledTime (which also happens with cluster/node restart)
        val currentTime = testInstant ?: Instant.now()
        val delta = currentTime.toEpochMilli() - expectedPreviousExecutionTimeEpochMillis
        // Remainder of the Delta time is how much we have already spent waiting.
        // We need to subtract remainder of that time from the interval time to get remaining schedule time to wait.
        val remainingScheduleTime = intervalInMills - delta.rem(intervalInMills)
        return Instant.ofEpochMilli(currentTime.toEpochMilli() + remainingScheduleTime)
    }

    override fun getPeriodStartingAt(startTime: Instant?): Pair<Instant, Instant> {
        val realStartTime = startTime ?: Instant.now()
        val newEndTime = realStartTime.plusMillis(intervalInMills)
        return Pair(realStartTime, newEndTime)
    }

    override fun getPeriodEndingAt(endTime: Instant?): Pair<Instant, Instant> {
        val realEndTime = endTime ?: Instant.now()
        val newStartTime = realEndTime.minusMillis(intervalInMills)
        return Pair(newStartTime, realEndTime)
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
