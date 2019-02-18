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

package com.amazon.opendistroforelasticsearch.alerting

import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import org.elasticsearch.test.ESTestCase
import java.lang.IllegalArgumentException
import java.time.Instant

class MonitorTests : ESTestCase() {

    fun `test enabled time`() {
        val monitor = randomMonitor()
        val enabledMonitor = monitor.copy(enabled = true, enabledTime = Instant.now())
        try {
            enabledMonitor.copy(enabled = false)
            fail("Disabling monitor with enabled time set should fail.")
        } catch (e: IllegalArgumentException) {
        }

        val disabledMonitor = monitor.copy(enabled = false, enabledTime = null)

        try {
            disabledMonitor.copy(enabled = true)
            fail("Enabling monitor without enabled time should fail")
        } catch (e: IllegalArgumentException) {
        }
    }

    fun `test max triggers`() {
        val monitor = randomMonitor()

        val tooManyTriggers = mutableListOf<Trigger>()
        for (i in 0..10) {
            tooManyTriggers.add(randomTrigger())
        }

        try {
            monitor.copy(triggers = tooManyTriggers)
            fail("Monitor with too many triggers should be rejected.")
        } catch (e: IllegalArgumentException) {
        }
    }
}
