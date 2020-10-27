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

package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.ANOMALY_RESULT_INDEX
import com.amazon.opendistroforelasticsearch.alerting.core.model.Input
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.randomMonitor
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase

class AnomalyDetectionUtilsTests : ESTestCase() {

    fun `test is ad monitor`() {
        val monitor = randomMonitor(
                inputs = listOf(SearchInput(listOf(ANOMALY_RESULT_INDEX),
                        SearchSourceBuilder().query(QueryBuilders.matchAllQuery())))
        )
        assertTrue(isADMonitor(monitor))
    }

    fun `test not ad monitor if monitor have no inputs`() {

        val monitor = randomMonitor(
                inputs = listOf()
        )
        assertFalse(isADMonitor(monitor))
    }

    fun `test not ad monitor if monitor input is not search input`() {
        val monitor = randomMonitor(
                inputs = listOf(object : Input {
                    override fun name(): String {
                        TODO("Not yet implemented")
                    }

                    override fun writeTo(out: StreamOutput?) {
                        TODO("Not yet implemented")
                    }

                    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
                        TODO("Not yet implemented")
                    }
                })
        )
        assertFalse(isADMonitor(monitor))
    }

    fun `test not ad monitor if monitor input has more than 1 indices`() {
        val monitor = randomMonitor(
                inputs = listOf(SearchInput(listOf(randomAlphaOfLength(5), randomAlphaOfLength(5)),
                        SearchSourceBuilder().query(QueryBuilders.matchAllQuery())))
        )
        assertFalse(isADMonitor(monitor))
    }

    fun `test not ad monitor if monitor input's index name is not AD result index`() {
        val monitor = randomMonitor(
                inputs = listOf(SearchInput(listOf(randomAlphaOfLength(5)), SearchSourceBuilder().query(QueryBuilders.matchAllQuery())))
        )
        assertFalse(isADMonitor(monitor))
    }

    fun `test add user role filter with null user`() {
        val searchSourceBuilder = SearchSourceBuilder()
        addUserBackendRolesFilter(null, searchSourceBuilder)
        assertEquals("{\"query\":{\"bool\":{\"must_not\":[{\"nested\":{\"query\":{\"exists\":{\"field\":\"user\",\"boost\":1.0}}," +
                "\"path\":\"user\",\"ignore_unmapped\":false,\"score_mode\":\"none\",\"boost\":1.0}}],\"adjust_pure_negative\":true," +
                "\"boost\":1.0}}}", searchSourceBuilder.toString())
    }

    fun `test add user role filter with null user backend role`() {
        val searchSourceBuilder = SearchSourceBuilder()
        addUserBackendRolesFilter(User(randomAlphaOfLength(5), null, listOf(randomAlphaOfLength(5)),
                listOf(randomAlphaOfLength(5))), searchSourceBuilder)
        assertEquals("{\"query\":{\"bool\":{\"must\":[{\"nested\":{\"query\":{\"exists\":{\"field\":\"user\",\"boost\":1.0}}," +
                "\"path\":\"user\",\"ignore_unmapped\":false,\"score_mode\":\"none\",\"boost\":1.0}}],\"must_not\":[{\"nested\":" +
                "{\"query\":{\"exists\":{\"field\":\"user.backend_roles.keyword\",\"boost\":1.0}},\"path\":\"user\",\"ignore_unmapped\"" +
                ":false,\"score_mode\":\"none\",\"boost\":1.0}}],\"adjust_pure_negative\":true,\"boost\":1.0}}}",
                searchSourceBuilder.toString())
    }

    fun `test add user role filter with empty user backend role`() {
        val searchSourceBuilder = SearchSourceBuilder()
        addUserBackendRolesFilter(User(randomAlphaOfLength(5), listOf(), listOf(randomAlphaOfLength(5)),
                listOf(randomAlphaOfLength(5))), searchSourceBuilder)
        assertEquals("{\"query\":{\"bool\":{\"must\":[{\"nested\":{\"query\":{\"exists\":{\"field\":\"user\",\"boost\":1.0}}," +
                "\"path\":\"user\",\"ignore_unmapped\":false,\"score_mode\":\"none\",\"boost\":1.0}}],\"must_not\":[{\"nested\":" +
                "{\"query\":{\"exists\":{\"field\":\"user.backend_roles.keyword\",\"boost\":1.0}},\"path\":\"user\",\"ignore_unmapped\"" +
                ":false,\"score_mode\":\"none\",\"boost\":1.0}}],\"adjust_pure_negative\":true,\"boost\":1.0}}}",
                searchSourceBuilder.toString())
    }

    fun `test add user role filter with normal user backend role`() {
        val searchSourceBuilder = SearchSourceBuilder()
        val backendRole1 = randomAlphaOfLength(5)
        val backendRole2 = randomAlphaOfLength(5)
        addUserBackendRolesFilter(User(randomAlphaOfLength(5), listOf(backendRole1, backendRole2), listOf(randomAlphaOfLength(5)),
                listOf(randomAlphaOfLength(5))), searchSourceBuilder)
        assertEquals("{\"query\":{\"bool\":{\"must\":[{\"nested\":{\"query\":{\"terms\":{\"user.backend_roles.keyword\":" +
                "[\"$backendRole1\",\"$backendRole2\"]," +
                "\"boost\":1.0}},\"path\":\"user\",\"ignore_unmapped\":false,\"score_mode\":\"none\",\"boost\":1.0}}]," +
                "\"adjust_pure_negative\":true,\"boost\":1.0}}}", searchSourceBuilder.toString())
    }
}
