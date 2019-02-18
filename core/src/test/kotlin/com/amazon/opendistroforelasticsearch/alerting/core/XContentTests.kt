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

package com.amazon.opendistroforelasticsearch.alerting.core

import com.amazon.opendistroforelasticsearch.alerting.core.model.Input
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.core.model.XContentTestBase
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class XContentTests : XContentTestBase {

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
}
