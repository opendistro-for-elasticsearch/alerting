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
package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.alerting.randomAlert
import com.amazon.opendistroforelasticsearch.alerting.randomMonitor
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.ALERTING_BASE_URI
import com.amazon.opendistroforelasticsearch.alerting.randomTrigger
import com.amazon.opendistroforelasticsearch.alerting.core.model.CronSchedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings
import com.amazon.opendistroforelasticsearch.alerting.makeRequest
import com.amazon.opendistroforelasticsearch.alerting.randomAction
import com.amazon.opendistroforelasticsearch.alerting.randomThrottle
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.apache.http.message.BasicHeader
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.ResponseException
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.junit.annotations.TestLogging
import org.elasticsearch.test.rest.ESRestTestCase
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.elasticsearch.common.unit.TimeValue

@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class MonitorRestApiIT : AlertingRestTestCase() {

    val USE_TYPED_KEYS = ToXContent.MapParams(mapOf("with_type" to "true"))

    @Throws(Exception::class)
    fun `test plugin is loaded`() {
        val response = entityAsMap(ESRestTestCase.client().makeRequest("GET", "_nodes/plugins"))
        val nodesInfo = response["nodes"] as Map<String, Map<String, Any>>
        for (nodeInfo in nodesInfo.values) {
            val plugins = nodeInfo["plugins"] as List<Map<String, Any>>
            for (plugin in plugins) {
                if (plugin["name"] == "opendistro_alerting") {
                    return
                }
            }
        }
        fail("Plugin not installed")
    }

    fun `test parsing monitor as a scheduled job`() {
        val monitor = createRandomMonitor()

        val builder = monitor.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), USE_TYPED_KEYS)
        val string = BytesReference.bytes(builder).utf8ToString()
        val xcp = createParser(XContentType.JSON.xContent(), string)
        val scheduledJob = ScheduledJob.parse(xcp, monitor.id, monitor.version)
        assertEquals(monitor, scheduledJob)
    }

    @Throws(Exception::class)
    fun `test creating a monitor`() {
        val monitor = randomMonitor()

        val createResponse = client().makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())

        assertEquals("Create monitor failed", RestStatus.CREATED, createResponse.restStatus())
        val responseBody = createResponse.asMap()
        val createdId = responseBody["_id"] as String
        val createdVersion = responseBody["_version"] as Int
        assertNotEquals("response is missing Id", Monitor.NO_ID, createdId)
        assertTrue("incorrect version", createdVersion > 0)
        assertEquals("Incorrect Location header", "$ALERTING_BASE_URI/$createdId", createResponse.getHeader("Location"))
    }

    @Throws(Exception::class)
    fun `test creating a monitor with action threshold greater than max threshold`() {
        val monitor = randomMonitorWithThrottle(100000, ChronoUnit.MINUTES)

        try {
            client().makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }

    @Throws(Exception::class)
    fun `test creating a monitor with action threshold less than min threshold`() {
        val monitor = randomMonitorWithThrottle(-1)

        try {
            client().makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }

    @Throws(Exception::class)
    fun `test creating a monitor with updating action threshold`() {
        adminClient().updateSettings("opendistro.alerting.action_throttle_max_value", TimeValue.timeValueHours(1))

        val monitor = randomMonitorWithThrottle(2, ChronoUnit.HOURS)

        try {
            client().makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
        adminClient().updateSettings("opendistro.alerting.action_throttle_max_value", TimeValue.timeValueHours(24))
    }

    fun `test creating a monitor with PUT fails`() {
        try {
            val monitor = randomMonitor()
            client().makeRequest("PUT", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
            fail("Expected 405 Method Not Allowed response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.METHOD_NOT_ALLOWED, e.response.restStatus())
        }
    }

    @Throws(Exception::class)
    fun `test updating search for a monitor`() {
        val monitor = createRandomMonitor()

        val updatedSearch = SearchInput(emptyList(),
                SearchSourceBuilder().query(QueryBuilders.termQuery("foo", "bar")))
        val updateResponse = client().makeRequest("PUT", monitor.relativeUrl(),
                emptyMap(), monitor.copy(inputs = listOf(updatedSearch)).toHttpEntity())

        assertEquals("Update monitor failed", RestStatus.OK, updateResponse.restStatus())
        val responseBody = updateResponse.asMap()
        assertEquals("Updated monitor id doesn't match", monitor.id, responseBody["_id"] as String)
        assertEquals("Version not incremented", (monitor.version + 1).toInt(), responseBody["_version"] as Int)

        val updatedMonitor = getMonitor(monitor.id)
        assertEquals("Monitor search not updated", listOf(updatedSearch), updatedMonitor.inputs)
    }

    @Throws(Exception::class)
    fun `test updating conditions for a monitor`() {
        val monitor = createRandomMonitor()

        val updatedTriggers = listOf(Trigger("foo", "1", Script("return true"), emptyList()))
        val updateResponse = client().makeRequest("PUT", monitor.relativeUrl(),
                emptyMap(), monitor.copy(triggers = updatedTriggers).toHttpEntity())

        assertEquals("Update monitor failed", RestStatus.OK, updateResponse.restStatus())
        val responseBody = updateResponse.asMap()
        assertEquals("Updated monitor id doesn't match", monitor.id, responseBody["_id"] as String)
        assertEquals("Version not incremented", (monitor.version + 1).toInt(), responseBody["_version"] as Int)

        val updatedMonitor = getMonitor(monitor.id)
        assertEquals("Monitor trigger not updated", updatedTriggers, updatedMonitor.triggers)
    }

    @Throws(Exception::class)
    fun `test updating schedule for a monitor`() {
        val monitor = createRandomMonitor()

        val updatedSchedule = CronSchedule(expression = "0 9 * * *", timezone = ZoneId.of("UTC"))
        val updateResponse = client().makeRequest("PUT", monitor.relativeUrl(),
                emptyMap(), monitor.copy(schedule = updatedSchedule).toHttpEntity())

        assertEquals("Update monitor failed", RestStatus.OK, updateResponse.restStatus())
        val responseBody = updateResponse.asMap()
        assertEquals("Updated monitor id doesn't match", monitor.id, responseBody["_id"] as String)
        assertEquals("Version not incremented", (monitor.version + 1).toInt(), responseBody["_version"] as Int)

        val updatedMonitor = getMonitor(monitor.id)
        assertEquals("Monitor trigger not updated", updatedSchedule, updatedMonitor.schedule)
    }

    @Throws(Exception::class)
    fun `test getting a monitor`() {
        val monitor = createRandomMonitor()

        val storedMonitor = getMonitor(monitor.id)

        assertEquals("Indexed and retrieved monitor differ", monitor, storedMonitor)
    }

    @Throws(Exception::class)
    fun `test getting a monitor that doesn't exist`() {
        try {
            getMonitor(randomAlphaOfLength(20))
            fail("expected response exception")
        } catch (e: ResponseException) {
            assertEquals(RestStatus.NOT_FOUND, e.response.restStatus())
        }
    }

    @Throws(Exception::class)
    fun `test checking if a monitor exists`() {
        val monitor = createRandomMonitor()

        val headResponse = client().makeRequest("HEAD", monitor.relativeUrl())
        assertEquals("Unable to HEAD monitor", RestStatus.OK, headResponse.restStatus())
        assertNull("Response contains unexpected body", headResponse.entity)
    }

    fun `test checking if a non-existent monitor exists`() {
        val headResponse = client().makeRequest("HEAD", "$ALERTING_BASE_URI/foobarbaz")
        assertEquals("Unexpected status", RestStatus.NOT_FOUND, headResponse.restStatus())
    }

    @Throws(Exception::class)
    fun `test deleting a monitor`() {
        val monitor = createRandomMonitor()

        val deleteResponse = client().makeRequest("DELETE", monitor.relativeUrl())
        assertEquals("Delete failed", RestStatus.OK, deleteResponse.restStatus())

        val getResponse = client().makeRequest("HEAD", monitor.relativeUrl())
        assertEquals("Deleted monitor still exists", RestStatus.NOT_FOUND, getResponse.restStatus())
    }

    @Throws(Exception::class)
    fun `test deleting a monitor that doesn't exist`() {
        try {
            client().makeRequest("DELETE", "$ALERTING_BASE_URI/foobarbaz")
            fail("expected 404 ResponseException")
        } catch (e: ResponseException) {
            assertEquals(RestStatus.NOT_FOUND, e.response.restStatus())
        }
    }

    fun `test getting UI metadata monitor not from Kibana`() {
        val monitor = createRandomMonitor(withMetadata = true)
        val getMonitor = getMonitor(monitorId = monitor.id)
        assertEquals("UI Metadata returned but request did not come from Kibana.", getMonitor.uiMetadata, mapOf<String, Any>())
    }

    fun `test getting UI metadata monitor from Kibana`() {
        val monitor = createRandomMonitor(refresh = true, withMetadata = true)
        val header = BasicHeader(HttpHeaders.USER_AGENT, "Kibana")
        val getMonitor = getMonitor(monitorId = monitor.id, header = header)
        assertEquals("", monitor.uiMetadata, getMonitor.uiMetadata)
    }

    fun `test query a monitor that exists`() {
        val monitor = createRandomMonitor(true)

        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()
        val searchResponse = client().makeRequest("GET", "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Map<String, Any>>
        val numberDocsFound = hits["total"]?.get("value")
        assertEquals("Monitor not found during search", 1, numberDocsFound)
    }

    fun `test query a monitor that exists POST`() {
        val monitor = createRandomMonitor(true)

        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()
        val searchResponse = client().makeRequest("POST", "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Map<String, Any>>
        val numberDocsFound = hits["total"]?.get("value")
        assertEquals("Monitor not found during search", 1, numberDocsFound)
    }

    fun `test query a monitor that doesn't exist`() {
        // Create a random monitor to create the ScheduledJob index. Otherwise we test will fail with 404 index not found.
        createRandomMonitor(refresh = true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery(ESTestCase.randomAlphaOfLength(5),
                ESTestCase.randomAlphaOfLength(5))).toString()

        val searchResponse = client().makeRequest(
                "GET",
                "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Map<String, Any>>
        val numberDocsFound = hits["total"]?.get("value")
        assertEquals("Monitor found during search when no document present.", 0, numberDocsFound)
    }

    fun `test query a monitor with UI metadata from Kibana`() {
        val monitor = createRandomMonitor(refresh = true, withMetadata = true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()
        val header = BasicHeader(HttpHeaders.USER_AGENT, "Kibana")
        val searchResponse = client().makeRequest(
                "GET",
                "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON),
                header)
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())

        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"] as Map<String, Map<String, Any>>
        val numberDocsFound = hits["total"]?.get("value")
        assertEquals("Monitor not found during search", 1, numberDocsFound)

        val searchHits = hits["hits"] as List<Any>
        val hit = searchHits[0] as Map<String, Any>
        val monitorHit = hit["_source"] as Map<String, Any>
        assertNotNull("UI Metadata returned from search but request did not come from Kibana", monitorHit[Monitor.UI_METADATA_FIELD])
    }

    fun `test query a monitor with UI metadata as user`() {
        val monitor = createRandomMonitor(refresh = true, withMetadata = true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()
        val searchResponse = client().makeRequest(
                "GET",
                "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())

        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"] as Map<String, Map<String, Any>>
        val numberDocsFound = hits["total"]?.get("value")
        assertEquals("Monitor not found during search", 1, numberDocsFound)

        val searchHits = hits["hits"] as List<Any>
        val hit = searchHits[0] as Map<String, Any>
        val monitorHit = hit["_source"] as Map<String, Any>
        assertNull("UI Metadata returned from search but request did not come from Kibana", monitorHit[Monitor.UI_METADATA_FIELD])
    }

    fun `test acknowledge all alert states`() {
        putAlertMappings() // Required as we do not have a create alert API.
        val monitor = createRandomMonitor(refresh = true)
        val acknowledgedAlert = createAlert(randomAlert(monitor).copy(state = Alert.State.ACKNOWLEDGED))
        val completedAlert = createAlert(randomAlert(monitor).copy(state = Alert.State.COMPLETED))
        val errorAlert = createAlert(randomAlert(monitor).copy(state = Alert.State.ERROR))
        val activeAlert = createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE))
        val invalidAlert = randomAlert(monitor).copy(id = "foobar")

        val response = acknowledgeAlerts(monitor, acknowledgedAlert, completedAlert, errorAlert, activeAlert, invalidAlert)
        val responseMap = response.asMap()

        val activeAlertAcknowledged = searchAlerts(monitor).single { it.id == activeAlert.id }
        assertNotNull("Unsuccessful acknowledgement", responseMap["success"] as List<String>)
        assertTrue("Alert not in acknowledged response", responseMap["success"].toString().contains(activeAlert.id))
        assertEquals("Alert not acknowledged.", Alert.State.ACKNOWLEDGED, activeAlertAcknowledged.state)
        assertNotNull("Alert acknowledged time is NULL", activeAlertAcknowledged.acknowledgedTime)

        val failedResponseList = responseMap["failed"].toString()
        assertTrue("Alert in state ${acknowledgedAlert.state} not found in failed list", failedResponseList.contains(acknowledgedAlert.id))
        assertTrue("Alert in state ${completedAlert.state} not found in failed list", failedResponseList.contains(errorAlert.id))
        assertTrue("Alert in state ${errorAlert.state} not found in failed list", failedResponseList.contains(completedAlert.id))
        assertTrue("Invalid alert not found in failed list", failedResponseList.contains(invalidAlert.id))
        assertFalse("Alert in state ${activeAlert.state} found in failed list", failedResponseList.contains(activeAlert.id))
    }

    fun `test mappings after monitor creation`() {
        createRandomMonitor(refresh = true)

        val response = client().makeRequest("GET", "/${ScheduledJob.SCHEDULED_JOBS_INDEX}/_mapping")
        val parserMap = createParser(XContentType.JSON.xContent(), response.entity.content).map() as Map<String, Map<String, Any>>
        val mappingsMap = parserMap[ScheduledJob.SCHEDULED_JOBS_INDEX]!!["mappings"] as Map<String, Any>
        val expected = createParser(
                XContentType.JSON.xContent(),
                javaClass.classLoader.getResource("mappings/scheduled-jobs.json").readText())
        val expectedMap = expected.map()

        assertEquals("Mappings are different", expectedMap, mappingsMap)
    }

    fun `test delete monitor moves alerts`() {
        client().updateSettings(ScheduledJobSettings.SWEEPER_ENABLED.key, true)
        putAlertMappings()
        val monitor = createRandomMonitor(true)
        val alert = createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE))
        refreshIndex("*")
        val deleteResponse = client().makeRequest("DELETE", "$ALERTING_BASE_URI/${monitor.id}")
        assertEquals("Delete request not successful", RestStatus.OK, deleteResponse.restStatus())

        // Wait 5 seconds for event to be processed and alerts moved
        Thread.sleep(5000)

        val alerts = searchAlerts(monitor)
        assertEquals("Active alert was not deleted", 0, alerts.size)

        val historyAlerts = searchAlerts(monitor, AlertIndices.HISTORY_WRITE_INDEX)
        assertEquals("Alert was not moved to history", 1, historyAlerts.size)
        assertEquals("Alert data incorrect", alert.copy(state = Alert.State.DELETED), historyAlerts.single())
    }

    fun `test delete trigger moves alerts`() {
        client().updateSettings(ScheduledJobSettings.SWEEPER_ENABLED.key, true)
        putAlertMappings()
        val trigger = randomTrigger()
        val monitor = createMonitor(randomMonitor(triggers = listOf(trigger)))
        val alert = createAlert(randomAlert(monitor).copy(triggerId = trigger.id, state = Alert.State.ACTIVE))
        refreshIndex("*")
        val updatedMonitor = monitor.copy(triggers = emptyList())
        val updateResponse = client().makeRequest("PUT", "$ALERTING_BASE_URI/${monitor.id}", emptyMap(),
                updatedMonitor.toHttpEntity())
        assertEquals("Update request not successful", RestStatus.OK, updateResponse.restStatus())

        // Wait 5 seconds for event to be processed and alerts moved
        Thread.sleep(5000)

        val alerts = searchAlerts(monitor)
        assertEquals("Active alert was not deleted", 0, alerts.size)

        val historyAlerts = searchAlerts(monitor, AlertIndices.HISTORY_WRITE_INDEX)
        assertEquals("Alert was not moved to history", 1, historyAlerts.size)
        assertEquals("Alert data incorrect", alert.copy(state = Alert.State.DELETED), historyAlerts.single())
    }

    fun `test delete trigger moves alerts only for deleted trigger`() {
        client().updateSettings(ScheduledJobSettings.SWEEPER_ENABLED.key, true)
        putAlertMappings()
        val triggerToDelete = randomTrigger()
        val triggerToKeep = randomTrigger()
        val monitor = createMonitor(randomMonitor(triggers = listOf(triggerToDelete, triggerToKeep)))
        val alertKeep = createAlert(randomAlert(monitor).copy(triggerId = triggerToKeep.id, state = Alert.State.ACTIVE))
        val alertDelete = createAlert(randomAlert(monitor).copy(triggerId = triggerToDelete.id, state = Alert.State.ACTIVE))
        refreshIndex("*")
        val updatedMonitor = monitor.copy(triggers = listOf(triggerToKeep))
        val updateResponse = client().makeRequest("PUT", "$ALERTING_BASE_URI/${monitor.id}", emptyMap(),
                updatedMonitor.toHttpEntity())
        assertEquals("Update request not successful", RestStatus.OK, updateResponse.restStatus())

        // Wait 5 seconds for event to be processed and alerts moved
        Thread.sleep(5000)

        val alerts = searchAlerts(monitor)
        // We have two alerts from above, 1 for each trigger, there should be only 1 left in active index
        assertEquals("One alert should be in active index", 1, alerts.size)
        assertEquals("Wrong alert in active index", alertKeep, alerts.single())

        val historyAlerts = searchAlerts(monitor, AlertIndices.HISTORY_WRITE_INDEX)
        // Only alertDelete should of been moved to history index
        assertEquals("One alert should be in history index", 1, historyAlerts.size)
        assertEquals("Alert data incorrect", alertDelete.copy(state = Alert.State.DELETED), historyAlerts.single())
    }

    fun `test update monitor with wrong version`() {
        val monitor = createRandomMonitor(refresh = true)
        try {
            client().makeRequest("PUT", "${monitor.relativeUrl()}?refresh=true&if_seq_no=1234&if_primary_term=1234",
                    emptyMap(), monitor.toHttpEntity())
            fail("expected 409 ResponseException")
        } catch (e: ResponseException) {
            assertEquals(RestStatus.CONFLICT, e.response.restStatus())
        }
    }

    fun `test monitor stats disable plugin`() {
        // Disable the Monitor plugin.
        disableScheduledJob()

        val responseMap = getAlertingStats()
        // assertEquals("Cluster name is incorrect", responseMap["cluster_name"], "alerting_integTestCluster")
        assertEquals("Scheduled job is not enabled", false, responseMap[ScheduledJobSettings.SWEEPER_ENABLED.key])
        assertEquals("Scheduled job index exists but there are no scheduled jobs.", false, responseMap["scheduled_job_index_exists"])
        val _nodes = responseMap["_nodes"] as Map<String, Int>
        assertEquals("Incorrect number of nodes", numberOfNodes, _nodes["total"])
        assertEquals("Failed nodes found during monitor stats call", 0, _nodes["failed"])
        assertEquals("More than $numberOfNodes successful node", numberOfNodes, _nodes["successful"])
    }

    fun `test monitor stats no jobs`() {
        // Disable the Monitor plugin.
        enableScheduledJob()

        val responseMap = getAlertingStats()
        // assertEquals("Cluster name is incorrect", responseMap["cluster_name"], "alerting_integTestCluster")
        assertEquals("Scheduled job is not enabled", true, responseMap[ScheduledJobSettings.SWEEPER_ENABLED.key])
        assertEquals("Scheduled job index exists but there are no scheduled jobs.", false, responseMap["scheduled_job_index_exists"])
        val _nodes = responseMap["_nodes"] as Map<String, Int>
        assertEquals("Incorrect number of nodes", numberOfNodes, _nodes["total"])
        assertEquals("Failed nodes found during monitor stats call", 0, _nodes["failed"])
        assertEquals("More than $numberOfNodes successful node", numberOfNodes, _nodes["successful"])
    }

    fun `test monitor stats jobs`() {
        // Enable the Monitor plugin.
        enableScheduledJob()
        createRandomMonitor(refresh = true)

        val responseMap = getAlertingStats()
        // assertEquals("Cluster name is incorrect", responseMap["cluster_name"], "alerting_integTestCluster")
        assertEquals("Scheduled job is not enabled", true, responseMap[ScheduledJobSettings.SWEEPER_ENABLED.key])
        assertEquals("Scheduled job index does not exist", true, responseMap["scheduled_job_index_exists"])
        assertEquals("Scheduled job index is not yellow", "yellow", responseMap["scheduled_job_index_status"])
        assertEquals("Nodes are not on schedule", numberOfNodes, responseMap["nodes_on_schedule"])

        val _nodes = responseMap["_nodes"] as Map<String, Int>
        assertEquals("Incorrect number of nodes", numberOfNodes, _nodes["total"])
        assertEquals("Failed nodes found during monitor stats call", 0, _nodes["failed"])
        assertEquals("More than $numberOfNodes successful node", numberOfNodes, _nodes["successful"])
    }

    @Throws(Exception::class)
    fun `test max number of monitors`() {
        client().updateSettings(AlertingSettings.ALERTING_MAX_MONITORS.key, "1")

        createRandomMonitor(refresh = true)
        try {
            createRandomMonitor(refresh = true)
            fail("Request should be rejected as there are too many monitors.")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }

    fun `test monitor specific metric`() {
        // Enable the Monitor plugin.
        enableScheduledJob()
        createRandomMonitor(refresh = true)

        val responseMap = getAlertingStats("/jobs_info")
        // assertEquals("Cluster name is incorrect", responseMap["cluster_name"], "alerting_integTestCluster")
        assertEquals("Scheduled job is not enabled", true, responseMap[ScheduledJobSettings.SWEEPER_ENABLED.key])
        assertEquals("Scheduled job index does not exist", true, responseMap["scheduled_job_index_exists"])
        assertEquals("Scheduled job index is not yellow", "yellow", responseMap["scheduled_job_index_status"])
        assertEquals("Nodes not on schedule", numberOfNodes, responseMap["nodes_on_schedule"])

        val _nodes = responseMap["_nodes"] as Map<String, Int>
        assertEquals("Incorrect number of nodes", numberOfNodes, _nodes["total"])
        assertEquals("Failed nodes found during monitor stats call", 0, _nodes["failed"])
        assertEquals("More than $numberOfNodes successful node", numberOfNodes, _nodes["successful"])
    }

    fun `test monitor stats incorrect metric`() {
        try {
            getAlertingStats("/foobarzzz")
            fail("Incorrect stats metric should have failed")
        } catch (e: ResponseException) {
            assertEquals("Failed", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }

    fun `test monitor stats _all and other metric`() {
        try {
            getAlertingStats("/_all,jobs_info")
            fail("Incorrect stats metric should have failed")
        } catch (e: ResponseException) {
            assertEquals("Failed", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }

    private fun randomMonitorWithThrottle(value: Int, unit: ChronoUnit = ChronoUnit.MINUTES): Monitor {
        val throttle = randomThrottle(value, unit)
        val action = randomAction().copy(throttle = throttle)
        val trigger = randomTrigger(actions = listOf(action))
        return randomMonitor(triggers = listOf(trigger))
    }
}
