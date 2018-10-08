package com.amazon.elasticsearch.action.node

import com.amazon.elasticsearch.JobSweeperMetrics
import com.amazon.elasticsearch.resthandler.RestScheduledJobStatsHandler
import com.amazon.elasticsearch.schedule.JobSchedulerMetrics
import org.elasticsearch.action.support.nodes.BaseNodeResponse
import org.elasticsearch.cluster.node.DiscoveryNode
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentFragment
import org.elasticsearch.common.xcontent.XContentBuilder

/**
 * Scheduled job stat that will be generated by each node.
 */
class ScheduledJobStats : BaseNodeResponse, ToXContentFragment {

    enum class ScheduleStatus(val status: String){
        RED("red"),
        GREEN("green");

        override fun toString(): String {
            return status
        }
    }

    lateinit var status: ScheduleStatus
    var jobSweeperMetrics: JobSweeperMetrics? = null
    var jobInfos: Array<JobSchedulerMetrics>? = null

    constructor()

    constructor(node: DiscoveryNode, status: ScheduleStatus, jobSweeperMetrics: JobSweeperMetrics?, jobsInfo: Array<JobSchedulerMetrics>?) : super(node) {
        this.status = status
        this.jobSweeperMetrics = jobSweeperMetrics
        this.jobInfos = jobsInfo
    }

    companion object {
        @JvmStatic
        fun readScheduledJobStatus(si: StreamInput): ScheduledJobStats {
            val scheduledJobStatus = ScheduledJobStats()
            scheduledJobStatus.readFrom(si)
            return scheduledJobStatus
        }
    }

    override fun readFrom(si: StreamInput) {
        super.readFrom(si)
        this.status = si.readEnum(ScheduleStatus::class.java)
        this.jobSweeperMetrics = si.readOptionalWriteable{ JobSweeperMetrics(si) }
        this.jobInfos = si.readOptionalArray({ JobSchedulerMetrics(si) }, { arrayOfNulls<JobSchedulerMetrics>(it)})
    }

    override fun writeTo(out: StreamOutput) {
        super.writeTo(out)
        out.writeEnum(status)
        out.writeOptionalWriteable(jobSweeperMetrics)
        out.writeOptionalArray(jobInfos)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.field("name", node.name)
        builder.field("schedule_status", status)
        builder.field("roles", node.roles.toTypedArray())
        if (jobSweeperMetrics != null) {
            builder.startObject(RestScheduledJobStatsHandler.JOB_SCHEDULING_METRICS)
            jobSweeperMetrics!!.toXContent(builder, params)
            builder.endObject()
        }

        if (jobInfos != null) {
            builder.startObject(RestScheduledJobStatsHandler.JOBS_INFO)
            for (job in jobInfos!!) {
                builder.startObject(job.scheduledJobId)
                job.toXContent(builder, params)
                builder.endObject()
            }
            builder.endObject()
        }
        return builder
    }
}