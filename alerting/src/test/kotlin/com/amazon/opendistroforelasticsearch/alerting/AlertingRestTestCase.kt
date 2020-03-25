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

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.core.settings.ScheduledJobSettings
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHeader
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.common.xcontent.json.JsonXContent
import org.elasticsearch.common.xcontent.json.JsonXContent.jsonXContent
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.SearchModule
import org.elasticsearch.test.rest.ESRestTestCase
import org.junit.AfterClass
import org.junit.rules.DisableOnDebug
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import javax.management.MBeanServerInvocationHandler
import javax.management.ObjectName
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

abstract class AlertingRestTestCase : ESRestTestCase() {

    private val isDebuggingTest = DisableOnDebug(null).isDebugging
    private val isDebuggingRemoteCluster = System.getProperty("cluster.debug", "false")!!.toBoolean()
    val numberOfNodes = System.getProperty("cluster.number_of_nodes", "1")!!.toInt()

    override fun xContentRegistry(): NamedXContentRegistry {
        return NamedXContentRegistry(mutableListOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }

    fun Response.asMap(): Map<String, Any> {
        return entityAsMap(this)
    }

    protected fun createMonitor(monitor: Monitor, refresh: Boolean = true): Monitor {
        val response = client().makeRequest("POST", "$ALERTING_BASE_URI?refresh=$refresh", emptyMap(),
                monitor.toHttpEntity())
        assertEquals("Unable to create a new monitor", RestStatus.CREATED, response.restStatus())

        val monitorJson = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE,
                response.entity.content).map()
        return monitor.copy(id = monitorJson["_id"] as String, version = (monitorJson["_version"] as Int).toLong())
    }

