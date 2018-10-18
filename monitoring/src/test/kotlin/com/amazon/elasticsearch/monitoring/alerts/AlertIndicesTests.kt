/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.alerts

import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.randomAlert
import org.elasticsearch.Version
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.settings.Settings.EMPTY
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.node.Node
import org.elasticsearch.search.SearchModule
import org.elasticsearch.test.ESIntegTestCase
import org.elasticsearch.threadpool.Scheduler
import org.elasticsearch.threadpool.ThreadPool
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock

class AlertIndicesTests : ESIntegTestCase() {

    private fun rolloverSettings(maxAge: Long = 1, maxDocs: Long = 0) : Settings {
        return settings(Version.CURRENT)
                .put(AlertIndices.HISTORY_INDEX_MAX_AGE_SETTING.key, TimeValue.timeValueSeconds(maxAge))
                .put(AlertIndices.HISTORY_INDEX_MAX_DOCS_SETTING.key, maxDocs)
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
        val alert = randomAlert()
        val builder = XContentBuilder.builder(XContentType.JSON.xContent())
        val request = IndexRequest(AlertIndices.HISTORY_WRITE_INDEX, AlertIndices.MAPPING_TYPE)
                .source(alert.toXContent(builder, ToXContent.EMPTY_PARAMS))
        client().index(request).actionGet()

        // when:
        val oldAlertIndex = getIndexAlias(AlertIndices.HISTORY_WRITE_INDEX).single()
        assertTrue("Rollover failed", alertIndices.rolloverHistoryIndex())

        // then:
        val newAlertIndex = getIndexAlias(AlertIndices.HISTORY_WRITE_INDEX).single { it != oldAlertIndex }
        assertNotEquals("History index was not rolled over", oldAlertIndex, newAlertIndex)
    }

    private fun assertIndexExists(index: String) {
        assertTrue("Expected index $index is missing",
                client().admin().indices().exists(IndicesExistsRequest(index)).actionGet().isExists)
    }

    private fun getIndexAlias(alias: String) : List<String> {
        return client().admin().indices().getAliases(GetAliasesRequest(alias)).actionGet()
                .aliases.keys().map { it.value }
    }
}