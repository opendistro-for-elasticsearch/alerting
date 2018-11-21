/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.util.ElasticAPI
import com.amazon.elasticsearch.util.string
import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.elasticsearch.client.Response
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentFactory
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