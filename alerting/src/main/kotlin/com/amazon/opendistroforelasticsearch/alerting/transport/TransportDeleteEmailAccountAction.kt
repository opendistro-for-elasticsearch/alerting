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

import com.amazon.opendistroforelasticsearch.alerting.action.DeleteEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteEmailAccountRequest
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings.Companion.ALLOW_LIST
import com.amazon.opendistroforelasticsearch.alerting.util.AlertingException
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

class TransportDeleteEmailAccountAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val clusterService: ClusterService,
    settings: Settings
) : HandledTransportAction<DeleteEmailAccountRequest, DeleteResponse>(
    DeleteEmailAccountAction.NAME, transportService, actionFilters, ::DeleteEmailAccountRequest
) {

    @Volatile private var allowList = ALLOW_LIST.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALLOW_LIST) { allowList = it }
    }

    override fun doExecute(task: Task, request: DeleteEmailAccountRequest, actionListener: ActionListener<DeleteResponse>) {

        if (!allowList.contains(DestinationType.EMAIL.value)) {
            actionListener.onFailure(
                AlertingException.wrap(ElasticsearchStatusException(
                    "This API is blocked since Destination type [${DestinationType.EMAIL}] is not allowed",
                    RestStatus.FORBIDDEN
                ))
            )
            return
        }

        val deleteRequest = DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, request.emailAccountID)
                .setRefreshPolicy(request.refreshPolicy)
        client.threadPool().threadContext.stashContext().use {
            client.delete(deleteRequest, object : ActionListener<DeleteResponse> {
                override fun onResponse(response: DeleteResponse) {
                    actionListener.onResponse(response)
                }

                override fun onFailure(t: Exception) {
                    actionListener.onFailure(t)
                }
            })
        }
    }
}
