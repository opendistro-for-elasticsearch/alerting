/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler

import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.ScheduledJob.Companion.NO_ID
import com.amazon.elasticsearch.model.ScheduledJob.Companion.NO_VERSION
import com.amazon.elasticsearch.monitor.Monitor
import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.elasticsearch.client.Response
import org.elasticsearch.client.ResponseException
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser.Token.END_OBJECT
import org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.junit.annotations.TestLogging
import org.elasticsearch.test.rest.ESRestTestCase
import org.junit.Before

@TestLogging("level:DEBUG")
class MonitorRestApiTests : ESRestTestCase() {

    @Before
    fun `recreate scheduled jobs index`() {
        // ESRestTestCase wipes all indexes after every test.
        // TODO: This really needs to be part of the plugin to watch for index deletion and recreate as needed.
        createIndex(ScheduledJob.SCHEDULED_JOBS_INDEX, Settings.EMPTY)
    }

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

    @Throws(Exception::class)
    fun `test creating a monitor`() {
        val monitor = randomMonitor()

        val createResponse = client().performRequest("POST", "_awses/monitors", emptyMap(), monitor.toHttpEntity())

        assertEquals("Create monitor failed", RestStatus.CREATED, createResponse.restStatus())
        val responseBody = createResponse.asMap()
        val createdId = responseBody["_id"] as String
        val createdVersion = responseBody["_version"] as Int
        assertNotEquals("response is missing Id", NO_ID, createdId)
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
    fun `test updating a monitor`() {
        val monitor = createRandomMonitor()

        val updatedSearch = randomSearch()
        val updateResponse = client().performRequest("PUT", monitor.relativeUrl(),
                emptyMap(), monitor.copy(search = updatedSearch).toHttpEntity())

        assertEquals("Update monitor failed", RestStatus.OK, updateResponse.restStatus())
        val responseBody = updateResponse.asMap()
        assertEquals("Updated monitor id doesn't match", monitor.id, responseBody["_id"] as String)
        assertEquals("Version not incremented", (monitor.version + 1).toInt(), responseBody["_version"] as Int)

        val updatedMonitor = getMonitor(monitor.id)
        assertEquals("Monitor not updated", updatedSearch, updatedMonitor.search)
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

    // Helper functions

    private fun createRandomMonitor() : Monitor {
        val monitor = randomMonitor()
        val response = client().performRequest("POST", "_awses/monitors", emptyMap(), monitor.toHttpEntity())
        assertEquals("Unable to create a new monitor", RestStatus.CREATED, response.restStatus())

        val monitorJson = XContentType.JSON.xContent()
                .createParser(NamedXContentRegistry.EMPTY, response.entity.content)
                .map()
        return monitor.copy(id = monitorJson["_id"] as String, version = (monitorJson["_version"] as Int).toLong())
    }

    private fun getMonitor(monitorId: String): Monitor {
        val response = client().performRequest("GET", "_awses/monitors/$monitorId")
        assertEquals("Unable to get monitor $monitorId", RestStatus.OK, response.restStatus())

        val parser = XContentType.JSON.xContent()
                .createParser(NamedXContentRegistry.EMPTY, response.entity.content)
        require(parser.nextToken() == START_OBJECT) { "Invalid response" }

        var id : String = NO_ID
        var version : Long = NO_VERSION
        var monitor : Monitor? = null

        while (parser.nextToken() != END_OBJECT) {
            when (parser.currentName()) {
                "_id" ->      { parser.nextToken(); id = parser.text() }
                "_version" -> { parser.nextToken(); version = parser.longValue() }
                "monitor" -> monitor = Monitor.fromJson(parser)
            }
        }
        return monitor!!.copy(id  = id, version = version)
    }

    private fun Response.restStatus() : RestStatus {
        return RestStatus.fromCode(this.statusLine.statusCode)
    }

    private fun Response.asMap() : Map<String, Any> {
        return entityAsMap(this)
    }

    private fun Monitor.toHttpEntity() : HttpEntity {
        return StringEntity(toJsonString(), ContentType.APPLICATION_JSON)
    }

    private fun Monitor.toJsonString(): String {
        val builder = XContentFactory.jsonBuilder()
        return this.toXContent(builder).string()
    }

    private fun Monitor.relativeUrl() = "_awses/monitors/$id"

    private fun randomMonitor(): Monitor {
        return Monitor(name = randomAlphaOfLength(10),
                enabled = ESTestCase.randomBoolean(),
                search = "not implemented",
                schedule = "not implemented",
                triggers = listOf())
    }

    private fun randomSearch(): String {
        return randomAlphaOfLength(20)
        // TODO("Return an actual search string")
    }
}
