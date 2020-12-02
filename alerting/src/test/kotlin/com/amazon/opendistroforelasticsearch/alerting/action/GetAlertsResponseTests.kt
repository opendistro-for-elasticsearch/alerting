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

import com.amazon.opendistroforelasticsearch.alerting.builder
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.test.ESTestCase
import org.junit.Assert
import java.time.Instant
import java.util.Collections

class GetAlertsResponseTests : ESTestCase() {

    fun `test get alerts response with no alerts`() {
        val req = GetAlertsResponse(Collections.emptyList(), 0)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetAlertsResponse(sin)
        Assert.assertTrue(newReq.alerts.isEmpty())
        assertEquals(0, newReq.totalAlerts)
    }

    fun `test get alerts response with alerts`() {
        val alert = Alert(
                "id",
                0L,
                0,
                "monitorId",
                "monitorName",
                0L,
                randomUser(),
                "triggerId",
                "triggerName",
                Alert.State.ACKNOWLEDGED,
                Instant.MIN,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                "severity",
                Collections.emptyList()
        )
        val req = GetAlertsResponse(listOf(alert), 1)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetAlertsResponse(sin)
        assertEquals(1, newReq.alerts.size)
        assertEquals(alert, newReq.alerts[0])
        assertEquals(1, newReq.totalAlerts)
    }

    fun `test toXContent for get alerts response`() {
        val now = Instant.now()

        val alert = Alert(
                "id",
                0L,
                0,
                "monitorId",
                "monitorName",
                0L,
                User("admin", listOf(), listOf(), listOf()),
                "triggerId",
                "triggerName",
                Alert.State.ACKNOWLEDGED,
                now,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                "severity",
                Collections.emptyList()
        )
        val req = GetAlertsResponse(listOf(alert), 1)
        var actualXContentString = req.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val expectedXContentString = "{\"alerts\":[{\"id\":\"id\",\"version\":0,\"monitor_id\":\"monitorId\"," +
                "\"schema_version\":0,\"monitor_version\":0,\"monitor_name\":\"monitorName\"," +
                "\"monitor_user\":{\"name\":\"admin\",\"backend_roles\":[],\"roles\":[]," +
                "\"custom_attribute_names\":[],\"user_requested_tenant\":null},\"trigger_id\":\"triggerId\"," +
                "\"trigger_name\":\"triggerName\",\"state\":\"ACKNOWLEDGED\",\"error_message\":null,\"alert_history\":[]," +
                "\"severity\":\"severity\",\"action_execution_results\":[],\"start_time\":" + now.toEpochMilli() +
                ",\"last_notification_time\":null,\"end_time\":null,\"acknowledged_time\":null}],\"totalAlerts\":1}"
        assertEquals(expectedXContentString, actualXContentString)
    }
}
