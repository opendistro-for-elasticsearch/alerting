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
import com.amazon.opendistroforelasticsearch.alerting.action.SearchEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import com.amazon.opendistroforelasticsearch.alerting.util.context
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.IOException

/**
 * Rest handlers to search for EmailGroup
 */
class RestSearchEmailGroupAction : BaseRestHandler() {

    override fun getName(): String {
        return "search_email_group_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.POST, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/_search"),
                Route(RestRequest.Method.GET, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/_search")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.parseXContent(request.contentOrSourceParamParser())
        searchSourceBuilder.fetchSource(context(request))

        // An exists query is added on top of the user's query to ensure that only documents of email_group type
        // are searched
        searchSourceBuilder.query(QueryBuilders.boolQuery().must(searchSourceBuilder.query())
                .filter(QueryBuilders.existsQuery(EmailGroup.EMAIL_GROUP_TYPE)))
                .seqNoAndPrimaryTerm(true)
        val searchRequest = SearchRequest()
                .source(searchSourceBuilder)
                .indices(SCHEDULED_JOBS_INDEX)
        return RestChannelConsumer { channel ->
            client.execute(SearchEmailGroupAction.INSTANCE, searchRequest, searchEmailGroupResponse(channel))
        }
    }

    private fun searchEmailGroupResponse(channel: RestChannel): RestResponseListener<SearchResponse> {
        return object : RestResponseListener<SearchResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: SearchResponse): RestResponse {
                if (response.isTimedOut) {
                    return BytesRestResponse(RestStatus.REQUEST_TIMEOUT, response.toString())
                }

                for (hit in response.hits) {
                    XContentType.JSON.xContent().createParser(channel.request().xContentRegistry,
                            LoggingDeprecationHandler.INSTANCE, hit.sourceAsString).use { hitsParser ->
                                val emailGroup = EmailGroup.parseWithType(hitsParser, hit.id, hit.version)
                                val xcb = emailGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS)
                                hit.sourceRef(BytesReference.bytes(xcb))
                            }
                }
                return BytesRestResponse(RestStatus.OK, response.toXContent(channel.newBuilder(), ToXContent.EMPTY_PARAMS))
            }
        }
    }
}
