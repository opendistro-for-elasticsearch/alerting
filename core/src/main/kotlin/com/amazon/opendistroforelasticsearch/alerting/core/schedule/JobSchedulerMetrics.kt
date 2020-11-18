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

package com.amazon.opendistroforelasticsearch.alerting.core.schedule

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentFragment
import org.elasticsearch.common.xcontent.XContentBuilder
import java.time.Instant

class JobSchedulerMetrics : ToXContentFragment, Writeable {
    val scheduledJobId: String
    val lastExecutionTime: Long?
    val runningOnTime: Boolean

    constructor(scheduledJobId: String, lastExecutionTime: Long?, runningOnTime: Boolean) {
        this.scheduledJobId = scheduledJobId
        this.lastExecutionTime = lastExecutionTime
        this.runningOnTime = runningOnTime
    }

    constructor(si: StreamInput) {
        scheduledJobId = si.readString()
        lastExecutionTime = si.readOptionalLong()
        runningOnTime = si.readBoolean()
    }

    override fun writeTo(out: StreamOutput) {
        out.writeString(scheduledJobId)
        out.writeOptionalLong(lastExecutionTime)
        out.writeBoolean(runningOnTime)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        if (lastExecutionTime != null)
            builder.timeField("last_execution_time", "last_execution_time_in_millis", Instant.ofEpochMilli(lastExecutionTime).toEpochMilli())
        builder.field("running_on_time", runningOnTime)
        return builder
    }
}
