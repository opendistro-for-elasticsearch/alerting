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

package com.amazon.opendistro.alerting.model

import com.amazon.opendistro.model.SNSAction
import com.amazon.opendistro.model.SearchInput
import com.amazon.opendistro.alerting.randomAlert
import com.amazon.opendistro.alerting.randomMonitor
import com.amazon.opendistro.alerting.randomTrigger
import com.amazon.opendistro.alerting.toJsonString
import com.amazon.opendistro.util.ElasticAPI
import com.amazon.opendistro.util.string
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.search.SearchModule
import org.elasticsearch.test.ESTestCase

class XContentTests : ESTestCase() {

    fun `test monitor parsing`() {
        val monitor = randomMonitor()

        val monitorString = monitor.toJsonString()
        val parsedMonitor = Monitor.parse(parser(monitorString))

        assertEquals("Round tripping Monitor doesn't work", monitor, parsedMonitor)
    }

    fun `test trigger parsing`() {
        val trigger = randomTrigger()

        val triggerString = trigger.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedTrigger = Trigger.parse(parser(triggerString))

        assertEquals("Round tripping Trigger doesn't work", trigger, parsedTrigger)
    }

    fun `test alert parsing`() {
        val alert = randomAlert()

        val alertString = alert.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedAlert = Alert.parse(parser(alertString))

        assertEquals("Round tripping alert doesn't work", alert, parsedAlert)
    }

    fun `test creating a monitor with duplicate trigger ids fails`() {
        try {
            val repeatedTrigger = randomTrigger()
            randomMonitor().copy(triggers = listOf(repeatedTrigger, repeatedTrigger))
            fail("Creating a monitor with duplicate triggers did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    private fun builder(): XContentBuilder {
        return XContentBuilder.builder(XContentType.JSON.xContent())
    }

    private fun parser(xc : String) : XContentParser {
        val parser = ElasticAPI.INSTANCE.jsonParser(xContentRegistry(), xc)
        parser.nextToken()
        return parser
    }

    override fun xContentRegistry() : NamedXContentRegistry {
        return NamedXContentRegistry(listOf(SNSAction.XCONTENT_REGISTRY, TestAction.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }
}
