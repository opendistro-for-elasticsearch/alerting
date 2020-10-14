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
import com.amazon.opendistroforelasticsearch.alerting.action.IndexMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexMonitorRequest
import com.amazon.opendistroforelasticsearch.alerting.action.IndexMonitorResponse
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.util.IF_PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util.IF_SEQ_NO
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.index.seqno.SequenceNumbers
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.POST
import org.elasticsearch.rest.RestRequest.Method.PUT
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException
import java.time.Instant

private val log = LogManager.getLogger(RestIndexMonitorAction::class.java)

/**
 * Rest handlers to create and update monitors.
 */
class RestIndexMonitorAction : BaseRestHandler() {

    override fun getName(): String {
        return "index_monitor_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(POST, AlertingPlugin.MONITOR_BASE_URI), // Create a new monitor
                Route(PUT, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        log.debug("${request.method()} ${AlertingPlugin.MONITOR_BASE_URI}")

        val id = request.param("monitorID", Monitor.NO_ID)
        if (request.method() == PUT && Monitor.NO_ID == id) {
            throw IllegalArgumentException("Missing monitor ID")
        }

        // Validate request by parsing JSON to Monitor
        val xcp = request.contentParser()
        ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        val monitor = Monitor.parse(xcp, id).copy(lastUpdateTime = Instant.now())
        val seqNo = request.paramAsLong(IF_SEQ_NO, SequenceNumbers.UNASSIGNED_SEQ_NO)
        val primaryTerm = request.paramAsLong(IF_PRIMARY_TERM, SequenceNumbers.UNASSIGNED_PRIMARY_TERM)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }
        val auth = request.header(ConfigConstants.AUTHORIZATION)
        val indexMonitorRequest = IndexMonitorRequest(id, seqNo, primaryTerm, refreshPolicy, request.method(), auth, monitor)

        return RestChannelConsumer { channel ->
            client.execute(IndexMonitorAction.INSTANCE, indexMonitorRequest, indexMonitorResponse(channel, request.method()))
        }
    }

    private fun indexMonitorResponse(channel: RestChannel, restMethod: RestRequest.Method):
            RestResponseListener<IndexMonitorResponse> {
        return object : RestResponseListener<IndexMonitorResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: IndexMonitorResponse): RestResponse {
                var returnStatus = RestStatus.CREATED
                if (restMethod == RestRequest.Method.PUT)
                    returnStatus = RestStatus.OK

                val restResponse = BytesRestResponse(returnStatus, response.toXContent(channel.newBuilder(), ToXContent.EMPTY_PARAMS))
                if (returnStatus == RestStatus.CREATED) {
                    val location = "${AlertingPlugin.MONITOR_BASE_URI}/${response.id}"
                    restResponse.addHeader("Location", location)
                }
                return restResponse
            }
        }
    }
}
