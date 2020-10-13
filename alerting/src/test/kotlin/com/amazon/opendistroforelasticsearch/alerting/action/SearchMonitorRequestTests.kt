/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.rest.ESRestTestCase
import java.util.concurrent.TimeUnit

class SearchMonitorRequestTests : ESTestCase() {

    fun `test search monitors request`() {
        val searchSourceBuilder = SearchSourceBuilder().from(0).size(100).timeout(TimeValue(60, TimeUnit.SECONDS))
        val searchRequest = SearchRequest().indices(ESRestTestCase.randomAlphaOfLength(10)).source(searchSourceBuilder)
        val searchMonitorRequest = SearchMonitorRequest(searchRequest, ESRestTestCase.randomAlphaOfLength(20))
        assertNotNull(searchMonitorRequest)

        val out = BytesStreamOutput()
        searchMonitorRequest.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = SearchMonitorRequest(sin)

        assertNotNull(newReq.authHeader)
        assertNotNull(newReq.searchRequest)
        assertEquals(1, newReq.searchRequest.indices().size)
    }
}
