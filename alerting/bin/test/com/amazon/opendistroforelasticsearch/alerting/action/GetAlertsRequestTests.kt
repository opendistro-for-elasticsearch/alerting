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
import org.elasticsearch.test.ESTestCase

class GetAlertsRequestTests : ESTestCase() {

    fun `test get alerts request`() {

        val table = Table("asc", "sortString", null, 1, 0, "")

        val req = GetAlertsRequest(table, "1", "active", null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetAlertsRequest(sin)

        assertEquals("1", newReq.severityLevel)
        assertEquals("active", newReq.alertState)
        assertNull(newReq.monitorId)
        assertEquals(table, newReq.table)
    }

    fun `test get alerts request with filter`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetAlertsRequest(table, "1", "active", null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetAlertsRequest(sin)

        assertEquals("1", newReq.severityLevel)
        assertEquals("active", newReq.alertState)
        assertNull(newReq.monitorId)
        assertEquals(table, newReq.table)
    }

    fun `test validate returns null`() {
        val table = Table("asc", "sortString", null, 1, 0, "")

        val req = GetAlertsRequest(table, "1", "active", null)
        assertNotNull(req)
        assertNull(req.validate())
    }
}
