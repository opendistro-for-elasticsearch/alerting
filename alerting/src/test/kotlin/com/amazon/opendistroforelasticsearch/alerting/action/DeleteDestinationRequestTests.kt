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

import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.test.ESTestCase
import org.junit.Assert

class DeleteDestinationRequestTests : ESTestCase() {

    fun `test delete destination request`() {

        val req = DeleteDestinationRequest("1234", WriteRequest.RefreshPolicy.IMMEDIATE)
        Assert.assertNotNull(req)
        Assert.assertEquals("1234", req.destinationId)
        Assert.assertEquals("true", req.refreshPolicy?.value)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = DeleteDestinationRequest(sin)
        Assert.assertEquals("1234", newReq.destinationId)
        Assert.assertEquals("true", newReq.refreshPolicy?.value)
    }
}
