/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Monitor
import org.elasticsearch.client.Response
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.search.builder.SearchSourceBuilder

class MonitorRunnerTests : MonitoringRestTestCase() {

    fun `test execute monitor`() {
         val action = randomAction(subjectTemplate = randomTemplateScript("Hello {{_ctx.monitor.name}}"),
                 messageTemplate = randomTemplateScript("Goodbye {{_ctx.monitor.name}}"))
         val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))))

        val response = executeMonitor(monitor)

        val output = entityAsMap(response)
         assertEquals(monitor.name, output["monitor_name"])
         for (triggerResult in output.objectMap("trigger_results").values) {
             for (actionResult in triggerResult.objectMap("action_results").values) {
                 @Suppress("UNCHECKED_CAST") val actionOutput = actionResult["output"] as Map<String, String>
                 assertEquals("Hello ${monitor.name}", actionOutput["subject"])
                 assertEquals("Goodbye ${monitor.name}", actionOutput["message"])
             }
         }
    }

    fun `test execute monitor not triggered`() {
        val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = NEVER_RUN)))

        val response = executeMonitor(monitor)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            assertTrue("Unexpected trigger was run", triggerResult.objectMap("action_results").isEmpty())
        }
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
    }

    fun `test execute action template error`() {
        // Intentional syntax error in mustache template
        val action = randomAction(subjectTemplate = randomTemplateScript("Hello {{_ctx.monitor.name"))
        val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))))

        val response = executeMonitor(monitor)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        for (triggerResult in output.objectMap("trigger_results").values) {
            for (actionResult in triggerResult.objectMap("action_results").values) {
                assertTrue("Missing action error message", (actionResult["error"] as String).isNotEmpty())
            }
        }
    }

    fun `test execute monitor search with period`() {
        val query = QueryBuilders.rangeQuery("monitor.last_update_time").gte("{{period_start}}").lte("{{period_end}}")
        val input = SearchInput(indices = listOf("_all"), query = SearchSourceBuilder().query(query))
        val triggerScript = """
            // make sure there is at least one monitor
            return _ctx.results[0].hits.hits.size() > 0
        """.trimIndent()
        val trigger = randomTrigger(condition = Script(triggerScript))
        val monitor = createMonitor(randomMonitor(inputs = listOf(input), triggers = listOf(trigger)), refresh = true)

        val response = executeMonitor(monitor.id)

        val output = entityAsMap(response)
        assertEquals(monitor.name, output["monitor_name"])
        val triggerResult = output.objectMap("trigger_results").objectMap(trigger.id)
        assertEquals(true, triggerResult["triggered"].toString().toBoolean())
        assertTrue("Unexpected trigger error message", triggerResult["error"]?.toString().isNullOrEmpty())
    }

    private fun executeMonitor(monitorId: String, params: Map<String, String> = mapOf("dryrun" to "true")) : Response =
            client().performRequest("POST", "/_awses/monitors/$monitorId/_execute", params)

    private fun executeMonitor(monitor: Monitor, params: Map<String, String> = mapOf("dryrun" to "true")) : Response =
            client().performRequest("POST", "/_awses/monitors/_execute", params, monitor.toHttpEntity())

    @Suppress("UNCHECKED_CAST")
    /** helper that returns a field in a json map whose values are all json objects */
    private fun Map<String, Any>.objectMap(key: String) : Map<String, Map<String, Any>> {
        return this[key] as Map<String, Map<String, Any>>
    }
}
