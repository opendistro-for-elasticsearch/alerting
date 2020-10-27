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
import com.amazon.opendistroforelasticsearch.alerting.action.IndexDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexDestinationRequest
import com.amazon.opendistroforelasticsearch.alerting.action.IndexDestinationResponse
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.util.IF_PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util.IF_SEQ_NO
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.index.seqno.SequenceNumbers
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException

private val log = LogManager.getLogger(RestIndexDestinationAction::class.java)

/**
 * Rest handlers to create and update Destination
 */
class RestIndexDestinationAction : BaseRestHandler() {

    override fun getName(): String {
        return "index_destination_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.POST, AlertingPlugin.DESTINATION_BASE_URI), // Creates new destination
                Route(RestRequest.Method.PUT, "${AlertingPlugin.DESTINATION_BASE_URI}/{destinationID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer {
        log.debug("${request.method()} ${AlertingPlugin.DESTINATION_BASE_URI}")

        val id = request.param("destinationID", Destination.NO_ID)
        if (request.method() == RestRequest.Method.PUT && Destination.NO_ID == id) {
            throw IllegalArgumentException("Missing destination ID")
        }

        // Validate request by parsing JSON to Destination
        val xcp = request.contentParser()
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        val destination = Destination.parse(xcp, id)
        val seqNo = request.paramAsLong(IF_SEQ_NO, SequenceNumbers.UNASSIGNED_SEQ_NO)
        val primaryTerm = request.paramAsLong(IF_PRIMARY_TERM, SequenceNumbers.UNASSIGNED_PRIMARY_TERM)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }
        val indexDestinationRequest = IndexDestinationRequest(id, seqNo, primaryTerm, refreshPolicy, request.method(), destination)
        return RestChannelConsumer {
            channel -> client.execute(IndexDestinationAction.INSTANCE, indexDestinationRequest,
                indexDestinationResponse(channel, request.method()))
        }
    }

    private fun indexDestinationResponse(channel: RestChannel, restMethod: RestRequest.Method):
            RestResponseListener<IndexDestinationResponse> {
        return object : RestResponseListener<IndexDestinationResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: IndexDestinationResponse): RestResponse {
                var returnStatus = RestStatus.CREATED
                if (restMethod == RestRequest.Method.PUT)
                    returnStatus = RestStatus.OK

                val restResponse = BytesRestResponse(returnStatus, response.toXContent(channel.newBuilder(), ToXContent.EMPTY_PARAMS))
                if (returnStatus == RestStatus.CREATED) {
                    val location = "${AlertingPlugin.DESTINATION_BASE_URI}/${response.id}"
                    restResponse.addHeader("Location", location)
                }
                return restResponse
            }
        }
    }
}
