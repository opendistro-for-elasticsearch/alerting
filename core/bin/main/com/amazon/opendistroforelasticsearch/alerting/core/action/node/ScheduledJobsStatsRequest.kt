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

    constructor(si: StreamInput): super(si) {
        jobSchedulingMetrics = si.readBoolean()
        jobsInfo = si.readBoolean()
    }
    constructor(nodeIds: Array<String>) : super(*nodeIds)

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

    fun clear(): ScheduledJobsStatsRequest {
        jobSchedulingMetrics = false
        jobsInfo = false
        return this
    }
}
