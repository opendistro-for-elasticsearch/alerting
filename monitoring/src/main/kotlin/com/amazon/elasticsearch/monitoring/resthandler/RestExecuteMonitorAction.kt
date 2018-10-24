/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.monitoring.MonitorRunner
import com.amazon.elasticsearch.monitoring.model.Monitor
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
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

class RestExecuteMonitorAction(val settings: Settings, val restController: RestController,
                               val runner: MonitorRunner) : BaseRestHandler(settings) {

    init {
        // TODO: Add support for executing exising monitors
        restController.registerHandler(POST, "/_awses/monitors/{monitorID}/_execute", this)
        restController.registerHandler(POST, "/_awses/monitors/_execute", this)
    }

    override fun getName(): String = "execute_monitor_action"

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        return RestChannelConsumer { channel ->
            val dryrun = request.paramAsBoolean("dryrun", false)
            val requestEnd = request.paramAsTime("period_end", TimeValue(client.threadPool().absoluteTimeInMillis()))

            val executeMonitor = fun(monitor: Monitor) {
                client.threadPool().generic().submit {
                    val (periodStart, periodEnd) =
                            monitor.schedule.getPeriodStartEnd(Instant.ofEpochMilli(requestEnd.millis))
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

                val xcp = XContentHelper.createParser(this.channel.request().xContentRegistry,
                        response.sourceAsBytesRef, this.channel.request().xContentType)
                val monitor = xcp.use {
                    ScheduledJob.parse(xcp, response.id, response.version) as Monitor
                }

                block(monitor)
            }
        }
    }
}