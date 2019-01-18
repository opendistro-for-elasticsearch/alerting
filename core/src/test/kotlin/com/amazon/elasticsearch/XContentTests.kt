/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch

import com.amazon.elasticsearch.model.Input
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.model.XContentTestBase
import com.amazon.elasticsearch.model.action.Action
import com.amazon.elasticsearch.model.action.SNSAction
import com.amazon.elasticsearch.util.string
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptType
import org.elasticsearch.search.builder.SearchSourceBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class XContentTests : XContentTestBase {

    @Test
    fun `test action parsing`() {
        val action = randomAction()

        val actionString = action.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedAction = Action.parse(parser(actionString))

        assertEquals(action, parsedAction, "Round tripping Action doesn't work")
    }

    @Test
    fun `test input parsing`() {
        val input = randomInput()

        val inputString = input.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedInput = Input.parse(parser(inputString))

        assertEquals(input, parsedInput, "Round tripping input doesn't work")
    }

    private fun randomInput(): Input {
        return SearchInput(indices = listOf("foo", "bar"),
                query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
    }


    fun randomAction() : Action {
        return SNSAction(name = "foo",
                topicARN = "arn:aws:sns:foo:012345678901:bar",
                roleARN = "arn:aws:iam::012345678901:foobar",
                messageTemplate = Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG, "quick brown fox", emptyMap()),
                subjectTemplate = Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG, "blah blah", emptyMap()))
    }
}