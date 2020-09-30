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

import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Slack
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase
import java.time.Instant
import java.util.Collections

class GetDestinationsResponseTests : ESTestCase() {

    fun `test get destination response with no destinations`() {
        val req = GetDestinationsResponse(RestStatus.OK, 0, Collections.emptyList())
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsResponse(sin)
        assertEquals(0, newReq.totalDestinations)
        assertTrue(newReq.destinations.isEmpty())
        assertEquals(RestStatus.OK, newReq.status)
    }

    fun `test get destination response with a destination`() {
        val slack = Slack("url")
        val destination = Destination(
                "id",
                0L,
                0,
                0,
                0,
                DestinationType.SLACK,
                "name",
                null,
                Instant.MIN,
                null,
                slack,
                null,
                null)

        val req = GetDestinationsResponse(RestStatus.OK, 1, listOf(destination))
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsResponse(sin)
        assertEquals(1, newReq.totalDestinations)
        assertEquals(destination, newReq.destinations[0])
        assertEquals(RestStatus.OK, newReq.status)
    }
}
