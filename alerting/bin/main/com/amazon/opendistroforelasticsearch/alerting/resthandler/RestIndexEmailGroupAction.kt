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
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailGroupRequest
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailGroupResponse
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
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
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException

private val log = LogManager.getLogger(RestIndexEmailGroupAction::class.java)

/**
 * Rest handlers to create and update EmailGroup.
 */
class RestIndexEmailGroupAction : BaseRestHandler() {

    override fun getName(): String {
        return "index_email_group_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.POST, AlertingPlugin.EMAIL_GROUP_BASE_URI), // Creates new email group
                Route(RestRequest.Method.PUT, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/{emailGroupID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val id = request.param("emailGroupID", EmailGroup.NO_ID)
        if (request.method() == RestRequest.Method.PUT && EmailGroup.NO_ID == id) {
            throw IllegalArgumentException("Missing email group ID")
        }

        // Validate request by parsing JSON to EmailGroup
        val xcp = request.contentParser()
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp)
        val emailGroup = EmailGroup.parse(xcp, id)
        val seqNo = request.paramAsLong(IF_SEQ_NO, SequenceNumbers.UNASSIGNED_SEQ_NO)
        val primaryTerm = request.paramAsLong(IF_PRIMARY_TERM, SequenceNumbers.UNASSIGNED_PRIMARY_TERM)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }

        val indexEmailGroupRequest = IndexEmailGroupRequest(id, seqNo, primaryTerm, refreshPolicy, request.method(), emailGroup)

        return RestChannelConsumer { channel ->
            client.execute(IndexEmailGroupAction.INSTANCE, indexEmailGroupRequest, indexEmailGroupResponse(channel, request.method()))
        }
    }

    private fun indexEmailGroupResponse(channel: RestChannel, restMethod: RestRequest.Method):
            RestResponseListener<IndexEmailGroupResponse> {
        return object : RestResponseListener<IndexEmailGroupResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: IndexEmailGroupResponse): RestResponse {
                var returnStatus = RestStatus.CREATED
                if (restMethod == RestRequest.Method.PUT)
                    returnStatus = RestStatus.OK

                val restResponse = BytesRestResponse(returnStatus, response.toXContent(channel.newBuilder(), ToXContent.EMPTY_PARAMS))
                if (returnStatus == RestStatus.CREATED) {
                    val location = "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/${response.id}"
                    restResponse.addHeader("Location", location)
                }

                return restResponse
            }
        }
    }
}
