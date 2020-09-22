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

import com.amazon.opendistroforelasticsearch.alerting.randomEmailAccount
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase

class IndexEmailAccountResponseTests : ESTestCase() {

    fun `test index email account response with email account`() {

        val testEmailAccount = randomEmailAccount(name = "test_email_account")
        val res = IndexEmailAccountResponse("1234", 1L, 1L, 2L, RestStatus.OK, testEmailAccount)
        assertNotNull(res)

        val out = BytesStreamOutput()
        res.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newRes = IndexEmailAccountResponse(sin)
        assertEquals("1234", newRes.id)
        assertEquals(1L, newRes.version)
        assertEquals(1L, newRes.seqNo)
        assertEquals(RestStatus.OK, newRes.status)
        assertNotNull(newRes.emailAccount)
        assertEquals("test_email_account", newRes.emailAccount.name)
    }
}
