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

import com.amazon.opendistroforelasticsearch.alerting.randomEmailGroup
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.test.ESTestCase

class IndexEmailGroupRequestTests : ESTestCase() {

    fun `test index email group post request`() {

        val req = IndexEmailGroupRequest("1234", 1L, 2L, WriteRequest.RefreshPolicy.IMMEDIATE, RestRequest.Method.POST,
            randomEmailGroup())
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = IndexEmailGroupRequest(sin)
        assertEquals("1234", newReq.emailGroupID)
        assertEquals(1L, newReq.seqNo)
        assertEquals(2L, newReq.primaryTerm)
        assertEquals(RestRequest.Method.POST, newReq.method)
        assertNotNull(newReq.emailGroup)
    }

    fun `test index email group put request`() {

        val req = IndexEmailGroupRequest("1234", 1L, 2L, WriteRequest.RefreshPolicy.IMMEDIATE, RestRequest.Method.PUT,
            randomEmailGroup())
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = IndexEmailGroupRequest(sin)
        assertEquals("1234", newReq.emailGroupID)
        assertEquals(1L, newReq.seqNo)
        assertEquals(2L, newReq.primaryTerm)
        assertEquals(RestRequest.Method.PUT, newReq.method)
        assertNotNull(newReq.emailGroup)
    }
}
