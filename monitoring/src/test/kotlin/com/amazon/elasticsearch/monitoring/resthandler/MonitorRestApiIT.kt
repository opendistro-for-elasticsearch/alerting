/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.model.CronSchedule
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.MonitoringRestTestCase
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.Trigger
import com.amazon.elasticsearch.monitoring.randomAlert
import com.amazon.elasticsearch.monitoring.randomMonitor
import com.amazon.elasticsearch.monitoring.settings.MonitoringSettings
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.string
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHeader
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.ResponseException
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.junit.annotations.TestLogging
import org.elasticsearch.test.rest.ESRestTestCase
import java.time.ZoneId

@TestLogging("level:DEBUG")
@Suppress("UNCHECKED_CAST")
class MonitorRestApiIT : MonitoringRestTestCase() {

    val USE_TYPED_KEYS = ToXContent.MapParams(mapOf("with_type" to "true"))

    @Throws(Exception::class)
    fun `test plugin is loaded`() {
        val response = entityAsMap(ESRestTestCase.client().performRequest("GET", "_nodes/plugins"))
        val nodesInfo = response["nodes"] as Map<String, Map<String, Any>>
        for (nodeInfo in nodesInfo.values) {
            val plugins = nodeInfo["plugins"] as List<Map<String, Any>>
            for (plugin in plugins) {
                if (plugin["name"] == "aes-monitoring") {
                    return
                }
            }
        }
        fail("Plugin not installed")
    }

