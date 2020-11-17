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

package com.amazon.opendistroforelasticsearch.alerting.core

import com.amazon.opendistroforelasticsearch.alerting.core.schedule.JobSchedulerMetrics
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.test.ESTestCase.assertEquals
import org.joda.time.DateTime
import org.junit.Test

class WriteableTests {

    @Test
    fun `test jobschedule metrics as stream`() {
        val metrics = JobSchedulerMetrics("test", DateTime.now().millis, false)
        val out = BytesStreamOutput()
        metrics.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newMetrics = JobSchedulerMetrics(sin)
        assertEquals("Round tripping metrics doesn't work", metrics.scheduledJobId, newMetrics.scheduledJobId)
    }
}
