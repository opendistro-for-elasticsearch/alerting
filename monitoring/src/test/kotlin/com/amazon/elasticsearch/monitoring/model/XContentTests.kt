/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.model.Action
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import junit.framework.Assert.assertEquals
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.Script
import org.elasticsearch.search.SearchModule
import org.junit.Test

class XContentTests {

    @Test
    fun `test trigger parsing`() {
        val trigger = randomTrigger()

        val triggerString = trigger.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedTrigger = Trigger.parse(parser(triggerString))

        assertEquals("Round tripping Trigger doesn't work", trigger, parsedTrigger)
    }

    fun builder(): XContentBuilder {
        return XContentBuilder.builder(XContentType.JSON.xContent())
    }

    fun parser(xc : String) : XContentParser {
        val parser = XContentType.JSON.xContent().createParser(xContentRegistry(), xc)
        parser.nextToken()
        return parser
    }

    fun xContentRegistry() : NamedXContentRegistry {
        return NamedXContentRegistry(listOf(SNSAction.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }


    private fun randomTrigger(): Trigger {
        return Trigger(name = "foo",
                severity = 1,
                condition = randomScript(),
                actions = listOf(randomAction())
        )
    }

    private fun randomScript() : Script {
        return Script("return true")
    }

    private fun randomAction() : Action {
        return SNSAction(name = "foo",
                topicARN = "arn:bar:baz",
                messageTemplate = "quick brown fox",
                subjectTemplate = "you know the rest")
    }
}