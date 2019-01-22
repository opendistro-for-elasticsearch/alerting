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

package com.amazon.opendistro

import com.amazon.opendistro.util.string
import com.amazon.opendistro.model.Input
import com.amazon.opendistro.model.SNSAction
import com.amazon.opendistro.model.SearchInput
import com.amazon.opendistro.model.XContentTestBase
import com.amazon.opendistro.model.action.Action
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