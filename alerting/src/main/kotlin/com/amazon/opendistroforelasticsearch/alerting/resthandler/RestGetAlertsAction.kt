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

package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsRequest
import com.amazon.opendistroforelasticsearch.alerting.model.Table
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants
import com.amazon.opendistroforelasticsearch.commons.authuser.AuthUser
import org.apache.logging.log4j.LogManager
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.TermsQueryBuilder
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.GET
import org.elasticsearch.rest.action.RestToXContentListener

/**
 * This class consists of the REST handler to retrieve alerts .
 */
class RestGetAlertsAction(
    val settings: Settings,
    clusterService: ClusterService,
    private val restClient: RestClient
) : BaseRestHandler() {

    private val log = LogManager.getLogger(RestGetAlertsAction::class.java)
    @Volatile private var filterBy = AlertingSettings.FILTER_BY_BACKEND_ROLES.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.FILTER_BY_BACKEND_ROLES) { filterBy = it }
    }

    override fun getName(): String {
        return "get_alerts_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(GET, "${AlertingPlugin.MONITOR_BASE_URI}/alerts")
        )
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        log.debug("${request.method()} ${AlertingPlugin.MONITOR_BASE_URI}/alerts")

        val sortString = request.param("sortString", "monitor_name.keyword")
        val sortOrder = request.param("sortOrder", "asc")
        val missing: String? = request.param("missing")
        val size = request.paramAsInt("size", 20)
        val startIndex = request.paramAsInt("startIndex", 0)
        val searchString = request.param("searchString", "")
        val severityLevel = request.param("severityLevel", "ALL")
        val alertState = request.param("alertState", "ALL")
        val monitorId: String? = request.param("monitorId")

        val table = Table(
                sortOrder,
                sortString,
                missing,
                size,
                startIndex,
                searchString
        )
        var filter: TermsQueryBuilder? = null
        if (filterBy) {
            val user = AuthUser(settings, restClient, request.headers[ConfigConstants.AUTHORIZATION]).get()
            if (user != null) {
                filter = QueryBuilders.termsQuery("monitor_user.backend_roles", user.backendRoles)
            }
        }
        val getAlertsRequest = GetAlertsRequest(table, severityLevel, alertState, monitorId, filter)
        return RestChannelConsumer {
            channel -> client.execute(GetAlertsAction.INSTANCE, getAlertsRequest, RestToXContentListener(channel))
        }
    }
}