    protected fun createDestination(destination: Destination = getTestDestination(), refresh: Boolean = true): Destination {
        val response = client().makeRequest(
                "POST",
                "$DESTINATION_BASE_URI?refresh=$refresh",
                emptyMap(),
                destination.toHttpEntity())
        assertEquals("Unable to create a new destination", RestStatus.CREATED, response.restStatus())
        val destinationJson = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE,
                response.entity.content).map()
        return destination.copy(id = destinationJson["_id"] as String, version = (destinationJson["_version"] as Int).toLong())
    }

    protected fun updateDestination(destination: Destination, refresh: Boolean = true): Destination {
        val response = client().makeRequest(
                "PUT",
                "$DESTINATION_BASE_URI/${destination.id}?refresh=$refresh",
                emptyMap(),
                destination.toHttpEntity())
        assertEquals("Unable to update a destination", RestStatus.OK, response.restStatus())
        val destinationJson = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE,
                response.entity.content).map()
        return destination.copy(id = destinationJson["_id"] as String, version = (destinationJson["_version"] as Int).toLong())
    }

    private fun getTestDestination(): Destination {
        return Destination(
                type = DestinationType.TEST_ACTION,
                name = "test",
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = null,
                customWebhook = null)
    }

    protected fun verifyIndexSchemaVersion(index: String, expectedVersion: Int) {
        val indexMapping = client().getIndexMapping(index)
        val indexName = indexMapping.keys.toList()[0]
        val mappings = indexMapping.stringMap(indexName)?.stringMap("mappings")
        var version = 0
        if (mappings!!.containsKey("_meta")) {
            val meta = mappings.stringMap("_meta")
            if (meta!!.containsKey("schema_version")) version = meta.get("schema_version") as Int
        }
        assertEquals(expectedVersion, version)
    }

    protected fun createAlert(alert: Alert): Alert {
        val response = client().makeRequest("POST", "/${AlertIndices.ALERT_INDEX}/_doc?refresh=true&routing=${alert.monitorId}",
                emptyMap(), alert.toHttpEntity())
        assertEquals("Unable to create a new alert", RestStatus.CREATED, response.restStatus())

        val alertJson = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE,
                response.entity.content).map()
        return alert.copy(id = alertJson["_id"] as String, version = (alertJson["_version"] as Int).toLong())
    }

    protected fun createRandomMonitor(refresh: Boolean = false, withMetadata: Boolean = false): Monitor {
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
        val response = client().makeRequest("GET", "$ALERTING_BASE_URI/$monitorId", null, header)
        assertEquals("Unable to get monitor $monitorId", RestStatus.OK, response.restStatus())

        val parser = createParser(XContentType.JSON.xContent(), response.entity.content)
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser::getTokenLocation)

        lateinit var id: String
        var version: Long = 0
        lateinit var monitor: Monitor

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            parser.nextToken()

            when (parser.currentName()) {
                "_id" -> id = parser.text()
                "_version" -> version = parser.longValue()
                "monitor" -> monitor = Monitor.parse(parser)
            }
        }
        return monitor.copy(id = id, version = version)
    }

    protected fun searchAlerts(monitor: Monitor, indices: String = AlertIndices.ALERT_INDEX, refresh: Boolean = true): List<Alert> {
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

    protected fun acknowledgeAlerts(monitor: Monitor, vararg alerts: Alert): Response {
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

    protected fun deleteIndex(index: String): Response {
        val response = adminClient().makeRequest("DELETE", "/$index")
        assertEquals("Unable to delete index", RestStatus.OK, response.restStatus())
        return response
    }

    protected fun executeMonitor(monitorId: String, params: Map<String, String> = mutableMapOf()): Response {
        return client().makeRequest("POST", "$ALERTING_BASE_URI/$monitorId/_execute", params)
    }

    protected fun executeMonitor(monitor: Monitor, params: Map<String, String> = mapOf()): Response =
            client().makeRequest("POST", "$ALERTING_BASE_URI/_execute", params, monitor.toHttpEntity())

    protected fun indexDoc(index: String, id: String, doc: String, refresh: Boolean = true): Response {
        val requestBody = StringEntity(doc, APPLICATION_JSON)
        val params = if (refresh) mapOf("refresh" to "true") else mapOf()
        val response = client().makeRequest("PUT", "$index/_doc/$id", params, requestBody)
        assertTrue("Unable to index doc: '${doc.take(15)}...' to index: '$index'",
                listOf(RestStatus.OK, RestStatus.CREATED).contains(response.restStatus()))
        return response
    }

    /** A test index that can be used across tests. Feel free to add new fields but don't remove any. */
    protected fun createTestIndex(index: String = randomAlphaOfLength(10).toLowerCase(Locale.ROOT)): String {
        createIndex(index, Settings.EMPTY, """
          "properties" : {
             "test_strict_date_time" : { "type" : "date", "format" : "strict_date_time" }
          }
        """.trimIndent())
        return index
    }

    fun putAlertMappings(mapping: String? = null) {
        val mappingHack = if (mapping != null) mapping else AlertIndices.alertMapping().trimStart('{').trimEnd('}')
        val encodedHistoryIndex = URLEncoder.encode(AlertIndices.HISTORY_INDEX_PATTERN, Charsets.UTF_8.toString())
        createIndex(AlertIndices.ALERT_INDEX, Settings.EMPTY, mappingHack)
        createIndex(encodedHistoryIndex, Settings.EMPTY, mappingHack, "\"${AlertIndices.HISTORY_WRITE_INDEX}\" : {}")
    }

    fun scheduledJobMappings(): String {
        return javaClass.classLoader.getResource("mappings/scheduled-jobs.json").readText()
    }

    fun createAlertingConfigIndex(mapping: String? = null) {
        val mappingHack = if (mapping != null) mapping else scheduledJobMappings().trimStart('{').trimEnd('}')
        createIndex(ScheduledJob.SCHEDULED_JOBS_INDEX, Settings.EMPTY, mappingHack)
    }

    protected fun Response.restStatus(): RestStatus {
        return RestStatus.fromCode(this.statusLine.statusCode)
    }

    protected fun Monitor.toHttpEntity(): HttpEntity {
        return StringEntity(toJsonString(), APPLICATION_JSON)
    }

    private fun Monitor.toJsonString(): String {
        val builder = XContentFactory.jsonBuilder()
        return shuffleXContent(toXContent(builder)).string()
    }

    private fun Destination.toHttpEntity(): HttpEntity {
        return StringEntity(toJsonString(), APPLICATION_JSON)
    }

    private fun Destination.toJsonString(): String {
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

    protected fun Monitor.relativeUrl() = "$ALERTING_BASE_URI/$id"

    // Useful settings when debugging to prevent timeouts
    override fun restClientSettings(): Settings {
        return if (isDebuggingTest || isDebuggingRemoteCluster) {
            Settings.builder()
                    .put(CLIENT_SOCKET_TIMEOUT, TimeValue.timeValueMinutes(10))
                    .build()
        } else {
            super.restClientSettings()
        }
    }

    fun RestClient.getClusterSettings(settings: Map<String, String>): Map<String, Any> {
        val response = this.makeRequest("GET", "_cluster/settings", settings)
        assertEquals(RestStatus.OK, response.restStatus())
        return response.asMap()
    }

    fun RestClient.getIndexMapping(index: String): Map<String, Any> {
        val response = this.makeRequest("GET", "$index/_mapping")
        assertEquals(RestStatus.OK, response.restStatus())
        return response.asMap()
    }

    fun RestClient.updateSettings(setting: String, value: Any): Map<String, Any> {
        val settings = jsonBuilder()
                .startObject()
                .startObject("persistent")
                .field(setting, value)
                .endObject()
                .endObject()
                .string()
        val response = this.makeRequest("PUT", "_cluster/settings", StringEntity(settings, APPLICATION_JSON))
        assertEquals(RestStatus.OK, response.restStatus())
        return response.asMap()
    }

    @Suppress("UNCHECKED_CAST")
    fun Map<String, Any>.opendistroSettings(): Map<String, Any>? {
        val map = this as Map<String, Map<String, Map<String, Map<String, Any>>>>
        return map["defaults"]?.get("opendistro")?.get("alerting")
    }

    @Suppress("UNCHECKED_CAST")
    fun Map<String, Any>.stringMap(key: String): Map<String, Any>? {
        val map = this as Map<String, Map<String, Any>>
        return map[key]
    }

    fun getAlertingStats(metrics: String = ""): Map<String, Any> {
        val monitorStatsResponse = client().makeRequest("GET", "/_opendistro/_alerting/stats$metrics")
        val responseMap = createParser(XContentType.JSON.xContent(), monitorStatsResponse.entity.content).map()
        return responseMap
    }

    fun enableScheduledJob(): Response {
        val updateResponse = client().makeRequest("PUT", "_cluster/settings",
                emptyMap(),
                StringEntity(XContentFactory.jsonBuilder().startObject().field("persistent")
                        .startObject().field(ScheduledJobSettings.SWEEPER_ENABLED.key, true).endObject()
                        .endObject().string(), ContentType.APPLICATION_JSON))
        return updateResponse
    }

    fun disableScheduledJob(): Response {
        val updateResponse = client().makeRequest("PUT", "_cluster/settings",
                emptyMap(),
                StringEntity(XContentFactory.jsonBuilder().startObject().field("persistent")
                        .startObject().field(ScheduledJobSettings.SWEEPER_ENABLED.key, false).endObject()
                        .endObject().string(), ContentType.APPLICATION_JSON))
        return updateResponse
    }

    companion object {
        internal interface IProxy {
            val version: String?
            var sessionId: String?

            fun getExecutionData(reset: Boolean): ByteArray?
            fun dump(reset: Boolean)
            fun reset()
        }

        /*
        * We need to be able to dump the jacoco coverage before the cluster shuts down.
        * The new internal testing framework removed some gradle tasks we were listening to,
        * to choose a good time to do it. This will dump the executionData to file after each test.
        * TODO: This is also currently just overwriting integTest.exec with the updated execData without
        *   resetting after writing each time. This can be improved to either write an exec file per test
        *   or by letting jacoco append to the file.
        * */
        @JvmStatic
        @AfterClass
        fun dumpCoverage() {
            // jacoco.dir set in esplugin-coverage.gradle, if it doesn't exist we don't
            // want to collect coverage, so we can return early
            val jacocoBuildPath = System.getProperty("jacoco.dir") ?: return
            val serverUrl = "service:jmx:rmi:///jndi/rmi://127.0.0.1:7777/jmxrmi"
            JMXConnectorFactory.connect(JMXServiceURL(serverUrl)).use { connector ->
                val proxy = MBeanServerInvocationHandler.newProxyInstance(
                        connector.mBeanServerConnection,
                        ObjectName("org.jacoco:type=Runtime"),
                        IProxy::class.java,
                        false
                )
                proxy.getExecutionData(false)?.let {
                    val path = Path.of("$jacocoBuildPath/integTest.exec")
                    Files.write(path, it)
                }
            }
        }
    }
}
