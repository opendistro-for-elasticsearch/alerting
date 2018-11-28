/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.string
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHeader
import org.elasticsearch.client.Response
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.*
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
                SNSAction.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }

    fun Response.asMap() : Map<String, Any> {
        return entityAsMap(this)
    }

    protected fun createMonitor(monitor: Monitor, refresh: Boolean = false): Monitor {
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

        var id : String = ScheduledJob.NO_ID
        var version : Long = ScheduledJob.NO_VERSION
        var monitor : Monitor? = null

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            parser.nextToken()

            when (parser.currentName()) {
                "_id" ->      id = parser.text()
                "_version" ->  version = parser.longValue()
                "monitor" ->  monitor = Monitor.parse(parser)
            }
        }
        return monitor!!.copy(id  = id, version = version)
    }

    protected fun getAlert(alertId: String, monitorId: String): Alert {
        val response = client().performRequest("GET", "/.aes-alerts/_doc/$alertId?routing=$monitorId")
        assertEquals("Unable to get alert $alertId", RestStatus.OK, response.restStatus())

        val parser = createParser(XContentType.JSON.xContent(), response.entity.content)
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser::getTokenLocation)

        var id: String = Alert.NO_ID
        var version: Long = Alert.NO_VERSION
        var alert: Alert? = null

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            parser.nextToken()

            when (parser.currentName()) {
                "_id" -> id = parser.text()
                "_version" -> version = parser.longValue()
                "_source" -> alert = Alert.parse(parser, id, version)
            }
        }
        return alert!!
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