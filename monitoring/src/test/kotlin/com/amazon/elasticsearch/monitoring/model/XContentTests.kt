/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.search.SearchModule
import org.elasticsearch.test.ESTestCase
import com.amazon.elasticsearch.monitoring.randomMonitor
import com.amazon.elasticsearch.monitoring.randomTrigger

class XContentTests :ESTestCase() {

    fun `test trigger parsing`() {
        val trigger = randomTrigger()

        val triggerString = trigger.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedTrigger = Trigger.parse(parser(triggerString))

        assertEquals("Round tripping Trigger doesn't work", trigger, parsedTrigger)
    }

    fun `test creating a monitor with duplicate trigger ids fails`() {
        try {
            val repeatedTrigger = randomTrigger()
            randomMonitor().copy(triggers = listOf(repeatedTrigger, repeatedTrigger))
            fail("Creating a monitor with duplicate triggers did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun builder(): XContentBuilder {
        return XContentBuilder.builder(XContentType.JSON.xContent())
    }

    fun parser(xc : String) : XContentParser {
        val parser = XContentType.JSON.xContent().createParser(xContentRegistry(), xc)
        parser.nextToken()
        return parser
    }

    override fun xContentRegistry() : NamedXContentRegistry {
        return NamedXContentRegistry(listOf(SNSAction.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }
}