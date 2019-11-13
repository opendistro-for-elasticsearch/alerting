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

package com.amazon.opendistroforelasticsearch.alerting.core.action.node

import com.amazon.opendistroforelasticsearch.alerting.core.JobSweeper
import com.amazon.opendistroforelasticsearch.alerting.core.JobSweeperMetrics
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.schedule.JobScheduler
import com.amazon.opendistroforelasticsearch.alerting.core.schedule.JobSchedulerMetrics
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.FailedNodeException
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.nodes.BaseNodeRequest
import org.elasticsearch.action.support.nodes.TransportNodesAction
import org.elasticsearch.cluster.health.ClusterIndexHealth
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.transport.TransportService
import java.io.IOException

private val log = LogManager.getLogger(ScheduledJobsStatsTransportAction::class.java)

class ScheduledJobsStatsTransportAction : TransportNodesAction<ScheduledJobsStatsRequest, ScheduledJobsStatsResponse,
        ScheduledJobsStatsTransportAction.ScheduledJobStatusRequest, ScheduledJobStats> {

    private val jobSweeper: JobSweeper
    private val jobScheduler: JobScheduler
    private val scheduledJobIndices: ScheduledJobIndices

    @Inject
    constructor(
        threadPool: ThreadPool,
        clusterService: ClusterService,
        transportService: TransportService,
        actionFilters: ActionFilters,
        jobSweeper: JobSweeper,
        jobScheduler: JobScheduler,
        scheduledJobIndices: ScheduledJobIndices
    ) : super(
        ScheduledJobsStatsAction.NAME,
        threadPool,
        clusterService,
        transportService,
        actionFilters,
        { ScheduledJobsStatsRequest() },
        { ScheduledJobStatusRequest() },
        ThreadPool.Names.MANAGEMENT,
        ScheduledJobStats::class.java
    ) {
        this.jobSweeper = jobSweeper
        this.jobScheduler = jobScheduler
        this.scheduledJobIndices = scheduledJobIndices
    }

    override fun newNodeRequest(request: ScheduledJobsStatsRequest): ScheduledJobStatusRequest {
        return ScheduledJobStatusRequest(request)
    }

    override fun newNodeResponse(): ScheduledJobStats {
        return ScheduledJobStats()
    }

    override fun newResponse(
        request: ScheduledJobsStatsRequest,
        responses: MutableList<ScheduledJobStats>,
        failures: MutableList<FailedNodeException>
    ): ScheduledJobsStatsResponse {
        val scheduledJobEnabled = jobSweeper.isSweepingEnabled()
        val scheduledJobIndexExist = scheduledJobIndices.scheduledJobIndexExists()
        val indexHealth: ClusterIndexHealth? = if (scheduledJobIndexExist) scheduledJobIndices.scheduledJobIndexHealth() else null

        return ScheduledJobsStatsResponse(
                clusterService.clusterName,
                responses,
                failures,
                scheduledJobEnabled,
                scheduledJobIndexExist,
                indexHealth)
    }

    override fun nodeOperation(request: ScheduledJobStatusRequest): ScheduledJobStats {
        return createScheduledJobStatus(request.request)
    }

    private fun createScheduledJobStatus(
        scheduledJobsStatusRequest: ScheduledJobsStatsRequest
    ): ScheduledJobStats {
        val jobSweeperMetrics = jobSweeper.getJobSweeperMetrics()
        val jobSchedulerMetrics = jobScheduler.getJobSchedulerMetric()

        val status: ScheduledJobStats.ScheduleStatus = evaluateStatus(jobSchedulerMetrics, jobSweeperMetrics)
        return ScheduledJobStats(this.transportService.localNode,
                status,
                if (scheduledJobsStatusRequest.jobSchedulingMetrics) jobSweeperMetrics else null,
                if (scheduledJobsStatusRequest.jobsInfo) jobSchedulerMetrics.toTypedArray() else null)
    }

    private fun evaluateStatus(
        jobsInfo: List<JobSchedulerMetrics>,
        jobSweeperMetrics: JobSweeperMetrics
    ): ScheduledJobStats.ScheduleStatus {
        val allJobsRunningOnTime = jobsInfo.all { it.runningOnTime }
        if (allJobsRunningOnTime && jobSweeperMetrics.fullSweepOnTime) {
            return ScheduledJobStats.ScheduleStatus.GREEN
        }
        log.info("Jobs Running on time: $allJobsRunningOnTime, Sweeper on time: ${jobSweeperMetrics.fullSweepOnTime}")
        return ScheduledJobStats.ScheduleStatus.RED
    }

    class ScheduledJobStatusRequest : BaseNodeRequest {

        lateinit var request: ScheduledJobsStatsRequest

        constructor() : super()
        constructor(request: ScheduledJobsStatsRequest) : super() {
            this.request = request
        }

        @Throws(IOException::class)
        override fun readFrom(si: StreamInput) {
            super.readFrom(si)
            request = ScheduledJobsStatsRequest()
            request.readFrom(si)
        }

        @Throws(IOException::class)
        override fun writeTo(out: StreamOutput) {
            super.writeTo(out)
            request.writeTo(out)
        }
    }
}
