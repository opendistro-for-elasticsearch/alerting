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

import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import org.elasticsearch.test.ESTestCase

class GetMonitorRequestTests : ESTestCase() {

    fun `test get monitor request`() {

        val req = GetMonitorRequest("1234", 1L, RestRequest.Method.GET, FetchSourceContext.FETCH_SOURCE)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetMonitorRequest(sin)
        assertEquals("1234", newReq.monitorId)
        assertEquals(1L, newReq.version)
        assertEquals(RestRequest.Method.GET, newReq.method)
        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
    }

    fun `test head monitor request`() {

        val req = GetMonitorRequest("1234", 2L, RestRequest.Method.HEAD, FetchSourceContext.FETCH_SOURCE)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetMonitorRequest(sin)
        assertEquals("1234", newReq.monitorId)
        assertEquals(2L, newReq.version)
        assertEquals(RestRequest.Method.HEAD, newReq.method)
        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
    }
}
