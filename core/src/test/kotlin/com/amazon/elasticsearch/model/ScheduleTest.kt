package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.ToXContent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduleTest : XContentTestBase {
    @Test
    fun `test time zone conversion`() {
        val cronExpression = "31 * * * *" // Run at minute 31.
        val testInstance = Instant.ofEpochSecond(1538164858L) // This is 2018-09-27 20:00:58 GMT which will in conversion lead to 30min 58 seconds IST

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("Asia/Kolkata"), testInstance)
        val nextTimeToExecute = cronSchedule.nextTimeToExecute()
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertTrue(nextTimeToExecute!!.seconds == 2L, "Execute time should be 2 seconds")
    }

    @Test
    fun `test time zone`() {
        val cronExpression = "0 11 * * 3" // Run at 11:00 on Wednesday.
        val testInstance = Instant.ofEpochSecond(1537927198L) // This is 2018-09-26 01:59:58 GMT which will in conversion lead to Wednesday 10:59:58 JST

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("Asia/Tokyo"), testInstance)
        val nextTimeToExecute = cronSchedule.nextTimeToExecute()
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertTrue(nextTimeToExecute!!.seconds == 2L, "Execute time should be 2 seconds")
    }
    @Test
    fun `test cron schedule round trip`() {
        val cronExpression = "0 * * * *"
        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("Asia/Tokyo"))

        val scheduleString = cronSchedule.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedSchedule = Schedule.parse(parser(scheduleString))

        assertTrue(parsedSchedule is CronSchedule, "Parsed scheduled is not Cron Scheduled Type.")
        assertEquals(cronSchedule, parsedSchedule, "Round tripping Cron Schedule doesn't work")
    }

    @Test
    fun `test interval schedule round trip`() {
        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES)

        val scheduleString = intervalSchedule.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedSchedule = Schedule.parse(parser(scheduleString))
        assertTrue(parsedSchedule is IntervalSchedule, "Parsed scheduled is not Interval Scheduled Type.")
        assertEquals(intervalSchedule, parsedSchedule, "Round tripping Interval Schedule doesn't work")
    }

    @Test
    fun `test cron invalid missing timezone`() {
        val scheduleString = "{\"cron\":{\"expression\":\"0 * * * *\"}}"
        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            Schedule.parse(parser(scheduleString))
        }
    }

    @Test
    fun `test cron invalid timezone rule`() {
        val scheduleString = "{\"cron\":{\"expression\":\"0 * * * *\",\"timezone\":\"Going/Nowhere\"}}"
        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            Schedule.parse(parser(scheduleString))
        }
    }

    @Test
    fun `test cron invalid timezone offset`() {
        val scheduleString = "{\"cron\":{\"expression\":\"0 * * * *\",\"timezone\":\"+++9\"}}"
        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            Schedule.parse(parser(scheduleString))
        }
    }

    @Test
    fun `test interval period`() {
        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES)

        val (startTime, endTime) = intervalSchedule.getPeriodStartEnd(null)

        assertEquals(startTime, endTime.minus(1, ChronoUnit.MINUTES), "Period didn't match interval")

        // Kotlin has destructuring declarations but no destructuring assignments? Gee, thanks...
        val (startTime2, endTime2) =  intervalSchedule.getPeriodStartEnd(endTime)
        assertEquals(startTime2, endTime, "Periods don't overlap")
        assertEquals(startTime, endTime.minus(1, ChronoUnit.MINUTES), "Period didn't match interval")
    }

    @Test
    fun `test cron period`() {
        val cronSchedule = CronSchedule("0 * * * *", ZoneId.of("Asia/Tokyo"))

        val (startTime, endTime) = cronSchedule.getPeriodStartEnd(null)

        assertTrue(cronSchedule.executionTime.isMatch(ZonedDateTime.ofInstant(endTime, ZoneId.of("Asia/Tokyo"))))
        assertNotNull(startTime, "Start time is null for cron")

        val (startTime2, endTime2) =  cronSchedule.getPeriodStartEnd(endTime)
        assertEquals(startTime2, endTime, "Periods don't overlap")
    }
}