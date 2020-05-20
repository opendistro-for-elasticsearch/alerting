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

import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.action.RestStatusToXContentListener
import java.io.IOException

/**
 * This class consists of the REST handler to delete destination.
 */
class RestDeleteDestinationAction : BaseRestHandler() {

    override fun getName(): String {
        return "delete_destination_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.DELETE, "${AlertingPlugin.DESTINATION_BASE_URI}/{destinationID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val destinationId = request.param("destinationID")
        val refreshPolicy = WriteRequest.RefreshPolicy.parse(request.param(REFRESH, WriteRequest.RefreshPolicy.IMMEDIATE.value))

        return RestChannelConsumer { channel ->
                val deleteDestinationRequest =
                        DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, destinationId)
                                .setRefreshPolicy(refreshPolicy)
                client.delete(deleteDestinationRequest, RestStatusToXContentListener(channel))
        }
    }
}
