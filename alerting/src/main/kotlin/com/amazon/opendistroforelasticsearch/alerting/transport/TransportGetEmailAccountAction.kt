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

import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailAccountRequest
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailAccountResponse
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

private val log = LogManager.getLogger(TransportGetEmailAccountAction::class.java)

class TransportGetEmailAccountAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<GetEmailAccountRequest, GetEmailAccountResponse>(
    GetEmailAccountAction.NAME, transportService, actionFilters, ::GetEmailAccountRequest
) {

    override fun doExecute(
        task: Task,
        getEmailAccountRequest: GetEmailAccountRequest,
        actionListener: ActionListener<GetEmailAccountResponse>
    ) {

        val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, getEmailAccountRequest.emailAccountID)
                .version(getEmailAccountRequest.version)
                .fetchSourceContext(getEmailAccountRequest.srcContext)

        client.get(getRequest, object : ActionListener<GetResponse> {
            override fun onResponse(response: GetResponse) {
                if (!response.isExists) {
                    actionListener.onFailure(ElasticsearchStatusException("Email Account not found.", RestStatus.NOT_FOUND))
                }

                var emailAccount: EmailAccount? = null
                if (!response.isSourceEmpty) {
                    XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                            response.sourceAsBytesRef, XContentType.JSON).use { xcp ->
                        emailAccount = EmailAccount.parseWithType(xcp, response.id)
                    }
                }

                actionListener.onResponse(
                    GetEmailAccountResponse(response.id, response.version, response.seqNo, response.primaryTerm,
                        RestStatus.OK, emailAccount)
                )
            }

            override fun onFailure(e: Exception) {
                actionListener.onFailure(e)
            }
        })
    }
}
