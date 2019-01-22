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

package com.amazon.opendistro.action.node

import org.elasticsearch.action.Action
import org.elasticsearch.client.ElasticsearchClient

class ScheduledJobsStatsAction : Action<ScheduledJobsStatsRequest, ScheduledJobsStatsResponse, ScheduledJobsStatsRequestBuilder>(NAME) {
    companion object {
        val INSTANCE = ScheduledJobsStatsAction()
        const val NAME = "cluster:admin/opendistro/_scheduled_jobs/stats"
    }

    override fun newRequestBuilder(client: ElasticsearchClient): ScheduledJobsStatsRequestBuilder {
        return ScheduledJobsStatsRequestBuilder(client, this)
    }

    override fun newResponse(): ScheduledJobsStatsResponse {
        return ScheduledJobsStatsResponse()
    }
}