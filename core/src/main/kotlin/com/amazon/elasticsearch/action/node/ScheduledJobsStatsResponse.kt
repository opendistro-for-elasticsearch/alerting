package com.amazon.elasticsearch.action.node

import org.elasticsearch.action.FailedNodeException
import org.elasticsearch.action.support.nodes.BaseNodesResponse
import org.elasticsearch.cluster.ClusterName
import org.elasticsearch.cluster.health.ClusterIndexHealth
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentFragment
import org.elasticsearch.common.xcontent.XContentBuilder

/**
 * ScheduledJobsStatsResponse is a class that will contain all the response from each node.
 */
class ScheduledJobsStatsResponse : BaseNodesResponse<ScheduledJobStats>, ToXContentFragment {

    private var indexExists: Boolean? = null
    private var indexHealth: ClusterIndexHealth? = null

    constructor()
    constructor(clusterName: ClusterName, nodeResponses: List<ScheduledJobStats>, failures: List<FailedNodeException>,
                indexExists: Boolean, indexHealth: ClusterIndexHealth?) :
            super(clusterName, nodeResponses, failures) {
        this.indexExists = indexExists
        this.indexHealth = indexHealth
    }

    override fun writeNodesTo(out: StreamOutput, nodes: MutableList<ScheduledJobStats>) {
        out.writeStreamableList(nodes)
    }

    override fun readNodesFrom(si: StreamInput): MutableList<ScheduledJobStats> {
        return si.readList<ScheduledJobStats> { ScheduledJobStats.readScheduledJobStatus(it) }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.field("scheduled_job_index_exists", indexExists)
        if (indexExists == false) { // indexExists was not null and equal false
            return builder // no need to write all the ScheduledJobStatus if we do not have index.
        }
        builder.field("scheduled_job_index_status", indexHealth?.status?.name?.toLowerCase())
        val nodesOnSchedule = nodes.count { it.status == ScheduledJobStats.ScheduleStatus.GREEN }
        val nodesNotOnSchedule = nodes.count { it.status == ScheduledJobStats.ScheduleStatus.RED }
        builder.field("nodes_on_schedule", nodesOnSchedule)
        builder.field("nodes_not_on_schedule", nodesNotOnSchedule)
        builder.startObject("nodes")
        for (scheduledJobStatus in nodes) {
            builder.startObject(scheduledJobStatus.node.id)
            scheduledJobStatus.toXContent(builder, params)
            builder.endObject()
        }
        builder.endObject()

        return builder
    }
}