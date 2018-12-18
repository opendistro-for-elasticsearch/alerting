/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertError
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Alert.State.ACKNOWLEDGED
import com.amazon.elasticsearch.monitoring.model.Alert.State.ACTIVE
import com.amazon.elasticsearch.monitoring.model.Alert.State.COMPLETED
import com.amazon.elasticsearch.monitoring.model.Alert.State.ERROR
import com.amazon.elasticsearch.monitoring.model.Monitor
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MILLIS

class MonitorRunnerIT : MonitoringRestTestCase() {

    fun `test execute monitor with dryrun`() {
         val action = randomSNSAction(subjectTemplate = randomTemplateScript("Hello {{_ctx.monitor.name}}"),
                 messageTemplate = randomTemplateScript("Goodbye {{_ctx.monitor.name}}"))
         val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))))

        val response = executeMonitor(monitor, params = DRYRUN_MONITOR)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                @Suppress("UNCHECKED_CAST") val actionOutput = actionResult["output"] as Map<String, String>
                assertEquals("Hello ${monitor.name}", actionOutput["subject"])
                assertEquals("Goodbye ${monitor.name}", actionOutput["message"])
            }
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert saved for test monitor", 0, alerts.size)
    }

    fun `test execute monitor not triggered`() {
        val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = NEVER_RUN)))

        val response = executeMonitor(monitor)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            assertTrue("Unexpected trigger was run", triggerResult.objectMap("action_results").isEmpty())
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert saved for test monitor", 0, alerts.size)
    }

    fun `test active alert is updated on each run`() {
        val monitor = createMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN))))

        executeMonitor(monitor.id)
        val firstRunAlert = searchAlerts(monitor).single()
        verifyAlert(firstRunAlert, monitor)
        // Runner uses ThreadPool.CachedTimeThread thread which only updates once every 200 ms. Wait a bit to
        // see lastNotificationTime change.
        Thread.sleep(200)
        executeMonitor(monitor.id)
        val secondRunAlert = searchAlerts(monitor).single()
        verifyAlert(secondRunAlert, monitor)

        assertEquals("New alert was created, instead of updating existing alert.", firstRunAlert.id, secondRunAlert.id)
        assertEquals("Start time shouldn't change", firstRunAlert.startTime, secondRunAlert.startTime)
        assertNotEquals("Last notification should be different.",
                firstRunAlert.lastNotificationTime, secondRunAlert.lastNotificationTime)
    }

    fun `test execute monitor input error`() {
        // use a non-existent index to trigger an input error
        val input = SearchInput(indices = listOf("foo"), query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
        val monitor = createMonitor(randomMonitor(inputs = listOf(input),
                triggers = listOf(randomTrigger(condition = NEVER_RUN))))

        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        assertTrue("Missing monitor error message", (output["error"] as String).isNotEmpty())

        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ERROR)
    }

    fun `test acknowledged alert does not suppress subsequent errors`() {
        createIndex("foo", Settings.EMPTY)
        val input = SearchInput(indices = listOf("foo"), query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
        val monitor = createMonitor(randomMonitor(inputs = listOf(input),
                triggers = listOf(randomTrigger(condition = ALWAYS_RUN))))

        var response = executeMonitor(monitor.id)

        var output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        assertTrue("Unexpected monitor error message", (output["error"] as String?).isNullOrEmpty())
        val activeAlert = searchAlerts(monitor).single()
        verifyAlert(activeAlert, monitor)

        // Now acknowledge the alert and delete the index to cause the next run of the monitor to fail...
        acknowledgeAlerts(monitor, activeAlert)
        deleteIndex("foo")
        response = executeMonitor(monitor.id)

        output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        val errorAlert = searchAlerts(monitor).single { it.state == ERROR }
        verifyAlert(errorAlert, monitor, ERROR)
    }

    fun `test acknowledged alert is not updated unnecessarily`() {
        val monitor = createMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN))))
        executeMonitor(monitor.id)
        acknowledgeAlerts(monitor, searchAlerts(monitor).single())
        val acknowledgedAlert = searchAlerts(monitor).single()
        verifyAlert(acknowledgedAlert, monitor, ACKNOWLEDGED)

        // Runner uses ThreadPool.CachedTimeThread thread which only updates once every 200 ms. Wait a bit to
        // let lastNotificationTime change.  W/o this sleep the test can result in a false negative.
        Thread.sleep(200)
        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        val currentAlert = searchAlerts(monitor).single()
        assertEquals("Acknowledged alert was updated when nothing changed", currentAlert, acknowledgedAlert)
        for (triggerResult in output.objectMap("trigger_results").values) {
            assertTrue("Action run when alert is acknowledged.", triggerResult.objectMap("action_results").isEmpty())
        }
    }

    fun `test alert completion`() {
        val trigger = randomTrigger(condition = Script("_ctx.alert == null"))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))

        executeMonitor(monitor.id)
        val activeAlert = searchAlerts(monitor).single()
        verifyAlert(activeAlert, monitor)

        executeMonitor(monitor.id)
        assertTrue("There's still an active alert", searchAlerts(monitor, AlertIndices.ALERT_INDEX).isEmpty())
        val completedAlert = searchAlerts(monitor, AlertIndices.ALL_INDEX_PATTERN).single()
        verifyAlert(completedAlert, monitor, COMPLETED)
    }

    fun `test execute monitor script error`() {
        // This painless script should cause a syntax error
        val trigger = randomTrigger(condition = Script("foo bar baz"))
        val monitor = randomMonitor(triggers = listOf(trigger))

        val response = executeMonitor(monitor)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            assertTrue("Missing trigger error message", (triggerResult["error"] as String).isNotEmpty())
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert saved for test monitor", 0, alerts.size)
    }

    fun `test execute action template error`() {
        // Intentional syntax error in mustache template
        val action = randomAction(template = randomTemplateScript("Hello {{_ctx.monitor.name"))
        val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))))

        val response = executeMonitor(monitor)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                assertTrue("Missing action error message", (actionResult["error"] as String).isNotEmpty())
            }
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert saved for test monitor", 0, alerts.size)
    }

    fun `test execute monitor search with period`() {
        val query = QueryBuilders.rangeQuery("monitor.last_update_time").gte("{{period_start}}").lte("{{period_end}}")
        val input = SearchInput(indices = listOf("_all"), query = SearchSourceBuilder().query(query))
        val triggerScript = """
            // make sure there is at least one monitor
            return _ctx.results[0].hits.hits.size() > 0
        """.trimIndent()
        val trigger = randomTrigger(condition = Script(triggerScript))
        val monitor = createMonitor(randomMonitor(inputs = listOf(input), triggers = listOf(trigger)))

        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        val triggerResult = output.objectMap("trigger_results").objectMap(trigger.id)
        assertEquals(true, triggerResult["triggered"].toString().toBoolean())
        assertTrue("Unexpected trigger error message", triggerResult["error"]?.toString().isNullOrEmpty())

        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor)
    }

    fun `test execute monitor search with period date math`() {
        val testIndex = createTestIndex()
        val fiveDaysAgo = ZonedDateTime.now().minus(5, DAYS).truncatedTo(MILLIS)
        val testTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(fiveDaysAgo)
        val testDoc = """{ "test_strict_date_time" : "$testTime" }"""
        indexDoc(testIndex, "1", testDoc)

        // Queries that use period_start/end should expect these values to always be formatted as 'epoch_millis'. Either
        // the query should specify the format (like below) or the mapping for the index/field being queried should allow
        // epoch_millis as an alternative (ES's default mapping for date fields "strict_date_optional_time||epoch_millis")
        val query = QueryBuilders.rangeQuery("test_strict_date_time")
                .gt("{{period_end}}||-10d")
                .lte("{{period_end}}")
                .format("epoch_millis")
        val input = SearchInput(indices = listOf(testIndex), query = SearchSourceBuilder().query(query))
        val triggerScript = """
            // make sure there is exactly one hit
            return _ctx.results[0].hits.hits.size() == 1
        """.trimIndent()
        val trigger = randomTrigger(condition = Script(triggerScript))
        val monitor = randomMonitor(inputs = listOf(input), triggers = listOf(trigger))

        val response = executeMonitor(monitor)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        val triggerResult = output.objectMap("trigger_results").objectMap(trigger.id)
        assertEquals(true, triggerResult["triggered"].toString().toBoolean())
        assertTrue("Unexpected trigger error message", triggerResult["error"]?.toString().isNullOrEmpty())
        assertNotEquals("period incorrect", output["period_start"], output["period_end"])

        // Don't expect any alerts for this monitor as it has not been saved
        val alerts = searchAlerts(monitor)
        assertEquals("Alert saved for test monitor", 0, alerts.size)
    }

    fun `test monitor with one bad action and one good action`() {
        val goodAction = randomAction(template = randomTemplateScript("Hello {{_ctx.monitor.name}}"))
        val syntaxErrorAction = randomAction(name = "bad syntax", template = randomTemplateScript("{{foo"))
        val actions = listOf(goodAction, syntaxErrorAction)
        val monitor = createMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = actions))))

        val output = entityAsMap(executeMonitor(monitor.id))

        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                @Suppress("UNCHECKED_CAST") val actionOutput = actionResult["output"] as Map<String, String>
                if (actionResult["name"] == goodAction.name) {
                    assertEquals("Hello ${monitor.name}", actionOutput["message"])
                } else if (actionResult["name"] == syntaxErrorAction.name) {
                    assertTrue("Missing action error message", (actionResult["error"] as String).isNotEmpty())
                } else {
                    fail("Unknown action: ${actionResult["name"]}")
                }
            }
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ERROR)
    }

    fun `test execute monitor adds to alert error history`() {
        putAlertMappings() // Required as we do not have a create alert API.
        // This template script has a parsing error to purposefully create an errorMessage during runMonitor
        val action = randomAction(template = randomTemplateScript("Hello {{_ctx.monitor.name"))
        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))
        val listOfFiveErrorMessages = (1..5).map { i -> AlertError(timestamp = Instant.now(), message = "error message $i") }
        val activeAlert = createAlert(randomAlert(monitor).copy(state = ACTIVE, errorHistory = listOfFiveErrorMessages,
                triggerId = trigger.id, triggerName = trigger.name, severity = trigger.severity))

        val response = executeMonitor(monitor.id)

        val updatedAlert = searchAlerts(monitor).single()
        assertEquals("Existing active alert was not updated", activeAlert.id, updatedAlert.id)
        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                assertTrue("Missing action error message", (actionResult["error"] as String).isNotEmpty())
            }
        }
        assertEquals("Wrong number of error messages in history", 6, updatedAlert.errorHistory.size)
    }

    fun `test latest error is not lost when alert is completed`() {
        // Creates an active alert the first time it's run and completes it the second time the monitor is run.
        val trigger = randomTrigger(condition = Script("""
            if (_ctx.alert == null) {
                throw new RuntimeException("foo");
            } else {
                return false;
            }
        """.trimIndent()))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))

        executeMonitor(monitor.id)
        val errorAlert = searchAlerts(monitor).single()
        verifyAlert(errorAlert, monitor, ERROR)
        executeMonitor(monitor.id)
        val completedAlert = searchAlerts(monitor, AlertIndices.ALL_INDEX_PATTERN).single()
        verifyAlert(completedAlert, monitor, COMPLETED)

        assertNull("Completed alert still has error message.", completedAlert.errorMessage)
        assertTrue("Missing error history.", completedAlert.errorHistory.isNotEmpty())
        val latestError = completedAlert.errorHistory.single().message
        assertTrue("Latest error is missing from history.", latestError.contains("RuntimeException(\"foo\")"))
    }

    fun `test execute monitor limits alert error history to 10 error messages`() {
        putAlertMappings() // Required as we do not have a create alert API.
        // This template script has a parsing error to purposefully create an errorMessage during runMonitor
        val action = randomAction(template = randomTemplateScript("Hello {{_ctx.monitor.name"))
        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))
        val listOfTenErrorMessages = (1..10).map { i -> AlertError(timestamp = Instant.now(),message = "error message $i") }
        val activeAlert = createAlert(randomAlert(monitor).copy(state = ACTIVE, errorHistory = listOfTenErrorMessages,
                triggerId = trigger.id, triggerName = trigger.name, severity = trigger.severity))

        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                assertTrue("Missing action error message", (actionResult["error"] as String).isNotEmpty())
            }
        }
        val updatedAlert = searchAlerts(monitor).single()
        assertEquals("Existing active alert was not updated", activeAlert.id, updatedAlert.id)
        assertEquals("Wrong number of error messages in history", 10, updatedAlert.errorHistory.size)
    }

    fun `test execute monitor creates alert for trigger with no actions`() {
        putAlertMappings() // Required as we do not have a create alert API.

        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = emptyList())
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))

        executeMonitor(monitor.id)

        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ACTIVE)
    }

    fun `test execute monitor with bad search`() {
        val query = QueryBuilders.matchAllQuery()
        val input = SearchInput(indices = listOf("_#*IllegalIndexCharacters"), query = SearchSourceBuilder().query(query))
        val monitor = createMonitor(randomMonitor(inputs = listOf(input), triggers = listOf(randomTrigger(condition = ALWAYS_RUN))))

        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        assertTrue("Missing error message from a bad query", (output["error"] as String).isNotEmpty())
    }

    fun `test execute monitor non-dryrun`() {
        val monitor = createMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(randomAction())))))

        val response = executeMonitor(monitor.id, mapOf("dryrun" to "false"))

        assertEquals("failed dryrun", RestStatus.OK, response.restStatus())
        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ACTIVE)
    }

    fun `test execute monitor with already active alert`() {
        val monitor = createMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(randomAction())))))

        val firstExecuteResponse = executeMonitor(monitor.id, mapOf("dryrun" to "false"))

        assertEquals("failed dryrun", RestStatus.OK, firstExecuteResponse.restStatus())
        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ACTIVE)

        val secondExecuteReponse = executeMonitor(monitor.id, mapOf("dryrun" to "false"))

        assertEquals("failed dryrun", RestStatus.OK, firstExecuteResponse.restStatus())
        val newAlerts = searchAlerts(monitor)
        assertEquals("Second alert not saved", 1, newAlerts.size)
        verifyAlert(newAlerts.single(), monitor, ACTIVE)
    }

    private fun verifyAlert(alert: Alert, monitor: Monitor, expectedState: Alert.State = ACTIVE) {
        assertNotNull(alert.id)
        assertNotNull(alert.startTime)
        assertNotNull(alert.lastNotificationTime)
        assertEquals("Alert in wrong state", expectedState, alert.state)
        if (expectedState == ERROR) {
            assertNotNull("Missing error message", alert.errorMessage)
        } else {
            assertNull("Unexpected error message", alert.errorMessage)
        }
        if (expectedState == COMPLETED) {
            assertNotNull("End time missing for completed alert.", alert.endTime)
        } else {
            assertNull("End time set for active alert", alert.endTime)
        }
        assertEquals(monitor.id, alert.monitorId)
        assertEquals(monitor.name, alert.monitorName)
        assertEquals(monitor.version, alert.monitorVersion)

        // assert trigger exists for alert
        val trigger = monitor.triggers.single { it.id == alert.triggerId }
        assertEquals(trigger.name, alert.triggerName)
    }

    @Suppress("UNCHECKED_CAST")
    /** helper that returns a field in a json map whose values are all json objects */
    private fun Map<String, Any>.objectMap(key: String) : Map<String, Map<String, Any>> {
        return this[key] as Map<String, Map<String, Any>>
    }
}
