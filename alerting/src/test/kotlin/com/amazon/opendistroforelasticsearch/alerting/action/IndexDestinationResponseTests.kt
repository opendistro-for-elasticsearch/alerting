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

import com.amazon.opendistroforelasticsearch.alerting.model.destination.Chime
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase
import java.time.Instant

class IndexDestinationResponseTests : ESTestCase() {

    fun `test index destination response`() {

        val req = IndexDestinationResponse("1234", 0L, 1L, 2L, RestStatus.CREATED,
                Destination("1234", 0L, 1, DestinationType.CHIME, "TestChimeDest",
                    randomUser(), Instant.now(), Chime("test.com"), null, null, null))
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = IndexDestinationResponse(sin)
        assertEquals("1234", newReq.id)
        assertEquals(0, newReq.version)
        assertEquals(1, newReq.seqNo)
        assertEquals(2, newReq.primaryTerm)
        assertEquals(RestStatus.CREATED, newReq.status)
        assertNotNull(newReq.destination)
        assertEquals("1234", newReq.destination.id)
    }
}
