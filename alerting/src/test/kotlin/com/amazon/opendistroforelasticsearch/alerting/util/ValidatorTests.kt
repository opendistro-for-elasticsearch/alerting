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

package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.randomMonitor
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.test.ESTestCase
import kotlin.test.assertFailsWith

class ValidatorTests : ESTestCase() {

    fun `test correct action throttle`() {
        val monitor = randomMonitor()

        Validator.validateActionThrottle(monitor, TimeValue.timeValueMinutes(120), TimeValue.timeValueMinutes(60))
    }

    fun `test action throttle greater than max value`() {
        val monitor = randomMonitor()
        assertFailsWith<IllegalArgumentException>("Can only set throttle period less than 1h") {
            Validator.validateActionThrottle(monitor, TimeValue.timeValueMinutes(60), TimeValue.timeValueMinutes(1)) }
    }

    fun `test action throttle less than min value`() {
        val monitor = randomMonitor()
        assertFailsWith<IllegalArgumentException>("Can only set throttle period greater than 2h") {
            Validator.validateActionThrottle(monitor, TimeValue.timeValueMinutes(180), TimeValue.timeValueMinutes(120)) }
    }
}
