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

import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.rest.ESRestTestCase

class GetDestinationsRequestTests : ESTestCase() {

    fun `test get destination request`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetDestinationsRequest("1234", 1L, FetchSourceContext.FETCH_SOURCE, table, "slack", null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsRequest(sin)
        assertEquals("1234", newReq.destinationId)
        assertEquals(1L, newReq.version)
        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
        assertEquals(table, newReq.table)
        assertEquals("slack", newReq.destinationType)
    }

    fun `test get destination request without src context`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetDestinationsRequest("1234", 1L, null, table, "slack", null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsRequest(sin)
        assertEquals("1234", newReq.destinationId)
        assertEquals(1L, newReq.version)
        assertEquals(null, newReq.srcContext)
        assertEquals(table, newReq.table)
        assertEquals("slack", newReq.destinationType)
    }

    fun `test get destination request without destinationId`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetDestinationsRequest(null, 1L, FetchSourceContext.FETCH_SOURCE, table, "slack", null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsRequest(sin)
        assertEquals(null, newReq.destinationId)
        assertEquals(1L, newReq.version)
        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
        assertEquals(table, newReq.table)
        assertEquals("slack", newReq.destinationType)
    }

    fun `test get destination request with filter`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetDestinationsRequest(null, 1L, FetchSourceContext.FETCH_SOURCE, table, "slack", ESRestTestCase.randomAlphaOfLength(20))
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsRequest(sin)
        assertEquals(null, newReq.destinationId)
        assertEquals(1L, newReq.version)
        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
        assertEquals(table, newReq.table)
        assertEquals("slack", newReq.destinationType)
        assertNotNull(newReq.authHeader)
    }
}
