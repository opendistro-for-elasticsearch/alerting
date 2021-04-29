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

import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsRequest
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsResponse
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.addFilter
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.util.AlertingException
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.Operator
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService
import java.io.IOException

private val log = LogManager.getLogger(TransportGetAlertsAction::class.java)

class TransportGetAlertsAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    clusterService: ClusterService,
    actionFilters: ActionFilters,
    val settings: Settings,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<GetAlertsRequest, GetAlertsResponse>(
        GetAlertsAction.NAME, transportService, actionFilters, ::GetAlertsRequest
) {

    @Volatile private var filterByEnabled = AlertingSettings.FILTER_BY_BACKEND_ROLES.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.FILTER_BY_BACKEND_ROLES) { filterByEnabled = it }
    }

    override fun doExecute(
        task: Task,
        getAlertsRequest: GetAlertsRequest,
        actionListener: ActionListener<GetAlertsResponse>
    ) {
            val userStr = client.threadPool().threadContext.getTransient<String>(
                    ConfigConstants.OPENDISTRO_SECURITY_USER_INFO_THREAD_CONTEXT
            )
            log.debug("User and roles string from thread context: $userStr")
            val user: User? = User.parse(userStr)

            val tableProp = getAlertsRequest.table
            val sortBuilder = SortBuilders
                    .fieldSort(tableProp.sortString)
                    .order(SortOrder.fromString(tableProp.sortOrder))
            if (!tableProp.missing.isNullOrBlank()) {
                sortBuilder.missing(tableProp.missing)
            }

            val queryBuilder = QueryBuilders.boolQuery()

            if (getAlertsRequest.severityLevel != "ALL")
                queryBuilder.filter(QueryBuilders.termQuery("severity", getAlertsRequest.severityLevel))

            if (getAlertsRequest.alertState != "ALL")
                queryBuilder.filter(QueryBuilders.termQuery("state", getAlertsRequest.alertState))

            if (getAlertsRequest.monitorId != null) {
                queryBuilder.filter(QueryBuilders.termQuery("monitor_id", getAlertsRequest.monitorId))
            }

            if (!tableProp.searchString.isNullOrBlank()) {
                queryBuilder
                        .must(QueryBuilders
                                .queryStringQuery(tableProp.searchString)
                                .defaultOperator(Operator.AND)
                                .field("monitor_name")
                                .field("trigger_name"))
            }
            val searchSourceBuilder = SearchSourceBuilder()
                    .version(true)
                    .seqNoAndPrimaryTerm(true)
                    .query(queryBuilder)
                    .sort(sortBuilder)
                    .size(tableProp.size)
                    .from(tableProp.startIndex)

            client.threadPool().threadContext.stashContext().use {
                resolve(searchSourceBuilder, actionListener, user)
            }
        }

        fun resolve(
            searchSourceBuilder: SearchSourceBuilder,
            actionListener: ActionListener<GetAlertsResponse>,
            user: User?
        ) {
            // user is null when: 1/ security is disabled. 2/when user is super-admin.
            if (user == null) {
                // user is null when: 1/ security is disabled. 2/when user is super-admin.
                search(searchSourceBuilder, actionListener)
            } else if (!filterByEnabled) {
                // security is enabled and filterby is disabled.
                search(searchSourceBuilder, actionListener)
            } else {
                // security is enabled and filterby is enabled.
                try {
                    log.info("Filtering result by: ${user.backendRoles}")
                    addFilter(user, searchSourceBuilder, "monitor_user.backend_roles.keyword")
                    search(searchSourceBuilder, actionListener)
                } catch (ex: IOException) {
                    actionListener.onFailure(AlertingException.wrap(ex))
                }
            }
        }

        fun search(searchSourceBuilder: SearchSourceBuilder, actionListener: ActionListener<GetAlertsResponse>) {
            val searchRequest = SearchRequest()
                    .indices(AlertIndices.ALL_INDEX_PATTERN)
                    .source(searchSourceBuilder)

            client.search(searchRequest, object : ActionListener<SearchResponse> {
                override fun onResponse(response: SearchResponse) {
                    val totalAlertCount = response.hits.totalHits?.value?.toInt()
                    val alerts = response.hits.map { hit ->
                        val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                                hit.sourceRef, XContentType.JSON)
                        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp)
                        val alert = Alert.parse(xcp, hit.id, hit.version)
                        alert
                    }
                    actionListener.onResponse(GetAlertsResponse(alerts, totalAlertCount))
                }

                override fun onFailure(t: Exception) {
                    actionListener.onFailure(t)
                }
            })
        }
}
