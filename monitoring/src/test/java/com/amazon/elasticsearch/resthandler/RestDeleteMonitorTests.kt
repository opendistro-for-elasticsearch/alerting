/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler

import com.amazon.elasticsearch.monitor.Monitor
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.junit.annotations.TestLogging
import org.elasticsearch.test.rest.ESRestTestCase

@TestLogging("level:DEBUG")
class RestDeleteMonitorTests : ESRestTestCase() {

    @Throws(Exception::class)
    fun testPluginIsLoaded() {
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
    fun testDeleteMonitor() {
        val monitor = createRandomMonitor()

        val deleteResponse = client().performRequest("DELETE", "_awses/monitors/" + monitor.id)
        assertEquals("Delete did not return correct response",
                RestStatus.OK, RestStatus.fromCode(deleteResponse.statusLine.statusCode))

        val getResponse = client().performRequest("HEAD", "_awses/monitors/" + monitor.id)
        assertEquals("Deleted monitor is still present",
                RestStatus.NOT_FOUND, RestStatus.fromCode(getResponse.statusLine.statusCode))
    }

    private fun createRandomMonitor() : Monitor {
        val monitor = randomMonitor()
        val monitorSource = monitorSource(monitor)
        val response = client().performRequest("POST", "_awses/monitors",
                emptyMap(), StringEntity(monitorSource, ContentType.APPLICATION_JSON))
        assertTrue("Unable to create a new monitor", response.statusLine.statusCode == RestStatus.CREATED.status)

        val monitorId = XContentType.JSON.xContent()
                .createParser(NamedXContentRegistry.EMPTY, response.entity.content)
                .map()
                .get("_id") as String
        return monitor.copy(id = monitorId)
    }

    private fun monitorSource(monitor: Monitor): String {
        val builder = XContentFactory.jsonBuilder()
        monitor.toXContent(builder)
        return builder.string()
    }

    private fun randomMonitor(): Monitor {
        return Monitor(id = Monitor.NO_ID,
                name = randomAlphaOfLength(10),
                enabled = ESTestCase.randomBoolean(),
                search = "not implemented",
                schedule = "not implemented",
                actions = listOf());
    }
}
