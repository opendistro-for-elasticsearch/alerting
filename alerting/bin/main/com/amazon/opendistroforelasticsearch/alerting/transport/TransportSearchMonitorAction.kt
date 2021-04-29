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

import com.amazon.opendistroforelasticsearch.alerting.action.SearchMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.SearchMonitorRequest
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.addFilter
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
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

private val log = LogManager.getLogger(TransportSearchMonitorAction::class.java)

class TransportSearchMonitorAction @Inject constructor(
    transportService: TransportService,
    val settings: Settings,
    val client: Client,
    clusterService: ClusterService,
    actionFilters: ActionFilters
) : HandledTransportAction<SearchMonitorRequest, SearchResponse>(
        SearchMonitorAction.NAME, transportService, actionFilters, ::SearchMonitorRequest
) {
    @Volatile private var filterByEnabled = AlertingSettings.FILTER_BY_BACKEND_ROLES.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.FILTER_BY_BACKEND_ROLES) { filterByEnabled = it }
    }

    override fun doExecute(task: Task, searchMonitorRequest: SearchMonitorRequest, actionListener: ActionListener<SearchResponse>) {
        val userStr = client.threadPool().threadContext.getTransient<String>(ConfigConstants.OPENDISTRO_SECURITY_USER_INFO_THREAD_CONTEXT)
        log.debug("User and roles string from thread context: $userStr")
        val user: User? = User.parse(userStr)

        client.threadPool().threadContext.stashContext().use {
            resolve(searchMonitorRequest, actionListener, user)
        }
    }

    fun resolve(searchMonitorRequest: SearchMonitorRequest, actionListener: ActionListener<SearchResponse>, user: User?) {
        if (user == null) {
            // user header is null when: 1/ security is disabled. 2/when user is super-admin.
            search(searchMonitorRequest.searchRequest, actionListener)
        } else if (!filterByEnabled) {
            // security is enabled and filterby is disabled.
            search(searchMonitorRequest.searchRequest, actionListener)
        } else {
            // security is enabled and filterby is enabled.
            log.info("Filtering result by: ${user.backendRoles}")
            addFilter(user, searchMonitorRequest.searchRequest.source(), "monitor.user.backend_roles.keyword")
            search(searchMonitorRequest.searchRequest, actionListener)
        }
    }

    fun search(searchRequest: SearchRequest, actionListener: ActionListener<SearchResponse>) {
        client.search(searchRequest, object : ActionListener<SearchResponse> {
            override fun onResponse(response: SearchResponse) {
                actionListener.onResponse(response)
            }

            override fun onFailure(t: Exception) {
                actionListener.onFailure(AlertingException.wrap(t))
            }
        })
    }
}
