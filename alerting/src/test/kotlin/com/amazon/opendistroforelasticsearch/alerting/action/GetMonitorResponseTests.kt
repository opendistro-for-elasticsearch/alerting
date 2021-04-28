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

import com.amazon.opendistroforelasticsearch.alerting.core.model.CronSchedule
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase
import java.time.Instant
import java.time.ZoneId

class GetMonitorResponseTests : ESTestCase() {

    fun `test get monitor response`() {
        val req = GetMonitorResponse("1234", 1L, 2L, 0L, RestStatus.OK, null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetMonitorResponse(sin)
        assertEquals("1234", newReq.id)
        assertEquals(1L, newReq.version)
        assertEquals(RestStatus.OK, newReq.status)
        assertEquals(null, newReq.monitor)
    }

    fun `test get monitor response with monitor`() {
        val cronExpression = "31 * * * *" // Run at minute 31.
        val testInstance = Instant.ofEpochSecond(1538164858L)

        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("Asia/Kolkata"), testInstance)
        val monitor = Monitor(
            id = "123",
            version = 0L,
            name = "test-monitor",
            enabled = true,
            schedule = cronSchedule,
            lastUpdateTime = Instant.now(),
            enabledTime = Instant.now(),
            monitorType = Monitor.MonitorType.TRADITIONAL_MONITOR,
            user = randomUser(),
            schemaVersion = 0,
            inputs = mutableListOf(),
            triggers = mutableListOf(),
            uiMetadata = mutableMapOf()
        )
        val req = GetMonitorResponse("1234", 1L, 2L, 0L, RestStatus.OK, monitor)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetMonitorResponse(sin)
        assertEquals("1234", newReq.id)
        assertEquals(1L, newReq.version)
        assertEquals(RestStatus.OK, newReq.status)
        assertNotNull(newReq.monitor)
    }
}
