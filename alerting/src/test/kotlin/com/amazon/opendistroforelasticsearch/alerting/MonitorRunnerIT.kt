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

package com.amazon.opendistroforelasticsearch.alerting

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertError
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.core.model.IntervalSchedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.model.ActionExecutionResult
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ACKNOWLEDGED
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ACTIVE
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.COMPLETED
import com.amazon.opendistroforelasticsearch.alerting.model.Alert.State.ERROR
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.action.Throttle
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MILLIS
import java.time.temporal.ChronoUnit.MINUTES

class MonitorRunnerIT : AlertingRestTestCase() {

    fun `test execute monitor with dryrun`() {
        val action = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name}}"), destinationId = createDestination().id)
        val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))))

        val response = executeMonitor(monitor, params = DRYRUN_MONITOR)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                @Suppress("UNCHECKED_CAST") val actionOutput = actionResult["output"] as Map<String, String>
                assertEquals("Hello ${monitor.name}", actionOutput["subject"])
                assertEquals("Hello ${monitor.name}", actionOutput["message"])
            }
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert saved for test monitor", 0, alerts.size)
    }

    fun `test execute monitor returns search result`() {
        val uniqueName = "unique name"
        val query = QueryBuilders.termQuery("monitor.name.keyword", uniqueName)
        val input = SearchInput(indices = listOf("*"), query = SearchSourceBuilder().query(query))
        val monitor = createMonitor(randomMonitor(name = uniqueName, inputs = listOf(input),
                triggers = listOf(randomTrigger(condition = ALWAYS_RUN))))

        val response = executeMonitor(monitor, params = DRYRUN_MONITOR)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        @Suppress("UNCHECKED_CAST")
        val searchResult = (output.objectMap("input_results")["results"] as List<Map<String, Any>>).first()
        @Suppress("UNCHECKED_CAST")
        val total = searchResult.stringMap("hits")?.get("total") as Map<String, String>
        assertEquals("Incorrect search result", 1, total["value"])
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
        val monitor = createMonitor(
                randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, destinationId = createDestination().id))))

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
        @Suppress("UNCHECKED_CAST")
        val inputResults = output.stringMap("input_results")
        assertTrue("Missing monitor error message", (inputResults?.get("error") as String).isNotEmpty())

        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ERROR)
    }

    fun `test acknowledged alert does not suppress subsequent errors`() {
        val destinationId = createDestination().id

        createIndex("foo", Settings.EMPTY)
        val input = SearchInput(indices = listOf("foo"), query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
        val monitor = createMonitor(
                randomMonitor(inputs = listOf(input),
                triggers = listOf(randomTrigger(condition = ALWAYS_RUN, destinationId = destinationId))))

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
        val monitor = createMonitor(
                randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, destinationId = createDestination().id))))
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
        val trigger = randomTrigger(condition = Script("ctx.alert == null"), destinationId = createDestination().id)
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
        val action = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name"))
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
            return ctx.results[0].hits.hits.size() > 0
        """.trimIndent()
        val destinationId = createDestination().id
        val trigger = randomTrigger(condition = Script(triggerScript), destinationId = destinationId)
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
            return ctx.results[0].hits.hits.size() == 1
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
        val goodAction = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name}}"), destinationId = createDestination().id)
        val syntaxErrorAction = randomAction(
                name = "bad syntax",
                template = randomTemplateScript("{{foo"),
                destinationId = createDestination().id)
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
        val action = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name"))
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
            if (ctx.alert == null) {
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

    fun `test throw script exception`() {
        // Creates an active alert the first time it's run and completes it the second time the monitor is run.
        val trigger = randomTrigger(condition = Script("""
            param[0]; return true
        """.trimIndent()))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))

        executeMonitor(monitor.id)
        val errorAlert = searchAlerts(monitor).single()
        verifyAlert(errorAlert, monitor, ERROR)
        executeMonitor(monitor.id)
        assertEquals("Error does not match",
                "Error evaluating trigger:\nparam[0]; return true\n^---- HERE", errorAlert.errorMessage)
    }

    fun `test execute monitor limits alert error history to 10 error messages`() {
        putAlertMappings() // Required as we do not have a create alert API.
        // This template script has a parsing error to purposefully create an errorMessage during runMonitor
        val action = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name"))
        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))
        val listOfTenErrorMessages = (1..10).map { i -> AlertError(timestamp = Instant.now(), message = "error message $i") }
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

        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = emptyList(), destinationId = createDestination().id)
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
        @Suppress("UNCHECKED_CAST")
        val inputResults = output.stringMap("input_results")
        assertTrue("Missing error message from a bad query", (inputResults?.get("error") as String).isNotEmpty())
    }

    fun `test execute monitor non-dryrun`() {
        val monitor = createMonitor(
                randomMonitor(triggers = listOf(randomTrigger(
                        condition = ALWAYS_RUN,
                        actions = listOf(randomAction(destinationId = createDestination().id))))))

        val response = executeMonitor(monitor.id, mapOf("dryrun" to "false"))

        assertEquals("failed dryrun", RestStatus.OK, response.restStatus())
        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ACTIVE)
    }

    fun `test execute monitor with already active alert`() {
        val monitor = createMonitor(
                randomMonitor(triggers = listOf(randomTrigger(
                        condition = ALWAYS_RUN,
                        actions = listOf(randomAction(destinationId = createDestination().id))))))

        val firstExecuteResponse = executeMonitor(monitor.id, mapOf("dryrun" to "false"))

        assertEquals("failed dryrun", RestStatus.OK, firstExecuteResponse.restStatus())
        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ACTIVE)

        val secondExecuteResponse = executeMonitor(monitor.id, mapOf("dryrun" to "false"))

        assertEquals("failed dryrun", RestStatus.OK, secondExecuteResponse.restStatus())
        val newAlerts = searchAlerts(monitor)
        assertEquals("Second alert not saved", 1, newAlerts.size)
        verifyAlert(newAlerts.single(), monitor, ACTIVE)
    }

    fun `test delete monitor with no alerts after alert indices is initialized`() {
        putAlertMappings()

        val newMonitor = createMonitor(
                randomMonitor(triggers = listOf(randomTrigger(condition = NEVER_RUN, actions = listOf(randomAction())))))
        val deleteNewMonitorResponse = client().makeRequest("DELETE", "$ALERTING_BASE_URI/${newMonitor.id}")

        assertEquals("Delete request not successful", RestStatus.OK, deleteNewMonitorResponse.restStatus())
    }

    fun `test update monitor stays on schedule`() {
        val monitor = createMonitor(randomMonitor(enabled = true))

        updateMonitor(monitor.copy(enabledTime = Instant.now()))

        val retrievedMonitor = getMonitor(monitorId = monitor.id)
        assertEquals("Monitor enabled time changed.", monitor.enabledTime, retrievedMonitor.enabledTime)
    }

    fun `test enabled time by disabling and re-enabling monitor`() {
        val monitor = createMonitor(randomMonitor(enabled = true))
        assertNotNull("Enabled time is null on a enabled monitor.", getMonitor(monitor.id).enabledTime)

        val disabledMonitor = updateMonitor(randomMonitor(enabled = false).copy(id = monitor.id))
        assertNull("Enabled time is not null on a disabled monitor.", disabledMonitor.enabledTime)

        val enabledMonitor = updateMonitor(randomMonitor(enabled = true).copy(id = monitor.id))
        assertNotNull("Enabled time is null on a enabled monitor.", enabledMonitor.enabledTime)
    }

    fun `test enabled time by providing enabled time`() {
        val enabledTime = Instant.ofEpochSecond(1538164858L) // This is 2018-09-27 20:00:58 GMT
        val monitor = createMonitor(randomMonitor(enabled = true, enabledTime = enabledTime))

        val retrievedMonitor = getMonitor(monitorId = monitor.id)
        assertTrue("Monitor is not enabled", retrievedMonitor.enabled)
        assertEquals("Enabled times do not match", monitor.enabledTime, retrievedMonitor.enabledTime)
    }

    fun `test monitor with throttled action for same alert`() {
        val actionThrottleEnabled = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name}}"),
                destinationId = createDestination().id,
                throttleEnabled = true, throttle = Throttle(value = 5, unit = MINUTES))
        val actionThrottleNotEnabled = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name}}"),
                destinationId = createDestination().id,
                throttleEnabled = false, throttle = Throttle(value = 5, unit = MINUTES))
        val actions = listOf(actionThrottleEnabled, actionThrottleNotEnabled)
        val monitor = createMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = actions)),
                schedule = IntervalSchedule(interval = 1, unit = ChronoUnit.MINUTES)))
        val monitorRunResultNotThrottled = entityAsMap(executeMonitor(monitor.id))
        verifyActionThrottleResults(monitorRunResultNotThrottled, mutableMapOf(Pair(actionThrottleEnabled.id, false),
                Pair(actionThrottleNotEnabled.id, false)))

        val notThrottledAlert = searchAlerts(monitor)
        assertEquals("1 alert should be returned", 1, notThrottledAlert.size)
        verifyAlert(notThrottledAlert.single(), monitor, ACTIVE)
        val notThrottledActionResults = verifyActionExecutionResultInAlert(notThrottledAlert[0],
                mutableMapOf(Pair(actionThrottleEnabled.id, 0), Pair(actionThrottleNotEnabled.id, 0)))

        assertEquals(notThrottledActionResults.size, 2)
        val monitorRunResultThrottled = entityAsMap(executeMonitor(monitor.id))
        verifyActionThrottleResults(monitorRunResultThrottled, mutableMapOf(Pair(actionThrottleEnabled.id, true),
                Pair(actionThrottleNotEnabled.id, false)))

        val throttledAlert = searchAlerts(monitor)
        assertEquals("1 alert should be returned", 1, throttledAlert.size)
        verifyAlert(throttledAlert.single(), monitor, ACTIVE)
        val throttledActionResults = verifyActionExecutionResultInAlert(throttledAlert[0],
                mutableMapOf(Pair(actionThrottleEnabled.id, 1), Pair(actionThrottleNotEnabled.id, 0)))

        assertEquals(notThrottledActionResults.size, 2)

        assertEquals(notThrottledActionResults[actionThrottleEnabled.id]!!.lastExecutionTime,
                throttledActionResults[actionThrottleEnabled.id]!!.lastExecutionTime)
    }

    fun `test monitor with throttled action for different alerts`() {
        val actionThrottleEnabled = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name}}"),
                destinationId = createDestination().id,
                throttleEnabled = true, throttle = Throttle(value = 5, unit = MINUTES))
        val actions = listOf(actionThrottleEnabled)
        val trigger = randomTrigger(condition = ALWAYS_RUN, actions = actions)
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger),
                schedule = IntervalSchedule(interval = 1, unit = ChronoUnit.MINUTES)))
        val monitorRunResult1 = entityAsMap(executeMonitor(monitor.id))
        verifyActionThrottleResults(monitorRunResult1, mutableMapOf(Pair(actionThrottleEnabled.id, false)))

        val activeAlert1 = searchAlerts(monitor)
        assertEquals("1 alert should be returned", 1, activeAlert1.size)
        verifyAlert(activeAlert1.single(), monitor, ACTIVE)
        val actionResults1 = verifyActionExecutionResultInAlert(activeAlert1[0], mutableMapOf(Pair(actionThrottleEnabled.id, 0)))

        updateMonitor(monitor.copy(triggers = listOf(trigger.copy(condition = NEVER_RUN)), id = monitor.id))
        executeMonitor(monitor.id)
        val completedAlert = searchAlerts(monitor, AlertIndices.ALL_INDEX_PATTERN).single()
        verifyAlert(completedAlert, monitor, COMPLETED)

        updateMonitor(monitor.copy(triggers = listOf(trigger.copy(condition = ALWAYS_RUN)), id = monitor.id))
        val monitorRunResult2 = entityAsMap(executeMonitor(monitor.id))
        verifyActionThrottleResults(monitorRunResult2, mutableMapOf(Pair(actionThrottleEnabled.id, false)))
        val activeAlert2 = searchAlerts(monitor)
        assertEquals("1 alert should be returned", 1, activeAlert2.size)
        assertNotEquals(activeAlert1[0].id, activeAlert2[0].id)

        val actionResults2 = verifyActionExecutionResultInAlert(activeAlert2[0], mutableMapOf(Pair(actionThrottleEnabled.id, 0)))
        assertNotEquals(actionResults1[actionThrottleEnabled.id]!!.lastExecutionTime,
                actionResults2[actionThrottleEnabled.id]!!.lastExecutionTime)
    }

    fun `test monitor HttpInput with non JSON response `() {
        val clusterIndex = randomInt(clusterHosts.size - 1)
        val input = randomHttpInput(
                scheme = clusterHosts[clusterIndex].schemeName,
                host = clusterHosts[clusterIndex].hostName,
                port = clusterHosts[clusterIndex].port,
                path = "_cat/indices",
                params = mapOf(),
                url = "",
                connection_timeout = 5000,
                socket_timeout = 5000)
        val monitor = createMonitor(randomMonitor(inputs = listOf(input)))
        val response = executeMonitor(monitor.id)
        val output = entityAsMap(response)
        @Suppress("UNCHECKED_CAST")
        val inputResults = output.stringMap("input_results")
        @Suppress("UNCHECKED_CAST")
        val errorMessage = inputResults?.get("error")
        assertTrue("Error did not occur from receiving invalid format of response, error message is actually: " +
                "$errorMessage\nOutput: $output",
                errorMessage.toString().contains("Unrecognized token"))
    }

    fun `test monitor HttpInput with JSON response`() {
        val clusterIndex = randomInt(clusterHosts.size - 1)
        val input = randomHttpInput(
                scheme = clusterHosts[clusterIndex].schemeName,
                host = clusterHosts[clusterIndex].hostName,
                port = clusterHosts[clusterIndex].port,
                path = "_cluster/health",
                params = mapOf(),
                url = "",
                connection_timeout = 5000,
                socket_timeout = 5000)
        val monitor = createMonitor(randomMonitor(inputs = listOf(input)))
        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        @Suppress("UNCHECKED_CAST")
        val inputResults = output.stringMap("input_results")
        val resultsContent = (inputResults?.get("results") as ArrayList<*>).get(0)
        val errorMessage = inputResults.get("error")

        assertEquals(monitor.name, output["monitor_name"])
        @Suppress("UNCHECKED_CAST")
        assertTrue("Monitor results should contain cluster_name field, but was actually: $resultsContent",
                resultsContent.toString().contains("cluster_name"))
        @Suppress("UNCHECKED_CAST")
        assertTrue("Error message should not exist, error: $errorMessage", errorMessage == null)
    }

    fun `test monitor HttpInput with alert triggered`() {
        putAlertMappings() // Required as we do not have a create alert API.
        val trigger = randomTrigger(condition = Script("""
            return ctx.results[0].number_of_pending_tasks < 1
        """.trimIndent()), destinationId = createDestination().id)
        val clusterIndex = randomInt(clusterHosts.size - 1)
        val input = randomHttpInput(
                scheme = clusterHosts[clusterIndex].schemeName,
                host = clusterHosts[clusterIndex].hostName,
                port = clusterHosts[clusterIndex].port,
                path = "_cluster/health",
                params = mapOf(),
                url = "",
                connection_timeout = 5000,
                socket_timeout = 5000)
        val monitor = createMonitor(randomMonitor(inputs = listOf(input), triggers = listOf(trigger)))
        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            assertTrue("Should be triggered.", triggerResult.objectMap("action_results").isNotEmpty())
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert not saved, $output", 1, alerts.size)
        verifyAlert(alerts.single(), monitor, ACTIVE)
    }

    fun `test monitor HttpInput with no alert triggered`() {
        putAlertMappings() // Required as we do not have a create alert API.
        val trigger = randomTrigger(condition = Script("""
            return ctx.results[0].status.equals("red")
        """.trimIndent()))
        val clusterIndex = randomInt(clusterHosts.size - 1)
        val input = randomHttpInput(
                scheme = clusterHosts[clusterIndex].schemeName,
                host = clusterHosts[clusterIndex].hostName,
                port = clusterHosts[clusterIndex].port,
                path = "_cluster/health",
                params = mapOf(),
                url = "",
                connection_timeout = 5000,
                socket_timeout = 5000)
        val monitor = createMonitor(randomMonitor(inputs = listOf(input), triggers = listOf(trigger)))
        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            val actionResults = triggerResult.objectMap("action_results")
            @Suppress("UNCHECKED_CAST")
            assertTrue("Should not be triggered, $actionResults", actionResults.isEmpty())
        }

        val alerts = searchAlerts(monitor)
        assertEquals("Alert saved for test monitor, output: $output", 0, alerts.size)
    }

    private fun verifyActionExecutionResultInAlert(alert: Alert, expectedResult: Map<String, Int>):
            MutableMap<String, ActionExecutionResult> {
        val actionResult = mutableMapOf<String, ActionExecutionResult>()
        for (result in alert.actionExecutionResults) {
            val expected = expectedResult[result.actionId]
            assertEquals(expected, result.throttledCount)
            actionResult.put(result.actionId, result)
        }
        return actionResult
    }

    private fun verifyActionThrottleResults(output: MutableMap<String, Any>, expectedResult: Map<String, Boolean>) {
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                val expected = expectedResult[actionResult["id"]]
                assertEquals(expected, actionResult["throttled"])
            }
        }
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
    private fun Map<String, Any>.objectMap(key: String): Map<String, Map<String, Any>> {
        return this[key] as Map<String, Map<String, Any>>
    }
}
