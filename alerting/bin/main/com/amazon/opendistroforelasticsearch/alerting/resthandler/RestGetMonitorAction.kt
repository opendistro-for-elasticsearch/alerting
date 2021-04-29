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
import com.amazon.opendistroforelasticsearch.alerting.action.GetMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetMonitorRequest
import com.amazon.opendistroforelasticsearch.alerting.util.context
import org.apache.logging.log4j.LogManager
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.GET
import org.elasticsearch.rest.RestRequest.Method.HEAD
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestToXContentListener
import org.elasticsearch.search.fetch.subphase.FetchSourceContext

private val log = LogManager.getLogger(RestGetMonitorAction::class.java)

/**
 * This class consists of the REST handler to retrieve a monitor .
 */
class RestGetMonitorAction : BaseRestHandler() {

    override fun getName(): String {
        return "get_monitor_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                // Get a specific monitor
                Route(GET, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}"),
                Route(HEAD, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}")
        )
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        log.debug("${request.method()} ${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}")

        val monitorId = request.param("monitorID")
        if (monitorId == null || monitorId.isEmpty()) {
            throw IllegalArgumentException("missing id")
        }

        var srcContext = context(request)
        if (request.method() == HEAD) {
            srcContext = FetchSourceContext.DO_NOT_FETCH_SOURCE
        }
        val getMonitorRequest = GetMonitorRequest(monitorId, RestActions.parseVersion(request), request.method(), srcContext)
        return RestChannelConsumer {
            channel -> client.execute(GetMonitorAction.INSTANCE, getMonitorRequest, RestToXContentListener(channel))
        }
    }
}
