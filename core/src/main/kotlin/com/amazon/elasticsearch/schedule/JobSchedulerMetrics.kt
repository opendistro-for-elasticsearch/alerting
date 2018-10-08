package com.amazon.elasticsearch.schedule

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
        if (lastExecutionTime != null) builder.field("last_execution_time", Instant.ofEpochMilli(lastExecutionTime))
        builder.field("running_on_time", runningOnTime)
        return builder
    }
}