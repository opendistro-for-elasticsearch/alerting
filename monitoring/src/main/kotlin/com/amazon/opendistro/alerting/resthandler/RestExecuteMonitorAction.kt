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

package com.amazon.opendistro.alerting.resthandler

import com.amazon.opendistro.model.ScheduledJob
import com.amazon.opendistro.alerting.MonitorRunner
import com.amazon.opendistro.alerting.MonitoringPlugin
import com.amazon.opendistro.alerting.model.Monitor
import com.amazon.opendistro.util.ElasticAPI
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActionListener
import java.time.Instant

class RestExecuteMonitorAction(val settings: Settings, restController: RestController,
                               private val runner: MonitorRunner) : BaseRestHandler(settings) {

    init {
        restController.registerHandler(POST, "${MonitoringPlugin.MONITOR_BASE_URI}/{monitorID}/_execute", this)
        restController.registerHandler(POST, "${MonitoringPlugin.MONITOR_BASE_URI}/_execute", this)
    }

    override fun getName(): String = "execute_monitor_action"

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        return RestChannelConsumer { channel ->
            val dryrun = request.paramAsBoolean("dryrun", false)
            val requestEnd = request.paramAsTime("period_end", TimeValue(Instant.now().toEpochMilli()))

            val executeMonitor = fun(monitor: Monitor) {
                runner.executor().submit {
                    val (periodStart, periodEnd) =
                            monitor.schedule.getPeriodEndingAt(Instant.ofEpochMilli(requestEnd.millis))
                    try {
                        val response = runner.runMonitor(monitor, periodStart, periodEnd, dryrun)
                        channel.sendResponse(BytesRestResponse(RestStatus.OK, channel.newBuilder().value(response)))
                    } catch (e: Exception) {
                        logger.error("Unexpected error running monitor", e)
                        channel.sendResponse(BytesRestResponse(channel, e))
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

    private fun processGetResponse(channel: RestChannel, block : (Monitor) -> Unit) : RestActionListener<GetResponse> {
        return object : RestActionListener<GetResponse>(channel) {

            override fun processResponse(response: GetResponse) {
                if (!response.isExists) {
                    val ret = this.channel.newErrorBuilder().startObject()
                            .field("message", "Can't find monitor with id: ${response.id}")
                            .endObject()
                    this.channel.sendResponse(BytesRestResponse(RestStatus.NOT_FOUND, ret))
                }

                val xcp = ElasticAPI.INSTANCE.createParser(this.channel.request().xContentRegistry,
                        response.sourceAsBytesRef, this.channel.request().xContentType ?: XContentType.JSON)
                val monitor = xcp.use {
                    ScheduledJob.parse(xcp, response.id, response.version) as Monitor
                }

                block(monitor)
            }
        }
    }
}