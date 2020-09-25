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
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.DeleteEmailAccountRequest
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.action.RestToXContentListener
import java.io.IOException

private val log: Logger = LogManager.getLogger(RestDeleteEmailAccountAction::class.java)

/**
 * Rest handler to delete EmailAccount.
 */
class RestDeleteEmailAccountAction : BaseRestHandler() {

    override fun getName(): String {
        return "delete_email_account_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.DELETE, "${AlertingPlugin.EMAIL_ACCOUNT_BASE_URI}/{emailAccountID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val emailAccountID = request.param("emailAccountID")
        log.debug("${request.method()} ${AlertingPlugin.EMAIL_ACCOUNT_BASE_URI}/$emailAccountID")

        val refreshPolicy = WriteRequest.RefreshPolicy.parse(request.param(REFRESH, WriteRequest.RefreshPolicy.IMMEDIATE.value))
        val deleteEmailAccountRequest = DeleteEmailAccountRequest(emailAccountID, refreshPolicy)

        return RestChannelConsumer { channel ->
            client.execute(DeleteEmailAccountAction.INSTANCE, deleteEmailAccountRequest, RestToXContentListener(channel))
        }
    }
}
