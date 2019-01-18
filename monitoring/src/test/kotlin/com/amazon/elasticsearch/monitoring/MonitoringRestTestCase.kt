/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.model.action.SNSAction
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.TestAction
import com.amazon.elasticsearch.test.makeRequest
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.string
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHeader
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.common.xcontent.json.JsonXContent
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.SearchModule
import org.elasticsearch.test.rest.ESRestTestCase
import org.junit.rules.DisableOnDebug
import java.net.URLEncoder
import java.util.Locale

abstract class MonitoringRestTestCase : ESRestTestCase() {

    private val isDebuggingTest = DisableOnDebug(null).isDebugging
    private val isDebuggingRemoteCluster = System.getProperty("cluster.debug", "false")!!.toBoolean()

    override fun xContentRegistry(): NamedXContentRegistry {
        return NamedXContentRegistry(mutableListOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY,
                SNSAction.XCONTENT_REGISTRY, TestAction.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }

    fun Response.asMap() : Map<String, Any> {
        return entityAsMap(this)
    }

    protected fun createMonitor(monitor: Monitor, refresh: Boolean = true) : Monitor {

        val response = client().makeRequest("POST", "/_awses/monitors?refresh=$refresh", emptyMap(),
                monitor.toHttpEntity())
        assertEquals("Unable to create a new monitor", RestStatus.CREATED, response.restStatus())

        val monitorJson = ElasticAPI.INSTANCE.jsonParser(NamedXContentRegistry.EMPTY, response.entity.content).map()
        return monitor.copy(id = monitorJson["_id"] as String, version = (monitorJson["_version"] as Int).toLong())
    }

    protected fun createAlert(alert: Alert): Alert {
        val response = client().makeRequest("POST", "/.aes-alerts/_doc?refresh=true&routing=${alert.monitorId}",
                emptyMap(), alert.toHttpEntity())
        assertEquals("Unable to create a new alert", RestStatus.CREATED, response.restStatus())

        val alertJson = ElasticAPI.INSTANCE.jsonParser(NamedXContentRegistry.EMPTY, response.entity.content).map()
        return alert.copy(id = alertJson["_id"] as String, version = (alertJson["_version"] as Int).toLong())
    }

    protected fun createRandomMonitor(refresh: Boolean = false, withMetadata: Boolean = false) : Monitor {
        val monitor = randomMonitor(withMetadata = withMetadata)
        val monitorId = createMonitor(monitor, refresh).id
        if (withMetadata) {
            return getMonitor(monitorId = monitorId, header = BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
        }
        return getMonitor(monitorId = monitorId)
    }

    protected fun updateMonitor(monitor: Monitor, refresh: Boolean = false): Monitor {
        val response = client().makeRequest("PUT", "${monitor.relativeUrl()}?refresh=$refresh",
                emptyMap(), monitor.toHttpEntity())
        assertEquals("Unable to update a monitor", RestStatus.OK, response.restStatus())
        return getMonitor(monitorId = monitor.id)
    }

    protected fun getMonitor(monitorId: String, header: BasicHeader = BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json")): Monitor {
        val response = client().makeRequest("GET", "_awses/monitors/$monitorId", null, header)
        assertEquals("Unable to get monitor $monitorId", RestStatus.OK, response.restStatus())

        val parser = createParser(XContentType.JSON.xContent(), response.entity.content)
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser::getTokenLocation)

        lateinit var id : String
        var version : Long = 0
        lateinit var monitor : Monitor

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            parser.nextToken()

            when (parser.currentName()) {
                "_id" ->      id = parser.text()
                "_version" ->  version = parser.longValue()
                "monitor" ->  monitor = Monitor.parse(parser)
            }
        }
        return monitor.copy(id  = id, version = version)
    }

    protected fun searchAlerts(monitor: Monitor, indices: String = AlertIndices.ALERT_INDEX, refresh: Boolean = true) : List<Alert> {
        if (refresh) refreshIndex(indices)

        // If this is a test monitor (it doesn't have an ID) and no alerts will be saved for it.
        val searchParams = if (monitor.id != Monitor.NO_ID) mapOf("routing" to monitor.id) else mapOf()
        val request = """
                { "version" : true,
                  "query" : { "term" : { "${Alert.MONITOR_ID_FIELD}" : "${monitor.id}" } }
                }
                """.trimIndent()
        val httpResponse = client().makeRequest("GET", "/$indices/_search", searchParams, StringEntity(request, APPLICATION_JSON))
        assertEquals("Search failed", RestStatus.OK, httpResponse.restStatus())

        val searchResponse = SearchResponse.fromXContent(createParser(JsonXContent.jsonXContent, httpResponse.entity.content))
        return searchResponse.hits.hits.map {
            val xcp = createParser(JsonXContent.jsonXContent, it.sourceRef).also { it.nextToken() }
            Alert.parse(xcp, it.id, it.version)
        }
    }

