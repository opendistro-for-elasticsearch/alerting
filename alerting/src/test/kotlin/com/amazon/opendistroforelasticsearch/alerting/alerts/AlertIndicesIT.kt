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

package com.amazon.opendistroforelasticsearch.alerting.alerts

import com.amazon.opendistroforelasticsearch.alerting.ALWAYS_RUN
import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.NEVER_RUN
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.randomMonitor
import com.amazon.opendistroforelasticsearch.alerting.randomTrigger
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.makeRequest
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.apache.http.entity.StringEntity
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.common.xcontent.json.JsonXContent.jsonXContent
import org.elasticsearch.rest.RestStatus

class AlertIndicesIT : AlertingRestTestCase() {

    fun `test create alert index`() {
        executeMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN))))

        assertIndexExists(AlertIndices.ALERT_INDEX)
        assertIndexExists(AlertIndices.HISTORY_WRITE_INDEX)
    }

    fun `test update alert index mapping with new schema version`() {
        assertIndexDoesNotExist(AlertIndices.ALERT_INDEX)
        assertIndexDoesNotExist(AlertIndices.HISTORY_WRITE_INDEX)

        putAlertMappings(AlertIndices.alertMapping().trimStart('{').trimEnd('}')
                .replace("\"schema_version\": 1", "\"schema_version\": 0"))
        assertIndexExists(AlertIndices.ALERT_INDEX)
        assertIndexExists(AlertIndices.HISTORY_WRITE_INDEX)
        verifyIndexSchemaVersion(AlertIndices.ALERT_INDEX, 0)
        verifyIndexSchemaVersion(AlertIndices.HISTORY_WRITE_INDEX, 0)
        client().makeRequest("DELETE", "*")
        executeMonitor(createRandomMonitor())
        assertIndexExists(AlertIndices.ALERT_INDEX)
        assertIndexExists(AlertIndices.HISTORY_WRITE_INDEX)
        verifyIndexSchemaVersion(ScheduledJob.SCHEDULED_JOBS_INDEX, 1)
        verifyIndexSchemaVersion(AlertIndices.ALERT_INDEX, 1)
        verifyIndexSchemaVersion(AlertIndices.HISTORY_WRITE_INDEX, 1)
    }

    fun `test alert index gets recreated automatically if deleted`() {
        assertIndexDoesNotExist(AlertIndices.ALERT_INDEX)
        val trueMonitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN)))

        executeMonitor(trueMonitor)
        assertIndexExists(AlertIndices.ALERT_INDEX)
        assertIndexExists(AlertIndices.HISTORY_WRITE_INDEX)

        client().makeRequest("DELETE", "*")
        assertIndexDoesNotExist(AlertIndices.ALERT_INDEX)
        assertIndexDoesNotExist(AlertIndices.HISTORY_WRITE_INDEX)

        val executeResponse = executeMonitor(trueMonitor)
        val xcp = createParser(XContentType.JSON.xContent(), executeResponse.entity.content)
        val output = xcp.map()
        assertNull("Error running a monitor after wiping alert indices", output["error"])
    }

    fun `test rollover history index`() {
        // Update the rollover check to be every 1 second and the index max age to be 1 second
        client().updateSettings(AlertingSettings.ALERT_HISTORY_ROLLOVER_PERIOD.key, "1s")
        client().updateSettings(AlertingSettings.ALERT_HISTORY_INDEX_MAX_AGE.key, "1s")

        val trueMonitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN)))
        executeMonitor(trueMonitor)

        // Allow for a rollover index.
        Thread.sleep(2000)
        assertTrue("Did not find 3 alert indices", getAlertIndices().size >= 3)
    }

    fun `test history disabled`() {
        resetHistorySettings()

        val trigger1 = randomTrigger(condition = ALWAYS_RUN)
        val monitor1 = createMonitor(randomMonitor(triggers = listOf(trigger1)))
        executeMonitor(monitor1.id)

        // Check if alert is active
        val activeAlert1 = searchAlerts(monitor1)
        assertEquals("1 alert should be active", 1, activeAlert1.size)

        // Change trigger and re-execute monitor to mark alert as COMPLETED
        updateMonitor(monitor1.copy(triggers = listOf(trigger1.copy(condition = NEVER_RUN)), id = monitor1.id), true)
        executeMonitor(monitor1.id)

        val completedAlert1 = searchAlerts(monitor1, AlertIndices.ALL_INDEX_PATTERN).single()
        assertNotNull("Alert is not completed", completedAlert1.endTime)

        assertEquals(1, getHistoryDocCount())

        // Disable alert history
        client().updateSettings(AlertingSettings.ALERT_HISTORY_ENABLED.key, "false")

        val trigger2 = randomTrigger(condition = ALWAYS_RUN)
        val monitor2 = createMonitor(randomMonitor(triggers = listOf(trigger2)))
        executeMonitor(monitor2.id)

        // Check if second alert is active
        val activeAlert2 = searchAlerts(monitor2)
        assertEquals("1 alert should be active", 1, activeAlert2.size)

        // Mark second alert as COMPLETED
        updateMonitor(monitor2.copy(triggers = listOf(trigger2.copy(condition = NEVER_RUN)), id = monitor2.id), true)
        executeMonitor(monitor2.id)

        // For the second alert, since history is now disabled, searching for the completed alert should return an empty List
        // since a COMPLETED alert will be removed from the alert index and not added to the history index
        val completedAlert2 = searchAlerts(monitor2, AlertIndices.ALL_INDEX_PATTERN)
        assertTrue("Alert is not completed", completedAlert2.isEmpty())

        // Get history entry count again and ensure the new alert was not added
        assertEquals(1, getHistoryDocCount())
    }

    fun `test short retention period`() {
        resetHistorySettings()

        // Create monitor and execute
        val trigger = randomTrigger(condition = ALWAYS_RUN)
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))
        executeMonitor(monitor.id)

        // Check if alert is active and alert index is created
        val activeAlert = searchAlerts(monitor)
        assertEquals("1 alert should be active", 1, activeAlert.size)
        assertEquals("Did not find 2 alert indices", 2, getAlertIndices().size)
        // History index is created but is empty
        assertEquals(0, getHistoryDocCount())

        // Mark alert as COMPLETED
        updateMonitor(monitor.copy(triggers = listOf(trigger.copy(condition = NEVER_RUN)), id = monitor.id), true)
        executeMonitor(monitor.id)

        // Verify alert is completed
        val completedAlert = searchAlerts(monitor, AlertIndices.ALL_INDEX_PATTERN).single()
        assertNotNull("Alert is not completed", completedAlert.endTime)

        // The completed alert should be removed from the active alert index and added to the history index
        assertEquals(1, getHistoryDocCount())

        // Update rollover check and max docs as well as decreasing the retention period
        client().updateSettings(AlertingSettings.ALERT_HISTORY_ROLLOVER_PERIOD.key, "1s")
        client().updateSettings(AlertingSettings.ALERT_HISTORY_MAX_DOCS.key, 1)
        client().updateSettings(AlertingSettings.ALERT_HISTORY_RETENTION_PERIOD.key, "1s")

        // Give some time for history to be rolled over and cleared
        Thread.sleep(2000)

        // Given the max_docs and retention settings above, the history index will rollover and the non-write index will be deleted.
        // This leaves two indices: alert index and an empty history write index
        assertEquals("Did not find 2 alert indices", 2, getAlertIndices().size)
        assertEquals(0, getHistoryDocCount())
    }

    private fun assertIndexExists(index: String) {
        val response = client().makeRequest("HEAD", index)
        assertEquals("Index $index does not exist.", RestStatus.OK, response.restStatus())
    }

    private fun assertIndexDoesNotExist(index: String) {
        val response = client().makeRequest("HEAD", index)
        assertEquals("Index $index does not exist.", RestStatus.NOT_FOUND, response.restStatus())
    }

    private fun resetHistorySettings() {
        client().updateSettings(AlertingSettings.ALERT_HISTORY_ENABLED.key, "true")
        client().updateSettings(AlertingSettings.ALERT_HISTORY_ROLLOVER_PERIOD.key, "60s")
        client().updateSettings(AlertingSettings.ALERT_HISTORY_RETENTION_PERIOD.key, "60s")
    }

    private fun getAlertIndices(): List<String> {
        val response = client().makeRequest("GET", "/_cat/indices/${AlertIndices.ALL_INDEX_PATTERN}?format=json")
        val xcp = createParser(XContentType.JSON.xContent(), response.entity.content)
        val responseList = xcp.list()
        val indices = mutableListOf<String>()
        responseList.filterIsInstance<Map<String, Any>>().forEach { indices.add(it["index"] as String) }

        return indices
    }

    private fun getHistoryDocCount(): Long {
        val request = """
            {
                "query": {
                    "match_all": {}
                }
            }
        """.trimIndent()
        val response = client().makeRequest("POST", "${AlertIndices.HISTORY_ALL}/_search", emptyMap(),
                StringEntity(request, APPLICATION_JSON))
        assertEquals("Request to get history failed", RestStatus.OK, response.restStatus())
        return SearchResponse.fromXContent(createParser(jsonXContent, response.entity.content)).hits.totalHits!!.value
    }
}
