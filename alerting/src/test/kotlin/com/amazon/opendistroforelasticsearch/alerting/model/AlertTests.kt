/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.model

import com.amazon.opendistroforelasticsearch.alerting.randomAlert
import org.elasticsearch.test.ESTestCase

class AlertTests : ESTestCase() {
    fun `test alert as template args`() {
        val alert = randomAlert().copy(acknowledgedTime = null, lastNotificationTime = null)

        val templateArgs = alert.asTemplateArg()

        assertEquals("Template args state does not match", templateArgs["state"], alert.state.toString())
        assertEquals("Template args error message does not match", templateArgs["error_message"], alert.errorMessage)
        assertEquals(
                "Template args acknowledged time does not match",
                templateArgs["acknowledged_time"],
                alert.acknowledgedTime?.toEpochMilli())
        assertEquals("Template args last notification time does not match",
                templateArgs["last_notification_time"],
                alert.lastNotificationTime?.toEpochMilli())
    }

    fun `test alert acknowledged`() {
        val ackAlert = randomAlert().copy(state = Alert.State.ACKNOWLEDGED)
        assertTrue("Alert is not acknowledged", ackAlert.isAcknowledged())

        val activeAlert = randomAlert().copy(state = Alert.State.ACTIVE)
        assertFalse("Alert is acknowledged", activeAlert.isAcknowledged())
    }
}
