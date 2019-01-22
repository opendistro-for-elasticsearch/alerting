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

package com.amazon.opendistro.alerting

import org.elasticsearch.test.ESTestCase
import java.lang.IllegalArgumentException
import java.time.Instant


class MonitorTests: ESTestCase() {

    fun `test enabled time`() {
        val monitor = randomMonitor()
        val enabled_monitor = monitor.copy(enabled = true, enabledTime = Instant.now())
        try {
            enabled_monitor.copy(enabled = false)
            fail("Disabling monitor with enabled time set should fail.")
        } catch (e: IllegalArgumentException) {
        }

        val disabled_monitor = monitor.copy(enabled = false, enabledTime = null)

        try {
            disabled_monitor.copy(enabled = true)
            fail("Enabling monitor without enabled time should fail")
        } catch (e: IllegalArgumentException) {
        }
    }
}