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
package com.amazon.opendistro.alerting.resthandler

import com.amazon.opendistro.model.ScheduledJob
import com.amazon.opendistro.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistro.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.opendistro.alerting.MonitoringPlugin
import com.amazon.opendistro.alerting.util._ID
import com.amazon.opendistro.alerting.util._VERSION
import com.amazon.opendistro.alerting.util.context
import com.amazon.opendistro.util.ElasticAPI
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.GET
import org.elasticsearch.rest.RestRequest.Method.HEAD
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.search.fetch.subphase.FetchSourceContext

/**
 * This class consists of the REST handler to retrieve a monitor .
 */
class RestGetMonitorAction(settings: Settings, controller: RestController) : BaseRestHandler(settings) {

    init {
        // Get a specific monitor
        controller.registerHandler(GET, "${MonitoringPlugin.MONITOR_BASE_URI}/{monitorID}", this)
        controller.registerHandler(HEAD, "${MonitoringPlugin.MONITOR_BASE_URI}/{monitorID}", this)
    }

    override fun getName(): String {
        return "get_monitor_action"
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val monitorId = request.param("monitorID")
        if (monitorId == null || monitorId.isEmpty()) {
            throw IllegalArgumentException("missing id")
        }
        val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE, monitorId)
                .version(RestActions.parseVersion(request))
                .fetchSourceContext(context(request))
        if (request.method() == HEAD) {
            getRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE)
        }
        return RestChannelConsumer { channel -> client.get(getRequest, getMonitorResponse(channel)) }
    }

    private fun getMonitorResponse(channel: RestChannel): RestResponseListener<GetResponse> {
        return object : RestResponseListener<GetResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: GetResponse): RestResponse {
                if (!response.isExists) {
                    return BytesRestResponse(RestStatus.NOT_FOUND, channel.newBuilder())
                }

                val builder = channel.newBuilder()
                        .startObject()
                        .field(_ID, response.id)
                        .field(_VERSION, response.version)
                if (!response.isSourceEmpty) {
                    ElasticAPI.INSTANCE
                            .jsonParser(channel.request().xContentRegistry, response.sourceAsBytesRef).use { xcp ->
                                val monitor = ScheduledJob.parse(xcp, response.id, response.version)
                                builder.field("monitor", monitor)
                            }
                }
                builder.endObject()
                return BytesRestResponse(RestStatus.OK, builder)
            }
        }
    }
}

