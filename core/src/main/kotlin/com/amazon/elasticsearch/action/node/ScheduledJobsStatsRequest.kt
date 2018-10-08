package com.amazon.elasticsearch.action.node

import org.elasticsearch.action.support.nodes.BaseNodesRequest
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import java.io.IOException

/**
 * A request to get node (cluster) level ScheduledJobsStatus.
 * By default all the parameters will be true.
 */
class ScheduledJobsStatsRequest : BaseNodesRequest<ScheduledJobsStatsRequest> {
    var jobSchedulingMetrics: Boolean = true
    var jobsInfo: Boolean = true

    constructor()
    constructor(nodeIds: Array<String>) : super(*nodeIds)

    @Throws(IOException::class)
    override fun readFrom(si: StreamInput) {
        super.readFrom(si)
        jobSchedulingMetrics = si.readBoolean()
        jobsInfo = si.readBoolean()
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        super.writeTo(out)
        out.writeBoolean(jobSchedulingMetrics)
        out.writeBoolean(jobsInfo)
    }

    fun all(): ScheduledJobsStatsRequest {
        jobSchedulingMetrics = true
        jobsInfo = true
        return this
    }

    fun clear(): ScheduledJobsStatsRequest{
        jobSchedulingMetrics = false
        jobsInfo = false
        return this
    }
}