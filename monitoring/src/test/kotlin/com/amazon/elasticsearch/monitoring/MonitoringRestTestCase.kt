/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.TestAction
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.string
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHeader
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Response
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
        val response = client().performRequest("POST", "/_awses/monitors?refresh=$refresh", emptyMap(), monitor.toHttpEntity())
        assertEquals("Unable to create a new monitor", RestStatus.CREATED, response.restStatus())

        val monitorJson = ElasticAPI.INSTANCE.jsonParser(NamedXContentRegistry.EMPTY, response.entity.content).map()
        return monitor.copy(id = monitorJson["_id"] as String, version = (monitorJson["_version"] as Int).toLong())
    }

    protected fun createAlert(alert: Alert): Alert {
        val response = client().performRequest("POST", "/.aes-alerts/_doc?refresh=true&routing=${alert.monitorId}", emptyMap(), alert.toHttpEntity())
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
        val response = client().performRequest("PUT", "${monitor.relativeUrl()}?refresh=$refresh",
                emptyMap(), monitor.toHttpEntity())
        assertEquals("Unable to update a monitor", RestStatus.OK, response.restStatus())
        return getMonitor(monitorId = monitor.id)
    }

    protected fun getMonitor(monitorId: String, header: BasicHeader = BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json")): Monitor {
        val response = client().performRequest("GET", "_awses/monitors/$monitorId", header)
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
        val searchParams = if (monitor.id != Monitor.NO_ID) {
            mapOf("routing" to monitor.id, "q" to "monitor_id:${monitor.id}")
        } else {
            mapOf()
        }
        val httpResponse = client().performRequest("GET", "/$indices/_search", searchParams)
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
                .let { StringEntity(it, ContentType.APPLICATION_JSON) }

        val response = client().performRequest("POST", "${monitor.relativeUrl()}/_acknowledge/alerts?refresh=true",
                emptyMap(), request)
        assertEquals("Acknowledge call failed.", RestStatus.OK, response.restStatus())
        return response
    }

    protected fun refreshIndex(index: String): Response {
        val response = client().performRequest("POST", "/$index/_refresh")
        assertEquals("Unable to refresh index", RestStatus.OK, response.restStatus())
        return response
    }

    protected fun executeMonitor(monitorId: String, params: Map<String, String> = mapOf()) : Response =
            client().performRequest("POST", "/_awses/monitors/$monitorId/_execute", params)

    protected fun executeMonitor(monitor: Monitor, params: Map<String, String> = mapOf()) : Response =
            client().performRequest("POST", "/_awses/monitors/_execute", params, monitor.toHttpEntity())

    fun putAlertMappings() {
        client().performRequest("PUT", "/.aes-alerts")
        client().performRequest("PUT", "/.aes-alerts/_mapping/_doc", emptyMap(),
                StringEntity(AlertIndices.alertMapping(), ContentType.APPLICATION_JSON))
    }

    protected fun Response.restStatus() : RestStatus {
        return RestStatus.fromCode(this.statusLine.statusCode)
    }

    protected fun Monitor.toHttpEntity() : HttpEntity {
        return StringEntity(toJsonString(), ContentType.APPLICATION_JSON)
    }

    private fun Monitor.toJsonString(): String {
        val builder = XContentFactory.jsonBuilder()
        return this.toXContent(builder).string()
    }

    private fun Alert.toHttpEntity(): HttpEntity {
        return StringEntity(toJsonString(), ContentType.APPLICATION_JSON)
    }

    private fun Alert.toJsonString(): String {
        val builder = XContentFactory.jsonBuilder()
        return this.toXContent(builder, ToXContent.EMPTY_PARAMS).string()
    }

    protected fun Monitor.relativeUrl() = "_awses/monitors/$id"

    protected fun alertsJson(ids: List<String>): HttpEntity {
        val builder = XContentFactory.jsonBuilder()
        builder.startObject().startArray("alerts")
        ids.forEach { builder.value(it) }
        builder.endArray().endObject()
        return StringEntity(builder.string(), ContentType.APPLICATION_JSON)
    }

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
}