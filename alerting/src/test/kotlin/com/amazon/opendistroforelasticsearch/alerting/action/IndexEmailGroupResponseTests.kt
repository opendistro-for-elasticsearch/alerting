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
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase

class IndexEmailGroupResponseTests : ESTestCase() {

    fun `test index email group response with email group`() {

        val testEmailGroup = randomEmailGroup(name = "test-email-group")
        val res = IndexEmailGroupResponse("1234", 1L, 1L, 2L, RestStatus.OK, testEmailGroup)
        assertNotNull(res)

        val out = BytesStreamOutput()
        res.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newRes = IndexEmailGroupResponse(sin)
        assertEquals("1234", newRes.id)
        assertEquals(1L, newRes.version)
        assertEquals(1L, newRes.seqNo)
        assertEquals(RestStatus.OK, newRes.status)
        assertNotNull(newRes.emailGroup)
        assertEquals("test-email-group", newRes.emailGroup.name)
    }
}
