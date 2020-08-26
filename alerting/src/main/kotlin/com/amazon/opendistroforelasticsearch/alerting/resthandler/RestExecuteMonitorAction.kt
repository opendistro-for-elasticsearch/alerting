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
import com.amazon.opendistroforelasticsearch.alerting.action.ExecuteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.ExecuteMonitorRequest
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.action.RestToXContentListener
import java.time.Instant

class RestExecuteMonitorAction : BaseRestHandler() {

    override fun getName(): String = "execute_monitor_action"

    override fun routes(): List<Route> {
        return listOf(
                Route(POST, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}/_execute"),
                Route(POST, "${AlertingPlugin.MONITOR_BASE_URI}/_execute")
        )
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        return RestChannelConsumer { channel ->
            val dryrun = request.paramAsBoolean("dryrun", false)
            val requestEnd = request.paramAsTime("period_end", TimeValue(Instant.now().toEpochMilli()))

            if (request.hasParam("monitorID")) {
                val monitorId = request.param("monitorID")
                val execMonitorRequest = ExecuteMonitorRequest(dryrun, requestEnd, monitorId, null)
                client.execute(ExecuteMonitorAction.INSTANCE, execMonitorRequest, RestToXContentListener(channel))
            } else {
                val xcp = request.contentParser()
                ensureExpectedToken(START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                val monitor = Monitor.parse(xcp, Monitor.NO_ID, Monitor.NO_VERSION)
                val execMonitorRequest = ExecuteMonitorRequest(dryrun, requestEnd, null, monitor)
                client.execute(ExecuteMonitorAction.INSTANCE, execMonitorRequest, RestToXContentListener(channel))
            }
        }
    }

    override fun responseParams(): Set<String> {
        return setOf("dryrun", "period_end", "monitorID")
    }
}