    fun `test parsing monitor as a scheduled job`() {
        val monitor = createRandomMonitor()

        val builder = monitor.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), USE_TYPED_KEYS)
        val string = ElasticAPI.INSTANCE.builderToBytesRef(builder).utf8ToString()
        val xcp = createParser(XContentType.JSON.xContent(), string)
        val scheduledJob = ScheduledJob.parse(xcp, monitor.id, monitor.version)
        assertEquals(monitor, scheduledJob)
    }

    @Throws(Exception::class)
    fun `test creating a monitor`() {
        val monitor = randomMonitor()

        val createResponse = client().performRequest("POST", "_awses/monitors", emptyMap(), monitor.toHttpEntity())

        assertEquals("Create monitor failed", RestStatus.CREATED, createResponse.restStatus())
        val responseBody = createResponse.asMap()
        val createdId = responseBody["_id"] as String
        val createdVersion = responseBody["_version"] as Int
        assertNotEquals("response is missing Id", Monitor.NO_ID, createdId)
        assertTrue("incorrect version", createdVersion > 0)
        assertEquals("Incorrect Location header", "/_awses/monitors/$createdId", createResponse.getHeader("Location"))
    }

    fun `test creating a monitor with PUT fails`() {
        try {
            val monitor = randomMonitor()
            client().performRequest("PUT", "_awses/monitors", emptyMap(), monitor.toHttpEntity())
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
        val updateResponse = client().performRequest("PUT", monitor.relativeUrl(),
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
        val updateResponse = client().performRequest("PUT", monitor.relativeUrl(),
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
        val updateResponse = client().performRequest("PUT", monitor.relativeUrl(),
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
        } catch (e : ResponseException) {
            assertEquals(RestStatus.NOT_FOUND, e.response.restStatus())
        }
    }

    @Throws(Exception::class)
    fun `test checking if a monitor exists`() {
        val monitor = createRandomMonitor()

        val headResponse = client().performRequest("HEAD", monitor.relativeUrl())
        assertEquals("Unable to HEAD monitor", RestStatus.OK, headResponse.restStatus())
        assertNull("Response contains unexpected body", headResponse.entity)
    }

    fun `test checking if a non-existent monitor exists`() {
        val headResponse = client().performRequest("HEAD", "_awses/monitors/foobarbaz")
        assertEquals("Unexpected status", RestStatus.NOT_FOUND, headResponse.restStatus())
    }

    @Throws(Exception::class)
    fun `test deleting a monitor`() {
        val monitor = createRandomMonitor()

        val deleteResponse = client().performRequest("DELETE", monitor.relativeUrl())
        assertEquals("Delete failed", RestStatus.OK, deleteResponse.restStatus())

        val getResponse = client().performRequest("HEAD", monitor.relativeUrl())
        assertEquals("Deleted monitor still exists", RestStatus.NOT_FOUND, getResponse.restStatus())
    }

    @Throws(Exception::class)
    fun `test deleting a monitor that doesn't exist`() {
        try {
            client().performRequest("DELETE", "_awses/monitors/foobarbaz")
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
        val searchResponse = client().performRequest("GET", "/_awses/monitors/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Any>
        val numberDocsFound = hits["total"]
        assertEquals("Monitor not found during search", 1, numberDocsFound)
    }

    fun `test query a monitor that exists POST`() {
        val monitor = createRandomMonitor(true)

        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()
        val searchResponse = client().performRequest("POST", "/_awses/monitors/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Any>
        val numberDocsFound = hits["total"]
        assertEquals("Monitor not found during search", 1, numberDocsFound)
    }

    fun `test query a monitor that doesn't exist`() {
        createRandomMonitor(refresh = true) // Create a random monitor to create the ScheduledJob index. Otherwise we test will fail with 404 index not found.
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery(ESTestCase.randomAlphaOfLength(5),
                ESTestCase.randomAlphaOfLength(5))).toString()

        val searchResponse = client().performRequest("GET", "/_awses/monitors/_search", emptyMap(), NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Any>
        val numberDocsFound = hits["total"]
        assertEquals("Monitor found during search when no document present.", 0, numberDocsFound)
    }

    fun `test query a monitor with UI metadata from Kibana`() {
        val monitor = createRandomMonitor(refresh = true, withMetadata = true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()
        val header = BasicHeader(HttpHeaders.USER_AGENT, "Kibana")
        val searchResponse = client().performRequest("GET", "/_awses/monitors/_search", emptyMap(), NStringEntity(search, ContentType.APPLICATION_JSON), header)
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())

        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"] as Map<String, Any>
        val numberDocsFound = hits["total"]
        assertEquals("Monitor not found during search", 1, numberDocsFound)

        val searchHits = hits["hits"] as List<Any>
        val hit = searchHits[0] as Map<String, Any>
        val monitorHit = hit["_source"] as Map<String, Any>
        assertNotNull("UI Metadata returned from search but request did not come from Kibana", monitorHit[Monitor.UI_METADATA_FIELD])
    }

    fun `test query a monitor with UI metadata as user`() {
        val monitor = createRandomMonitor(refresh = true, withMetadata = true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()
        val searchResponse = client().performRequest("GET", "/_awses/monitors/_search", emptyMap(), NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, searchResponse.restStatus())

        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"] as Map<String, Any>
        val numberDocsFound = hits["total"]
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

        val failedResponseList = responseMap.get("failed").toString()
        assertTrue("Alert in state ${acknowledgedAlert.state} not found in failed list", failedResponseList.contains(acknowledgedAlert.id))
        assertTrue("Alert in state ${completedAlert.state} not found in failed list", failedResponseList.contains(errorAlert.id))
        assertTrue("Alert in state ${errorAlert.state} not found in failed list", failedResponseList.contains(completedAlert.id))
        assertTrue("Invalid alert not found in failed list", failedResponseList.contains(invalidAlert.id))
        assertFalse("Alert in state ${activeAlert.state} found in failed list", failedResponseList.contains(activeAlert.id))
    }

    fun `test mappings after monitor creation`() {
        createRandomMonitor(refresh = true)

        val response = client().performRequest("GET", "/${ScheduledJob.SCHEDULED_JOBS_INDEX}/_mapping/_doc")
        val parserMap = createParser(XContentType.JSON.xContent(), response.entity.content).map() as Map<String, Map<String, Any>>
        val mappingsMap = parserMap[ScheduledJob.SCHEDULED_JOBS_INDEX]!!["mappings"] as Map<String, Any>
        val expected = createParser(XContentType.JSON.xContent(), javaClass.classLoader.getResource("mappings/scheduled-jobs.json").readText())
        val expectedMap = expected.map()

        assertEquals("Mappings are different", expectedMap, mappingsMap)
    }

    fun `test delete monitor and alerts`() {
        putAlertMappings()
        val monitor = createRandomMonitor(true)
        val alert = createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE))

        val deleteResponse = client().performRequest("DELETE", "_awses/monitors/${monitor.id}")
        assertEquals("Delete request not successful", RestStatus.OK, deleteResponse.restStatus())

        val alerts = searchAlerts(monitor)
        assertEquals("Active alert was not deleted", 0, alerts.size)

        val historyAlerts = searchAlerts(monitor, AlertIndices.HISTORY_WRITE_INDEX)
        assertEquals("Alert was not moved to history", 1, historyAlerts.size)
        assertEquals("Alert data incorrect", alert.copy(state = Alert.State.DELETED), historyAlerts.single())
    }

    fun `test update monitor with wrong version`() {
        val monitor = createRandomMonitor(refresh = true)
        try {
            client().performRequest("PUT", "${monitor.relativeUrl()}?refresh=true&version=1234",
                    emptyMap(), monitor.toHttpEntity())
            fail("expected 409 ResponseException")
        } catch (e: ResponseException) {
            assertEquals(RestStatus.CONFLICT, e.response.restStatus())
        }
    }

    fun `test disable monitoring`() {
        val aesSettings = client().getClusterSettings(mapOf("include_defaults" to "true")).aesSettings()
        assertEquals("Monitoring not enabled", "true", aesSettings?.get("enabled"))

        val updateResponse = client().performRequest("POST", "_awses/monitors/settings",
                emptyMap(),
                StringEntity(jsonBuilder().startObject().field(MonitoringSettings.MONITORING_ENABLED.key, false).endObject().string(), ContentType.APPLICATION_JSON))
        val responseMap = updateResponse.asMap().stringMap("persistent")?.stringMap("aes")?.stringMap("monitoring")
        assertEquals("Setting was not updated in response call.", "false", responseMap?.get("enabled"))

        val newSettings = client().getClusterSettings(mapOf("include_defaults" to "true")).stringMap("persistent")?.stringMap("aes")?.stringMap("monitoring")
        assertEquals("Setting was not updated.", "false", newSettings?.get("enabled"))
    }

    @Throws(Exception::class)
    fun `test disable monitoring with non boolean`() {
        val currentSettings = client().getClusterSettings(mapOf("include_defaults" to "true")).aesSettings()
        assertEquals("Monitoring not enabled", "true", currentSettings?.get("enabled"))

        try {
            val updateResponse = client().performRequest("POST", "_awses/monitors/settings",
                    emptyMap(),
                    StringEntity(jsonBuilder().startObject().field(MonitoringSettings.MONITORING_ENABLED.key, "foobarbaz").endObject().string(), ContentType.APPLICATION_JSON))
            fail("Settings request should have failed.")
        } catch (e: ResponseException) {
            assertEquals("Failed", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }

    @Throws(Exception::class)
    fun `test disable monitoring with wrong key`() {
        val currentSettings = client().getClusterSettings(mapOf("include_defaults" to "true")).aesSettings()
        assertEquals("Monitoring not enabled", "true", currentSettings?.get("enabled"))

        try {
            val updateResponse = client().performRequest("POST", "_awses/monitors/settings",
                    emptyMap(),
                    StringEntity(jsonBuilder().startObject().field("foo.bar", "baz").endObject().string(), ContentType.APPLICATION_JSON))
            fail("Settings request should have failed.")
        } catch (e: ResponseException) {
            assertEquals("Failed", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }

    fun `test monitor stats no jobs`() {
        val monitorStatsResponse = client().performRequest("GET", "/_awses/_monitors/stats")
        var responseMap = createParser(XContentType.JSON.xContent(), monitorStatsResponse.entity.content).map()
        assertEquals("Cluster name is incorrect", responseMap["cluster_name"], "monitoring_integTestCluster")
        assertEquals("Scheduled job index exists but there are no scheduled jobs.",false, responseMap["scheduled_job_index_exists"])
        val _nodes = responseMap["_nodes"] as Map<String, Int>
        assertEquals("Incorrect number of nodes", 1, _nodes["total"])
        assertEquals("Failed nodes found during monitor stats call", 0, _nodes["failed"])
        assertEquals("More than one successful node", 1, _nodes["successful"])
    }

    fun `test monitor stats jobs`() {
        val monitor = createRandomMonitor(refresh = true)

        val monitorStatsResponse = client().performRequest("GET", "/_awses/_monitors/stats")
        var responseMap = createParser(XContentType.JSON.xContent(), monitorStatsResponse.entity.content).map()
        assertEquals("Cluster name is incorrect", responseMap["cluster_name"], "monitoring_integTestCluster")
        assertEquals("Scheduled job index does not exist", true, responseMap["scheduled_job_index_exists"])
        assertEquals("Scheduled job index is not yellow", responseMap["scheduled_job_index_status"], "yellow")
        assertEquals("Node is not on schedule", responseMap["nodes_on_schedule"], 1)

        val _nodes = responseMap["_nodes"] as Map<String, Int>
        assertEquals("Incorrect number of nodes", _nodes["total"], 1)
        assertEquals("Failed nodes found during monitor stats call", _nodes["failed"], 0)
        assertEquals("More than one successful node", _nodes["successful"], 1)

        val nodes = responseMap["nodes"] as Map<String, Map<String, Any>>
    }
}
