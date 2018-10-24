/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Monitor
import org.elasticsearch.client.Response
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.search.SearchModule
import org.elasticsearch.test.rest.ESRestTestCase
import org.junit.Before

abstract class MonitoringRestTestCase : ESRestTestCase() {

    @Before
    fun `recreate scheduled jobs index`() {
        // ESRestTestCase wipes all indexes after every test.
        // TODO: This really needs to be part of the plugin to watch for index deletion and recreate as needed.
        createIndex(ScheduledJob.SCHEDULED_JOBS_INDEX, Settings.EMPTY)
    }

    override fun xContentRegistry(): NamedXContentRegistry {
        return NamedXContentRegistry(mutableListOf(Monitor.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY,
                SNSAction.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }

    fun Response.asMap() : Map<String, Any> {
        return entityAsMap(this)
    }
}