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

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertError
import com.amazon.opendistroforelasticsearch.alerting.model.ActionExecutionResult
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.test.ESTestCase
import org.junit.Assert
import java.time.Instant

class AcknowledgeAlertResponseTests : ESTestCase() {

    fun `test acknowledge alert response`() {

        val acknowledged = mutableListOf(
            Alert("1234", 0L, 1, "monitor-1234", "test-monitor", 0L, randomUser(),
                    "trigger-14", "test-trigger", Alert.State.ACKNOWLEDGED,
                    Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, ArrayList(),
                    "sev-2", ArrayList(), null
            )
        )
        val failed = mutableListOf(
            Alert("1234", 0L, 1, "monitor-1234", "test-monitor", 0L, randomUser(),
                "trigger-14", "test-trigger", Alert.State.ERROR, Instant.now(), Instant.now(),
                Instant.now(), Instant.now(), null, mutableListOf(AlertError(Instant.now(), "Error msg")),
        "sev-2", mutableListOf(ActionExecutionResult("7890", null, 0)), null)
        )
        val missing = mutableListOf("1", "2", "3", "4")

        val req = AcknowledgeAlertResponse(acknowledged, failed, missing)
        Assert.assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = AcknowledgeAlertResponse(sin)
        Assert.assertEquals(1, newReq.acknowledged.size)
        Assert.assertEquals(1, newReq.failed.size)
        Assert.assertEquals(4, newReq.missing.size)
    }
}