    protected fun acknowledgeAlerts(monitor: Monitor, vararg alerts: Alert) : Response {
        val request = XContentFactory.jsonBuilder().startObject()
                .array("alerts", *alerts.map { it.id }.toTypedArray())
                .endObject()
                .string()
                .let { StringEntity(it, APPLICATION_JSON) }

        val response = client().makeRequest("POST", "${monitor.relativeUrl()}/_acknowledge/alerts?refresh=true",
                emptyMap(), request)
        assertEquals("Acknowledge call failed.", RestStatus.OK, response.restStatus())
        return response
    }

    protected fun refreshIndex(index: String): Response {
        val response = client().makeRequest("POST", "/$index/_refresh")
        assertEquals("Unable to refresh index", RestStatus.OK, response.restStatus())
        return response
    }

    protected fun deleteIndex(index: String) : Response {
        val response = adminClient().makeRequest("DELETE", "/$index")
        assertEquals("Unable to delete index", RestStatus.OK, response.restStatus())
        return response
    }

    protected fun executeMonitor(monitorId: String, params: Map<String, String> = mapOf()) : Response =
            client().makeRequest("POST", "/_awses/monitors/$monitorId/_execute", params)

    protected fun executeMonitor(monitor: Monitor, params: Map<String, String> = mapOf()) : Response =
            client().makeRequest("POST", "/_awses/monitors/_execute", params, monitor.toHttpEntity())

    protected fun indexDoc(index: String, id: String, doc: String, refresh: Boolean = true) : Response {
        val requestBody = StringEntity(doc, APPLICATION_JSON)
        val params = if (refresh) mapOf("refresh" to "true") else mapOf()
        val response = client().makeRequest("PUT", "$index/_doc/$id", params, requestBody)
        assertTrue("Unable to index doc: '${doc.take(15)}...' to index: '$index'",
                listOf(RestStatus.OK, RestStatus.CREATED).contains(response.restStatus()))
        return response
    }

    /** A test index that can be used across tests. Feel free to add new fields but don't remove any. */
    protected fun createTestIndex(index: String = randomAlphaOfLength(10).toLowerCase(Locale.ROOT)) : String {
        createIndex(index, Settings.EMPTY, """
            "_doc" : {
              "properties" : {
                 "test_strict_date_time" : { "type" : "date", "format" : "strict_date_time" }
              }
            }
        """.trimIndent())
        return index
    }

    fun putAlertMappings() {
        val mappingHack = AlertIndices.alertMapping().trimStart('{').trimEnd('}')
        val encodedHistoryIndex = URLEncoder.encode(AlertIndices.HISTORY_INDEX_PATTERN, Charsets.UTF_8.toString())
        createIndex(AlertIndices.ALERT_INDEX, Settings.EMPTY, mappingHack)
        createIndex(encodedHistoryIndex, Settings.EMPTY, mappingHack)
        client().makeRequest("PUT", "/$encodedHistoryIndex/_alias/${AlertIndices.HISTORY_WRITE_INDEX}")
    }

    protected fun Response.restStatus() : RestStatus {
        return RestStatus.fromCode(this.statusLine.statusCode)
    }

    protected fun Monitor.toHttpEntity() : HttpEntity {
        return StringEntity(toJsonString(), APPLICATION_JSON)
    }

    private fun Monitor.toJsonString(): String {
        val builder = XContentFactory.jsonBuilder()
        return shuffleXContent(toXContent(builder)).string()
    }

    private fun Alert.toHttpEntity(): HttpEntity {
        return StringEntity(toJsonString(), APPLICATION_JSON)
    }

    private fun Alert.toJsonString(): String {
        val builder = XContentFactory.jsonBuilder()
        return shuffleXContent(toXContent(builder, ToXContent.EMPTY_PARAMS)).string()
    }

    protected fun Monitor.relativeUrl() = "_awses/monitors/$id"

    // Useful settings when debugging to prevent timeouts
    override fun restClientSettings(): Settings {
        return if (isDebuggingTest || isDebuggingRemoteCluster) {
            Settings.builder()
                    .put(CLIENT_RETRY_TIMEOUT, TimeValue.timeValueMinutes(10))
                    .put(CLIENT_SOCKET_TIMEOUT, TimeValue.timeValueMinutes(10))
                    .build()
        } else {
            super.restClientSettings()
        }
    }

    fun RestClient.getClusterSettings(settings: Map<String, String>) : Map<String, Any> {
        val response =  this.makeRequest("GET", "_cluster/settings", settings)
        assertEquals(RestStatus.OK, response.restStatus())
        return response.asMap()
    }

    @Suppress("UNCHECKED_CAST")
    fun Map<String, Any>.aesSettings(): Map<String, Any>? {
        val map = this as Map<String, Map<String, Map<String, Map<String, Any>>>>
        return map["defaults"]?.get("aes")?.get("monitoring")
    }

    @Suppress("UNCHECKED_CAST")
    fun Map<String, Any>.stringMap(key: String): Map<String, Any>? {
        val map = this as Map<String, Map<String, Any>>
        return map[key]
    }
}