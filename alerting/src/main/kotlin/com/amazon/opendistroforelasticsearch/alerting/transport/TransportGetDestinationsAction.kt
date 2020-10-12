/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.transport

import com.amazon.opendistroforelasticsearch.alerting.action.GetDestinationsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetDestinationsRequest
import com.amazon.opendistroforelasticsearch.alerting.action.GetDestinationsResponse
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.addFilter
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.util.AlertingException
import com.amazon.opendistroforelasticsearch.commons.authuser.AuthUserRequestBuilder
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.client.Response
import org.elasticsearch.client.ResponseListener
import org.elasticsearch.client.RestClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.Strings
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.Operator
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService
import java.io.IOException

private val log = LogManager.getLogger(TransportGetDestinationsAction::class.java)

class TransportGetDestinationsAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    val restClient: RestClient,
    clusterService: ClusterService,
    actionFilters: ActionFilters,
    val settings: Settings,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<GetDestinationsRequest, GetDestinationsResponse> (
        GetDestinationsAction.NAME, transportService, actionFilters, ::GetDestinationsRequest
) {

    @Volatile private var filterByEnabled = AlertingSettings.FILTER_BY_BACKEND_ROLES.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.FILTER_BY_BACKEND_ROLES) { filterByEnabled = it }
    }

    override fun doExecute(
        task: Task,
        getDestinationsRequest: GetDestinationsRequest,
        actionListener: ActionListener<GetDestinationsResponse>
    ) {

            val tableProp = getDestinationsRequest.table

            val sortBuilder = SortBuilders
                    .fieldSort(tableProp.sortString)
                    .order(SortOrder.fromString(tableProp.sortOrder))
            if (!tableProp.missing.isNullOrBlank()) {
                sortBuilder.missing(tableProp.missing)
            }

            val searchSourceBuilder = SearchSourceBuilder()
                    .sort(sortBuilder)
                    .size(tableProp.size)
                    .from(tableProp.startIndex)
                    .fetchSource(FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY))
                    .seqNoAndPrimaryTerm(true)
                    .version(true)
            val queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.existsQuery("destination"))

            if (!getDestinationsRequest.destinationId.isNullOrBlank())
                queryBuilder.filter(QueryBuilders.termQuery("_id", getDestinationsRequest.destinationId))

            if (getDestinationsRequest.destinationType != "ALL")
                queryBuilder.filter(QueryBuilders.termQuery("destination.type", getDestinationsRequest.destinationType))

            if (!tableProp.searchString.isNullOrBlank()) {
                queryBuilder
                        .must(QueryBuilders
                                .queryStringQuery(tableProp.searchString)
                                .defaultOperator(Operator.AND)
                                .field("destination.type")
                                .field("destination.name"))
            }
            searchSourceBuilder.query(queryBuilder)

            client.threadPool().threadContext.stashContext().use {
                resolve(getDestinationsRequest, searchSourceBuilder, actionListener)
            }
    }

    fun resolve(
        getDestinationsRequest: GetDestinationsRequest,
        searchSourceBuilder: SearchSourceBuilder,
        actionListener: ActionListener<GetDestinationsResponse>
    ) {
        if (getDestinationsRequest.authHeader.isNullOrEmpty()) {
            // auth header is null when: 1/ security is disabled. 2/when user is super-admin.
            search(searchSourceBuilder, actionListener)
        } else if (!filterByEnabled) {
            // security is enabled and filterby is disabled.
            search(searchSourceBuilder, actionListener)
        } else {
            // security is enabled and filterby is enabled.
            val authRequest = AuthUserRequestBuilder(
                    getDestinationsRequest.authHeader
            ).build()
            restClient.performRequestAsync(authRequest, object : ResponseListener {
                override fun onSuccess(response: Response) {
                    try {
                        val user = User(response)
                        addFilter(user, searchSourceBuilder, "destination.user.backend_roles")
                        log.info("Filtering result by: ${user.backendRoles}")
                        search(searchSourceBuilder, actionListener)
                    } catch (ex: IOException) {
                        actionListener.onFailure(AlertingException.wrap(ex))
                    }
                }

                override fun onFailure(ex: Exception) {
                    when (ex.message?.contains("Connection refused")) {
                        // Connection is refused when security plugin is not present. This case can happen only with integration tests.
                        true -> {
                            addFilter(User(), searchSourceBuilder, "destination.user.backend_roles")
                            search(searchSourceBuilder, actionListener)
                        }
                        false -> actionListener.onFailure(AlertingException.wrap(ex))
                    }
                }
            })
        }
    }

    fun search(searchSourceBuilder: SearchSourceBuilder, actionListener: ActionListener<GetDestinationsResponse>) {
        val searchRequest = SearchRequest()
                .source(searchSourceBuilder)
                .indices(ScheduledJob.SCHEDULED_JOBS_INDEX)
        client.search(searchRequest, object : ActionListener<SearchResponse> {
            override fun onResponse(response: SearchResponse) {
                val totalDestinationCount = response.hits.totalHits?.value?.toInt()
                val destinations = mutableListOf<Destination>()
                for (hit in response.hits) {
                    val id = hit.id
                    val version = hit.version
                    val seqNo = hit.seqNo.toInt()
                    val primaryTerm = hit.primaryTerm.toInt()
                    val xcp = XContentFactory.xContent(XContentType.JSON)
                            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, hit.sourceAsString)
                    XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                    XContentParserUtils.ensureExpectedToken(XContentParser.Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
                    XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                    destinations.add(Destination.parse(xcp, id, version, seqNo, primaryTerm))
                }
                actionListener.onResponse(GetDestinationsResponse(RestStatus.OK, totalDestinationCount, destinations))
            }

            override fun onFailure(t: Exception) {
                actionListener.onFailure(AlertingException.wrap(t))
            }
        })
    }
}
