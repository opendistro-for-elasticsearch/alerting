/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertError
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import org.elasticsearch.index.query.QueryBuilders
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
        verifyAlert(alerts.single(), monitor, Alert.State.ERROR)
    }

    fun `test execute monitor adds to alert error history`() {
        putAlertMappings() // Required as we do not have a create alert API.
        // This template script has a parsing error to purposefully create an errorMessage during runMonitor
        val action = randomAction(template = randomTemplateScript("Hello {{_ctx.monitor.name"))
        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))
        val listOfFiveErrorMessages = (1..5).map { i -> AlertError(timestamp = Instant.now(), message = "error message $i") }
        val activeAlert = createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE, errorHistory = listOfFiveErrorMessages,
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
        assertEquals("Wrong number of error messages in history",6, updatedAlert.errorHistory.size)
    }

    fun `test execute monitor limits alert error history to 10 error messages`() {
        putAlertMappings() // Required as we do not have a create alert API.
        // This template script has a parsing error to purposefully create an errorMessage during runMonitor
        val action = randomAction(template = randomTemplateScript("Hello {{_ctx.monitor.name"))
        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))
        val listOfTenErrorMessages = (1..10).map { i -> AlertError(timestamp = Instant.now(),message = "error message $i") }
        val activeAlert = createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE, errorHistory = listOfTenErrorMessages,
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
        verifyAlert(alerts.single(), monitor, Alert.State.ACTIVE)
    }

    private fun verifyAlert(alert: Alert, monitor: Monitor, expectedState: Alert.State = Alert.State.ACTIVE) {
        assertNotNull(alert.id)
        assertNotNull(alert.startTime)
        assertNotNull(alert.lastNotificationTime)
        assertEquals("Alert in wrong state", expectedState, alert.state)
        if (expectedState == Alert.State.ERROR) {
            assertNotNull("Missing error message", alert.errorMessage)
        } else {
            assertNull("Unexpected error message", alert.errorMessage)
        }
        assertEquals(monitor.id, alert.monitorId)
        assertEquals(monitor.name, alert.monitorName)
        assertEquals(monitor.version, alert.monitorVersion)

        // assert trigger exists for alert
        val trigger = monitor.triggers.filter { it.id == alert.triggerId }.single()
        assertEquals(trigger.name, alert.triggerName)
    }

    @Suppress("UNCHECKED_CAST")
    /** helper that returns a field in a json map whose values are all json objects */
    private fun Map<String, Any>.objectMap(key: String) : Map<String, Map<String, Any>> {
        return this[key] as Map<String, Map<String, Any>>
    }
}
