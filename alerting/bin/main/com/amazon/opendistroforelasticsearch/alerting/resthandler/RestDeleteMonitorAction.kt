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

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteMonitorRequest
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.DELETE
import org.elasticsearch.rest.action.RestToXContentListener
import java.io.IOException

private val log: Logger = LogManager.getLogger(RestDeleteMonitorAction::class.java)
/**
 * This class consists of the REST handler to delete monitors.
 * When a monitor is deleted, all alerts are moved to the [Alert.State.DELETED] state and moved to the alert history index.
 * If this process fails the monitor is not deleted.
 */
class RestDeleteMonitorAction : BaseRestHandler() {

    override fun getName(): String {
        return "delete_monitor_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(DELETE, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}") // Delete a monitor
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        log.debug("${request.method()} ${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}")

        val monitorId = request.param("monitorID")
        log.debug("${request.method()} ${AlertingPlugin.MONITOR_BASE_URI}/$monitorId")

        val refreshPolicy = RefreshPolicy.parse(request.param(REFRESH, RefreshPolicy.IMMEDIATE.value))
        val deleteMonitorRequest = DeleteMonitorRequest(monitorId, refreshPolicy)

        return RestChannelConsumer { channel ->
            client.execute(DeleteMonitorAction.INSTANCE, deleteMonitorRequest, RestToXContentListener(channel))
        }
    }
}
