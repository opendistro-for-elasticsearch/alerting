package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount
import com.amazon.opendistroforelasticsearch.alerting.util.context
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
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
 * Rest handlers to search for EmailAccount
 */
class RestSearchEmailAccountAction : BaseRestHandler() {

    override fun getName(): String {
        return "search_email_account_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.POST, "${AlertingPlugin.EMAIL_ACCOUNT_BASE_URI}/_search"),
                Route(RestRequest.Method.GET, "${AlertingPlugin.EMAIL_ACCOUNT_BASE_URI}/_search")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.parseXContent(request.contentOrSourceParamParser())
        searchSourceBuilder.fetchSource(context(request))

        // A term query is added on top of the user's query to ensure that only documents of email_account type
        // are searched
        searchSourceBuilder.query(QueryBuilders.boolQuery().must(searchSourceBuilder.query())
                .filter(QueryBuilders.termQuery(EmailAccount.EMAIL_ACCOUNT_TYPE + ".type", EmailAccount.EMAIL_ACCOUNT_TYPE)))
                .seqNoAndPrimaryTerm(true)
        val searchRequest = SearchRequest()
                .source(searchSourceBuilder)
                .indices(SCHEDULED_JOBS_INDEX)
        return RestChannelConsumer { channel -> client.search(searchRequest, searchEmailAccountResponse(channel)) }
    }

    private fun searchEmailAccountResponse(channel: RestChannel): RestResponseListener<SearchResponse> {
        return object : RestResponseListener<SearchResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: SearchResponse): RestResponse {
                if (response.isTimedOut) {
                    return BytesRestResponse(RestStatus.REQUEST_TIMEOUT, response.toString())
                }

                for (hit in response.hits) {
                    XContentType.JSON.xContent().createParser(channel.request().xContentRegistry,
                            LoggingDeprecationHandler.INSTANCE, hit.sourceAsString).use { hitsParser ->
                                val emailAccount = ScheduledJob.parse(hitsParser, hit.id)
                                val xcb = emailAccount.toXContent(jsonBuilder(), EMPTY_PARAMS)
                                hit.sourceRef(BytesReference.bytes(xcb))
                            }
                }
                return BytesRestResponse(RestStatus.OK, response.toXContent(channel.newBuilder(), EMPTY_PARAMS))
            }
        }
    }
}
