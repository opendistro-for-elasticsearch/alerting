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
package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.DELETE
import org.elasticsearch.rest.action.RestStatusToXContentListener
import java.io.IOException

/**
 * This class consists of the REST handler to delete monitors.
 * When a monitor is deleted, all alerts are moved to the [Alert.State.DELETED] state and moved to the alert history index.
 * If this process fails the monitor is not deleted.
 */
class RestDeleteMonitorAction(settings: Settings, controller: RestController) :
        BaseRestHandler(settings) {

    init {
        controller.registerHandler(DELETE, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}", this) // Delete a monitor
    }

    override fun getName(): String {
        return "delete_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val monitorId = request.param("monitorID")
        val refreshPolicy = RefreshPolicy.parse(request.param(REFRESH, RefreshPolicy.IMMEDIATE.value))

        return RestChannelConsumer { channel ->
            val deleteRequest = DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE, monitorId)
                    .setRefreshPolicy(refreshPolicy)
            client.delete(deleteRequest, RestStatusToXContentListener(channel))
        }
    }
}
