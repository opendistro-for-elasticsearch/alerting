/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

import com.amazon.elasticsearch.model.Action
import com.amazon.elasticsearch.model.Condition
import com.amazon.elasticsearch.model.Input
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.model.XContentTestBase
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.search.SearchModule
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
    fun `test condition parsing`() {
        val condition = randomCondition()

        val conditionString = condition.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedCondition = Condition.parse(parser(conditionString))

        assertEquals(condition, parsedCondition, "Round tripping Condition doesn't work")
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

    private fun randomCondition(): Condition {
        return Condition(name = "foo",
                severity = 1,
                condition = randomScript(),
                actions = listOf(randomAction())
        )
    }

    fun randomAction() : Action {
        return SNSAction(name = "foo",
                topicARN = "arn:bar:baz",
                messageTemplate = "quick brown fox",
                subjectTemplate = "you know the rest")
    }

    fun randomScript() : Script {
        return Script("return true")
    }
}