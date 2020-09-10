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
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetEmailGroupRequest
import com.amazon.opendistroforelasticsearch.alerting.util.context
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestToXContentListener
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import java.lang.IllegalArgumentException

/**
 * Rest handlers to retrieve an EmailGroup
 */
class RestGetEmailGroupAction : BaseRestHandler() {

    override fun getName(): String {
        return "get_email_group_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.GET, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/{emailGroupID}"),
                Route(RestRequest.Method.HEAD, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/{emailGroupID}")
        )
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val emailGroupID = request.param("emailGroupID")
        if (emailGroupID == null || emailGroupID.isEmpty()) {
            throw IllegalArgumentException("Missing email group ID")
        }

        var srcContext = context(request)
        if (request.method() == RestRequest.Method.HEAD) {
            srcContext = FetchSourceContext.DO_NOT_FETCH_SOURCE
        }

        val getEmailGroupRequest = GetEmailGroupRequest(emailGroupID, RestActions.parseVersion(request), request.method(), srcContext)
        return RestChannelConsumer { channel ->
            client.execute(GetEmailGroupAction.INSTANCE, getEmailGroupRequest, RestToXContentListener(channel))
        }
    }
}
