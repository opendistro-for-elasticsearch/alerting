/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.model.CronSchedule
import com.amazon.elasticsearch.model.IntervalSchedule
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Trigger
import com.amazon.elasticsearch.monitoring.model.Monitor
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHeader
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.Response
import org.elasticsearch.client.ResponseException
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser.Token.END_OBJECT
import org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.search.SearchModule
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.junit.annotations.TestLogging
import org.elasticsearch.test.rest.ESRestTestCase
import org.junit.Before
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@TestLogging("level:DEBUG")
class MonitorRestApiTests : ESRestTestCase() {

    val USE_TYPED_KEYS = ToXContent.MapParams(mapOf("with_type" to "true"))

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

    override fun xContentRegistry(): NamedXContentRegistry {
        return NamedXContentRegistry(mutableListOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY,
                SNSAction.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }

    fun `test parsing monitor as a scheduled job`() {
        val monitor = randomMonitor()
        createRandomMonitor()

        val builder = monitor.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), USE_TYPED_KEYS)
        val string = builder.string()
        val xcp = createParser(XContentType.JSON.xContent(), string)
        val scheduledJob = ScheduledJob.parse(xcp)
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

        val updatedTriggers = listOf(Trigger("foo", 1, Script("return true"), emptyList()))
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
        var monitor = createRandomMonitor(withMetadata = true)
        var getMonitor = getMonitor(monitorId = monitor.id)
        assertEquals("UI Metadata returned but request did not come from Kibana.", getMonitor.uiMetadata, mapOf<String, Any>())
    }

    fun `test getting UI metadata monitor from Kibana`() {
        var monitor = createRandomMonitor(refresh = true, withMetadata = true)
        var header = BasicHeader(HttpHeaders.USER_AGENT, "Kibana")
        var getMonitor = getMonitor(monitorId = monitor.id, header = header)
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

    fun `test query a monitor that doesn't exist`() {
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

    // Helper functions

    private fun createRandomMonitor(refresh: Boolean = false, withMetadata: Boolean = false) : Monitor {
        val monitor = randomMonitor(withMetadata)
        return createMonitor(monitor, refresh)
    }

    private fun createMonitor(monitor: Monitor, refresh: Boolean = false): Monitor {
        val response = client().performRequest("POST", "/_awses/monitors?refresh=$refresh", emptyMap(), monitor.toHttpEntity())
        assertEquals("Unable to create a new monitor", RestStatus.CREATED, response.restStatus())

        val monitorJson = XContentType.JSON.xContent()
                .createParser(NamedXContentRegistry.EMPTY, response.entity.content)
                .map()
        return monitor.copy(id = monitorJson["_id"] as String, version = (monitorJson["_version"] as Int).toLong())
    }

    private fun getMonitor(monitorId: String, header: BasicHeader = BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json")): Monitor {
        val response = client().performRequest("GET", "_awses/monitors/$monitorId", header)
        assertEquals("Unable to get monitor $monitorId", RestStatus.OK, response.restStatus())


        val parser = createParser(XContentType.JSON.xContent(), response.entity.content)
        XContentParserUtils.ensureExpectedToken(START_OBJECT, parser.nextToken(), parser::getTokenLocation)

        var id : String = ScheduledJob.NO_ID
        var version : Long = ScheduledJob.NO_VERSION
        var monitor : Monitor? = null

        while (parser.nextToken() != END_OBJECT) {
            parser.nextToken()

            when (parser.currentName()) {
                "_id" ->      id = parser.text()
                "_version" ->  version = parser.longValue()
                "monitor" ->  monitor = Monitor.parse(parser)
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

    private fun randomMonitor(withMetadata: Boolean = false): Monitor {
        return Monitor(name = randomAlphaOfLength(10),
                enabled = ESTestCase.randomBoolean(),
                inputs = listOf(SearchInput(emptyList(), SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))),
                schedule = IntervalSchedule(interval = 5, unit = ChronoUnit.MINUTES),
                triggers = listOf(),
                uiMetadata = if (withMetadata) mapOf("foo" to "bar") else mapOf())
    }

    private fun randomSearch(): String {
        return randomAlphaOfLength(20)
        // TODO("Return an actual source string")
    }
}
