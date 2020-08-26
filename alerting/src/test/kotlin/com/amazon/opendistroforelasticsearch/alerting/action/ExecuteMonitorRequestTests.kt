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

import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.randomMonitor
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase

class ExecuteMonitorRequestTests : ESTestCase() {

    fun `test execute monitor request with id`() {

        val req = ExecuteMonitorRequest(false, TimeValue.timeValueSeconds(100L), "1234", null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = ExecuteMonitorRequest(sin)
        assertEquals("1234", newReq.monitorId)
        assertEquals(false, newReq.dryrun)
        assertNull(newReq.monitor)
        assertEquals(req.monitor, newReq.monitor)
    }

    fun `test execute monitor request with monitor`() {
        val monitor = randomMonitor().copy(inputs = listOf(SearchInput(emptyList(), SearchSourceBuilder())))
        val req = ExecuteMonitorRequest(false, TimeValue.timeValueSeconds(100L), null, monitor)
        assertNotNull(req.monitor)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = ExecuteMonitorRequest(sin)
        assertNull(newReq.monitorId)
        assertEquals(false, newReq.dryrun)
        assertNotNull(newReq.monitor)
        assertEquals(req.monitor, newReq.monitor)
    }
}
