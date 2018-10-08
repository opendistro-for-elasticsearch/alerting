package com.amazon.elasticsearch

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentFragment
import org.elasticsearch.common.xcontent.XContentBuilder

data class JobSweeperMetrics(val lastFullSweepTimeMillis: Long, val fullSweepOnTime: Boolean) : ToXContentFragment, Writeable {

    constructor(si: StreamInput) : this(si.readLong(), si.readBoolean())

    override fun writeTo(out: StreamOutput) {
        out.writeLong(lastFullSweepTimeMillis)
        out.writeBoolean(fullSweepOnTime)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.field("last_full_sweep_time_millis", lastFullSweepTimeMillis)
        builder.field("full_sweep_on_time", fullSweepOnTime)
        return builder
    }
}