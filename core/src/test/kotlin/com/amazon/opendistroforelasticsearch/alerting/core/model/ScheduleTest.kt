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

import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import org.elasticsearch.common.xcontent.ToXContent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduleTest : XContentTestBase {
    @Test
    fun `test time zone conversion`() {
        val cronExpression = "31 * * * *" // Run at minute 31.
        // This is 2018-09-27 20:00:58 GMT which will in conversion lead to 30min 58 seconds IST
        val testInstance = Instant.ofEpochSecond(1538164858L)

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("Asia/Kolkata"), testInstance)
        val nextTimeToExecute = cronSchedule.nextTimeToExecute(Instant.now())
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertEquals(2L, nextTimeToExecute.seconds, "Execute time should be 2 seconds")
    }

    @Test
    fun `test time zone`() {
        val cronExpression = "0 11 * * 3" // Run at 11:00 on Wednesday.
        // This is 2018-09-26 01:59:58 GMT which will in conversion lead to Wednesday 10:59:58 JST
        val testInstance = Instant.ofEpochSecond(1537927198L)

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("Asia/Tokyo"), testInstance)
        val nextTimeToExecute = cronSchedule.nextTimeToExecute(Instant.now())
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertEquals(2L, nextTimeToExecute.seconds, "Execute time should be 2 seconds")
    }

    @Test
    fun `test cron calculates next time to execute after restart`() {
        val cronExpression = "* * * * *"
        // This is 2018-09-26 01:59:58 GMT
        val testInstance = Instant.ofEpochSecond(1537927198L)
        // This enabled time represents GMT: Wednesday, September 19, 2018 3:19:51 AM
        val enabledTimeInstance = Instant.ofEpochSecond(1537327191)

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("America/Los_Angeles"), testInstance)
        // The nextTimeToExecute should be the minute after the test instance, not enabledTimeInstance, replicating a cluster restart
        val nextTimeToExecute = cronSchedule.getExpectedNextExecutionTime(enabledTimeInstance, null)
        assertNotNull(nextTimeToExecute, "There should be next execute time")
        assertEquals(testInstance.plusSeconds(2L), nextTimeToExecute,
                "nextTimeToExecute should be 2 seconds after test instance")
    }

    @Test
    fun `test cron calculates next time to execute using cached previous time`() {
        val cronExpression = "* * * * *"
        // This is 2018-09-26 01:59:58 GMT
        val previousExecutionTimeInstance = Instant.ofEpochSecond(1537927198L)
        // This enabled time represents GMT: Wednesday, September 19, 2018 3:19:51 AM
        val enabledTimeInstance = Instant.ofEpochSecond(1537327191)

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("America/Los_Angeles"))
        // The nextTimeToExecute should be the minute after the previous execution time instance, not enabledTimeInstance
        val nextTimeToExecute = cronSchedule.getExpectedNextExecutionTime(enabledTimeInstance, previousExecutionTimeInstance)
        assertNotNull(nextTimeToExecute, "There should be next execute time")
        assertEquals(previousExecutionTimeInstance.plusSeconds(2L), nextTimeToExecute,
                "nextTimeToExecute should be 2 seconds after test instance")
    }

    @Test
    fun `test interval calculates next time to execute using enabled time`() {
        // This enabled time represents 2018-09-26 01:59:58 GMT
        val enabledTimeInstance = Instant.ofEpochSecond(1537927138L)
        // This is 2018-09-26 01:59:59 GMT, which is 61 seconds after enabledTime
        val testInstance = Instant.ofEpochSecond(1537927199L)

        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES, testInstance)

        // The nextTimeToExecute should be 120 seconds after the enabled time
        val nextTimeToExecute = intervalSchedule.getExpectedNextExecutionTime(enabledTimeInstance, null)
        assertNotNull(nextTimeToExecute, "There should be next execute time")
        assertEquals(enabledTimeInstance.plusSeconds(120L), nextTimeToExecute,
                "nextTimeToExecute should be 120 seconds seconds after enabled time")
    }

    @Test
    fun `test interval calculates next time to execute using cached previous time`() {
        // This is 2018-09-26 01:59:58 GMT
        val previousExecutionTimeInstance = Instant.ofEpochSecond(1537927198L)
        // This is 2018-09-26 02:00:00 GMT
        val testInstance = Instant.ofEpochSecond(1537927200L)
        // This enabled time represents 2018-09-26 01:58:58 GMT
        val enabledTimeInstance = Instant.ofEpochSecond(1537927138L)

        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES, testInstance)

        // The nextTimeToExecute should be the minute after the previous execution time instance
        val nextTimeToExecute = intervalSchedule.getExpectedNextExecutionTime(enabledTimeInstance, previousExecutionTimeInstance)
        assertNotNull(nextTimeToExecute, "There should be next execute time")
        assertEquals(previousExecutionTimeInstance.plusSeconds(60L), nextTimeToExecute,
                "nextTimeToExecute should be 60 seconds after previous execution time")
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
    fun `test invalid type`() {
        val scheduleString = "{\"foobarzzz\":{\"expression\":\"0 * * * *\",\"timezone\":\"+++9\"}}"
        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            Schedule.parse(parser(scheduleString))
        }
    }

    @Test
    fun `test two types`() {
        val scheduleString = "{\"cron\":{\"expression\":\"0 * * * *\",\"timezone\":\"Asia/Tokyo\"}, " +
                "\"period\":{\"interval\":\"1\",\"unit\":\"Minutes\"}}"
        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            Schedule.parse(parser(scheduleString))
        }
    }

    @Test
    fun `test invalid cron expression`() {
        val scheduleString = "{\"cron\":{\"expression\":\"5 * 1 * * *\",\"timezone\":\"Asia/Tokyo\"}}"
        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            Schedule.parse(parser(scheduleString))
        }
    }

    @Test
    fun `test interval period starting at`() {
        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES)

        val (periodStartTime, periodEndTime) = intervalSchedule.getPeriodStartingAt(null)

        assertEquals(periodStartTime, periodEndTime.minus(1, ChronoUnit.MINUTES), "Period didn't match interval")

        val startTime = Instant.now()
        // Kotlin has destructuring declarations but no destructuring assignments? Gee, thanks...
        val (periodStartTime2, _) = intervalSchedule.getPeriodStartingAt(startTime)
        assertEquals(startTime, periodStartTime2, "Periods doesn't start at provided start time")
    }

    @Test
    fun `test interval period ending at`() {
        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES)

        val (periodStartTime, periodEndTime) = intervalSchedule.getPeriodEndingAt(null)

        assertEquals(periodStartTime, periodEndTime.minus(1, ChronoUnit.MINUTES), "Period didn't match interval")

        val endTime = Instant.now()
        //  destructuring declarations but no destructuring assignments? Gee, thanks... https://youtrack.jetbrains.com/issue/KT-11362
        val (_, periodEndTime2) = intervalSchedule.getPeriodEndingAt(endTime)
        assertEquals(endTime, periodEndTime2, "Periods doesn't end at provided end time")
    }

    @Test
    fun `test cron period starting at`() {
        val cronSchedule = CronSchedule("0 * * * *", ZoneId.of("Asia/Tokyo"))

        val (startTime1, endTime) = cronSchedule.getPeriodStartingAt(null)
        assertTrue(startTime1 <= Instant.now(), "startTime is in future; should be the last execution time")
        assertTrue(cronSchedule.executionTime.isMatch(ZonedDateTime.ofInstant(endTime, ZoneId.of("Asia/Tokyo"))))

        val (startTime, _) = cronSchedule.getPeriodStartingAt(endTime)
        assertEquals(startTime, endTime, "Subsequent period doesn't start at provided end time")
    }

    @Test
    fun `test cron period ending at`() {
        val cronSchedule = CronSchedule("0 * * * *", ZoneId.of("Asia/Tokyo"))

        val (startTime, endTime1) = cronSchedule.getPeriodEndingAt(null)
        assertTrue(endTime1 >= Instant.now(), "endTime is in past; should be the next execution time")
        assertTrue(cronSchedule.executionTime.isMatch(ZonedDateTime.ofInstant(startTime, ZoneId.of("Asia/Tokyo"))))

        val (_, endTime2) = cronSchedule.getPeriodEndingAt(startTime)
        assertEquals(endTime2, startTime, "Previous period doesn't end at provided start time")
    }

    @Test
    fun `cron job not running on time`() {
        val cronSchedule = createTestCronSchedule()

        val lastExecutionTime = 1539715560L
        assertFalse(cronSchedule.runningOnTime(Instant.ofEpochSecond(lastExecutionTime)))
    }

    @Test
    fun `cron job running on time`() {
        val cronSchedule = createTestCronSchedule()

        val lastExecutionTime = 1539715620L
        assertTrue(cronSchedule.runningOnTime(Instant.ofEpochSecond(lastExecutionTime)))
    }

    @Test
    fun `period job running exactly at interval`() {
        val testInstance = Instant.ofEpochSecond(1539715678L)
        val enabledTime = Instant.ofEpochSecond(1539615178L)
        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES, testInstance)

        val nextTimeToExecute = intervalSchedule.nextTimeToExecute(enabledTime)
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertEquals(60L, nextTimeToExecute.seconds, "Excepted 60 seconds but was ${nextTimeToExecute.seconds}")
    }

    @Test
    fun `period job 3 minutes`() {
        val testInstance = Instant.ofEpochSecond(1539615226L)
        val enabledTime = Instant.ofEpochSecond(1539615144L)
        val intervalSchedule = IntervalSchedule(3, ChronoUnit.MINUTES, testInstance)

        val nextTimeToExecute = intervalSchedule.nextTimeToExecute(enabledTime)
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertEquals(98L, nextTimeToExecute.seconds, "Excepted 98 seconds but was ${nextTimeToExecute.seconds}")
    }

    @Test
    fun `period job running on time`() {
        val intervalSchedule = createTestIntervalSchedule()

        val lastExecutionTime = 1539715620L
        assertTrue(intervalSchedule.runningOnTime(Instant.ofEpochSecond(lastExecutionTime)))
    }

    @Test
    fun `period job not running on time`() {
        val intervalSchedule = createTestIntervalSchedule()

        val lastExecutionTime = 1539715560L
        assertFalse(intervalSchedule.runningOnTime(Instant.ofEpochSecond(lastExecutionTime)))
    }

    @Test
    fun `period job test null last execution time`() {
        val intervalSchedule = createTestIntervalSchedule()

        assertTrue(intervalSchedule.runningOnTime(null))
    }

    private fun createTestIntervalSchedule(): IntervalSchedule {
        val testInstance = Instant.ofEpochSecond(1539715678L)
        val enabledTime = Instant.ofEpochSecond(1539615146L)
        val intervalSchedule = IntervalSchedule(1, ChronoUnit.MINUTES, testInstance)

        val nextTimeToExecute = intervalSchedule.nextTimeToExecute(enabledTime)
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertEquals(28L, nextTimeToExecute.seconds, "Excepted 28 seconds but was ${nextTimeToExecute.seconds}")

        return intervalSchedule
    }

    private fun createTestCronSchedule(): CronSchedule {
        val cronExpression = "* * * * *"
        val testInstance = Instant.ofEpochSecond(1539715678L)

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("UTC"), testInstance)
        val nextTimeToExecute = cronSchedule.nextTimeToExecute(Instant.now())
        assertNotNull(nextTimeToExecute, "There should be next execute time.")
        assertEquals(2L, nextTimeToExecute.seconds, "Execute time should be 2 seconds")

        return cronSchedule
    }

    @Test
    fun `test invalid interval units`() {
        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            IntervalSchedule(1, ChronoUnit.SECONDS)
        }

        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            IntervalSchedule(1, ChronoUnit.MONTHS)
        }

        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            IntervalSchedule(-1, ChronoUnit.MINUTES)
        }
    }
}
