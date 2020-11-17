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

import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailGroupRequest
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailGroupResponse
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings.Companion.ALLOW_LIST
import com.amazon.opendistroforelasticsearch.alerting.util.AlertingException
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

private val log = LogManager.getLogger(TransportGetEmailGroupAction::class.java)

class TransportGetEmailGroupAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val clusterService: ClusterService,
    settings: Settings,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<GetEmailGroupRequest, GetEmailGroupResponse>(
    GetEmailGroupAction.NAME, transportService, actionFilters, ::GetEmailGroupRequest
) {

    @Volatile private var allowList = ALLOW_LIST.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(ALLOW_LIST) { allowList = it }
    }

    override fun doExecute(
        task: Task,
        getEmailGroupRequest: GetEmailGroupRequest,
        actionListener: ActionListener<GetEmailGroupResponse>
    ) {

        if (!allowList.contains(DestinationType.EMAIL.value)) {
            actionListener.onFailure(
                AlertingException.wrap(ElasticsearchStatusException(
                    "This API is blocked since Destination type [${DestinationType.EMAIL}] is not allowed",
                    RestStatus.FORBIDDEN
                ))
            )
            return
        }

        val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, getEmailGroupRequest.emailGroupID)
                .version(getEmailGroupRequest.version)
                .fetchSourceContext(getEmailGroupRequest.srcContext)
        client.threadPool().threadContext.stashContext().use {
            client.get(getRequest, object : ActionListener<GetResponse> {
                override fun onResponse(response: GetResponse) {
                    if (!response.isExists) {
                        actionListener.onFailure(AlertingException.wrap(
                            ElasticsearchStatusException("Email Group not found.", RestStatus.NOT_FOUND)
                        ))
                        return
                    }

                    var emailGroup: EmailGroup? = null
                    if (!response.isSourceEmpty) {
                        XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                                response.sourceAsBytesRef, XContentType.JSON).use { xcp ->
                            emailGroup = EmailGroup.parseWithType(xcp, response.id, response.version)
                        }
                    }

                    actionListener.onResponse(
                            GetEmailGroupResponse(response.id, response.version, response.seqNo, response.primaryTerm,
                                    RestStatus.OK, emailGroup)
                    )
                }

                override fun onFailure(e: Exception) {
                    actionListener.onFailure(e)
                }
            })
        }
    }
}
