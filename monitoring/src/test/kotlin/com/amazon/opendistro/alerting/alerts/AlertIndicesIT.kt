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

package com.amazon.opendistro.alerting.alerts

import com.amazon.opendistro.alerting.ALERTING_BASE_URI
import com.amazon.opendistro.alerting.model.Monitor
import com.amazon.opendistro.alerting.randomAlert
import com.amazon.opendistro.alerting.randomMonitor
import com.amazon.opendistro.alerting.randomTrigger
import com.amazon.opendistro.alerting.settings.MonitoringSettings
import com.amazon.opendistro.alerting.toJsonString
import com.amazon.opendistro.test.makeRequest
import com.amazon.opendistro.util.ElasticAPI
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.elasticsearch.Version
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.node.Node
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.test.ESIntegTestCase
import org.elasticsearch.threadpool.Scheduler
import org.elasticsearch.threadpool.ThreadPool
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock

class AlertIndicesIT : ESIntegTestCase() {

    private fun rolloverSettings(maxAge: Long = 1, maxDocs: Long = 0) : Settings {
        return settings(Version.CURRENT)
                .put(MonitoringSettings.ALERT_HISTORY_INDEX_MAX_AGE.key, TimeValue.timeValueSeconds(maxAge))
                .put(MonitoringSettings.ALERT_HISTORY_MAX_DOCS.key, maxDocs)
                .put(Node.NODE_NAME_SETTING.key, "test-node")
                .build()
    }

    var threadPool : ThreadPool = mock(ThreadPool::class.java)

    override fun setUp() {
        super.setUp()
        `when`(threadPool.scheduleWithFixedDelay(any(), any(), anyString()))
                .thenReturn(mock(Scheduler.Cancellable::class.java))
    }

    fun `test create alert index`() {
        val alertIndices = AlertIndices(rolloverSettings(), client().admin().indices(), threadPool)

        alertIndices.createAlertIndex()
        alertIndices.createInitialHistoryIndex()

        assertIndexExists(AlertIndices.ALERT_INDEX)
        assertIndexExists(AlertIndices.HISTORY_WRITE_INDEX)
    }

    fun `test rollover history index`() {
        // given:
        val alertIndices = AlertIndices(rolloverSettings(), client().admin().indices(), threadPool)
        alertIndices.createInitialHistoryIndex()
        val alert = randomAlert(monitor = createMonitor(randomMonitor()))
        val builder = XContentBuilder.builder(XContentType.JSON.xContent())
        val request = IndexRequest(AlertIndices.HISTORY_WRITE_INDEX, AlertIndices.MAPPING_TYPE)
                .source(alert.toXContent(builder, ToXContent.EMPTY_PARAMS))
                .routing(alert.monitorId)
        client().index(request).actionGet()

        // when:
        val oldAlertIndex = getIndexAlias(AlertIndices.HISTORY_WRITE_INDEX).single()
        assertTrue("Rollover failed", alertIndices.rolloverHistoryIndex())

        // then:
        val newAlertIndex = getIndexAlias(AlertIndices.HISTORY_WRITE_INDEX).single { it != oldAlertIndex }
        assertNotEquals("History index was not rolled over", oldAlertIndex, newAlertIndex)
    }

    // This is kind of a weird test. It's not testing the state of the local AlertIndices instance but
    // rather the instance inside the test cluster which is on a different JVM.  I suppose one could argue that
    // it's all the other tests that are weird since they're running plugin code outside the cluster... ¯\_(ツ)_/¯
    fun `test alert index gets recreated automatically if deleted`() {
        // Obi-Wan voice: These are not the AlertIndices you're looking for...
        val alertIndices = AlertIndices(rolloverSettings(), client().admin().indices(), threadPool)
        alertIndices.createAlertIndex()

        cluster().wipeIndices("_all")

        val trigger = randomTrigger().copy(condition = Script("return true"))
        val monitor = randomMonitor().copy(triggers = listOf(trigger))

        val response = getRestClient().makeRequest("POST", "$ALERTING_BASE_URI/_execute",
                mapOf("dryrun" to "true"), StringEntity(monitor.toJsonString(), ContentType.APPLICATION_JSON))
        val xcp = createParser(XContentType.JSON.xContent(), response.entity.content)
        val output = xcp.map()
        assertNull("Error running a monitor after wiping alert indices", output["error"])
    }

    private fun assertIndexExists(index: String) {
        assertTrue("Expected index $index is missing",
                client().admin().indices().exists(IndicesExistsRequest(index)).actionGet().isExists)
    }

    private fun getIndexAlias(alias: String) : List<String> {
        val aliases = client().admin().indices().getAliases(GetAliasesRequest(alias)).actionGet().aliases
        val indices = mutableListOf<String>()
        aliases.forEach { entry ->
            if (entry.value.any { amd -> amd.alias == alias }) {
                indices.add(entry.key)
            }
        }
        return indices.toList()
    }

    // Copy of code in MonitoringRestTestCase; it's needed because we don't have TransportClient support for monitor CRUD
    // APIs only RestClient. If we ever have more tests that are subclasses of ESIntegTestCase we should factor these
    // out so they're shared. But given that ESIntegTestCase is going to be deprecated soon we shouldn't have those.
    private fun createMonitor(monitor: Monitor, refresh: Boolean = true) : Monitor {
        val response = getRestClient().makeRequest("POST", "$ALERTING_BASE_URI?refresh=$refresh", emptyMap(),
                StringEntity(monitor.toJsonString(), ContentType.APPLICATION_JSON))
        val responseStatus = RestStatus.fromCode(response.statusLine.statusCode)
        assertEquals("Unable to create a new monitor", RestStatus.CREATED, responseStatus)

        val monitorJson = ElasticAPI.INSTANCE.jsonParser(NamedXContentRegistry.EMPTY, response.entity.content).map()
        return monitor.copy(id = monitorJson["_id"] as String, version = (monitorJson["_version"] as Int).toLong())
    }
}