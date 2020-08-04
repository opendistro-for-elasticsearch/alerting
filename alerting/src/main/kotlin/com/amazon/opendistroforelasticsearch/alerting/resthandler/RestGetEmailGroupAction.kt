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
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import com.amazon.opendistroforelasticsearch.alerting.util._ID
import com.amazon.opendistroforelasticsearch.alerting.util._PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util._SEQ_NO
import com.amazon.opendistroforelasticsearch.alerting.util.context
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import java.lang.IllegalArgumentException

/**
 * Rest handlers to retrieve an EmailGroup
 */
class RestGetEmailGroupAction : BaseRestHandler() {

    override fun getName(): String {
        return "get_email_group_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.GET, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/{emailGroupID}"),
                Route(RestRequest.Method.HEAD, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/{emailGroupID}")
        )
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val emailGroupID = request.param("emailGroupID")
        if (emailGroupID == null || emailGroupID.isEmpty()) {
            throw IllegalArgumentException("Missing email group ID")
        }

        val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, emailGroupID)
                .version(RestActions.parseVersion(request))
                .fetchSourceContext(context(request))

        if (request.method() == RestRequest.Method.HEAD) {
            getRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE)
        }

        return RestChannelConsumer { channel -> client.get(getRequest, getEmailGroupResponse(channel)) }
    }

    private fun getEmailGroupResponse(channel: RestChannel): RestResponseListener<GetResponse> {
        return object : RestResponseListener<GetResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: GetResponse): RestResponse {
                if (!response.isExists) {
                    return BytesRestResponse(RestStatus.NOT_FOUND, channel.newBuilder())
                }

                val builder = channel.newBuilder()
                        .startObject()
                        .field(_ID, response.id)
                        .field(_SEQ_NO, response.seqNo)
                        .field(_PRIMARY_TERM, response.primaryTerm)
                if (!response.isSourceEmpty) {
                    XContentHelper.createParser(channel.request().xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                            response.sourceAsBytesRef, XContentType.JSON).use { xcp ->
                                val emailGroup = EmailGroup.parseWithType(xcp, response.id)
                                builder.field("email_group", emailGroup)
                            }
                }
                builder.endObject()
                return BytesRestResponse(RestStatus.OK, builder)
            }
        }
    }
}
