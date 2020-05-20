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
import com.amazon.opendistroforelasticsearch.alerting.MonitorRunner
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.ElasticThreadContextElement
import org.apache.logging.log4j.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActionListener
import java.time.Instant

private val log = LogManager.getLogger(RestExecuteMonitorAction::class.java)

class RestExecuteMonitorAction(
    val settings: Settings,
    private val runner: MonitorRunner
) : BaseRestHandler() {

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

            val executeMonitor = fun(monitor: Monitor) {
                // Launch the coroutine with the clients threadContext. This is needed to preserve authentication information
                // stored on the threadContext set by the security plugin when using the Alerting plugin with the Security plugin.
                runner.launch(ElasticThreadContextElement(client.threadPool().threadContext)) {
                    val (periodStart, periodEnd) =
                            monitor.schedule.getPeriodEndingAt(Instant.ofEpochMilli(requestEnd.millis))
                    try {
                        val response = runner.runMonitor(monitor, periodStart, periodEnd, dryrun)
                        withContext(Dispatchers.IO) {
                            channel.sendResponse(BytesRestResponse(RestStatus.OK, channel.newBuilder().value(response)))
                        }
                    } catch (e: Exception) {
                        log.error("Unexpected error running monitor", e)
                        withContext(Dispatchers.IO) { channel.sendResponse(BytesRestResponse(channel, e)) }
                    }
                }
            }

            if (request.hasParam("monitorID")) {
                val getRequest = GetRequest(ScheduledJob.SCHEDULED_JOBS_INDEX).id(request.param("monitorID"))
                client.get(getRequest, processGetResponse(channel, executeMonitor))
            } else {
                val xcp = request.contentParser()
                ensureExpectedToken(START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                executeMonitor(Monitor.parse(xcp, Monitor.NO_ID, Monitor.NO_VERSION))
            }
        }
    }

    override fun responseParams(): Set<String> {
        return setOf("dryrun", "period_end", "monitorID")
    }

    private fun processGetResponse(channel: RestChannel, block: (Monitor) -> Unit): RestActionListener<GetResponse> {
        return object : RestActionListener<GetResponse>(channel) {

            override fun processResponse(response: GetResponse) {
                if (!response.isExists) {
                    val ret = this.channel.newErrorBuilder().startObject()
                            .field("message", "Can't find monitor with id: ${response.id}")
                            .endObject()
                    this.channel.sendResponse(BytesRestResponse(RestStatus.NOT_FOUND, ret))
                }

                val xcp = (this.channel.request().xContentType ?: XContentType.JSON).xContent()
                        .createParser(this.channel.request().xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                                response.sourceAsBytesRef.streamInput())
                val monitor = xcp.use {
                    ScheduledJob.parse(xcp, response.id, response.version) as Monitor
                }

                block(monitor)
            }
        }
    }
}
