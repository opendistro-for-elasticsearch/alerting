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

import com.amazon.elasticsearch.ad.transport.StopDetectorAction
import com.amazon.elasticsearch.ad.transport.StopDetectorRequest
import com.amazon.elasticsearch.ad.transport.StopDetectorResponse
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.model.AnomalyDetectorInput
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.util.context
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.DELETE
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActionListener
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestStatusToXContentListener
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import java.io.IOException

private val log = LogManager.getLogger(RestDeleteMonitorAction::class.java)

/**
 * This class consists of the REST handler to delete monitors.
 * When a monitor is deleted, all alerts are moved to the [Alert.State.DELETED] state and moved to the alert history index.
 * If this process fails the monitor is not deleted.
 */
class RestDeleteMonitorAction(settings: Settings, controller: RestController) :
        BaseRestHandler(settings) {

    init {
        controller.registerHandler(DELETE, "${AlertingPlugin.MONITOR_BASE_URI}/{monitorID}", this) // Delete a monitor
    }

    override fun getName(): String {
        return "delete_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val monitorId = request.param("monitorID")
        val refreshPolicy = RefreshPolicy.parse(request.param(REFRESH, RefreshPolicy.IMMEDIATE.value))

        val getRequest = GetRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, monitorId)
                .version(RestActions.parseVersion(request))
                .fetchSourceContext(context(request))

        if (request.method() == RestRequest.Method.HEAD) {
            getRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE)
        }

        return RestChannelConsumer { channel -> client.get(getRequest, getMonitorResponse(channel, client, monitorId, refreshPolicy)) }
    }

    private fun getMonitorResponse(
        channel: RestChannel,
        client: NodeClient,
        monitorId: String,
        refreshPolicy: RefreshPolicy
    ): RestActionListener<GetResponse> {
        return object : RestActionListener<GetResponse>(channel) {
            @Throws(Exception::class)
            override fun processResponse(response: GetResponse) {
                if (!response.isExists) {
                    val message = channel.newErrorBuilder().startObject()
                            .field("message", "Can't find monitor with id:" + monitorId)
                            .endObject()
                    channel.sendResponse(BytesRestResponse(RestStatus.NOT_FOUND, message))
                    return
                }

                XContentHelper.createParser(channel.request().xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                        response.sourceAsBytesRef, XContentType.JSON).use { xcp ->
                    val monitor: Monitor = ScheduledJob.parse(xcp, response.id, response.version) as Monitor
                    val anomalyDetectorInput = monitor.inputs.firstOrNull { input -> input is AnomalyDetectorInput }
                    var anomalyDetectorDeleted = false

                    if (anomalyDetectorInput != null && anomalyDetectorInput is AnomalyDetectorInput) {
                        anomalyDetectorDeleted = deleteAnomalyDetector(client, anomalyDetectorInput.detectorId, monitorId)
                        if (!anomalyDetectorDeleted) {
                            channel.sendResponse(BytesRestResponse(RestStatus.EXPECTATION_FAILED,
                                    "Failed to delete anomaly detector's resources"))
                            return
                        }
                    }

                    val deleteRequest = DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, monitorId).setRefreshPolicy(refreshPolicy)
                    client.delete(deleteRequest, RestStatusToXContentListener(channel))
                }
            }
        }
    }

    // TODO: handle delete failure
    private fun deleteAnomalyDetector(client: NodeClient, detectorId: String, monitorId: String): Boolean {
        try {
            val request = StopDetectorRequest(detectorId)
            val future = client.execute(StopDetectorAction.INSTANCE, request)
            val result = StopDetectorResponse.fromActionResponse(future.actionGet())
            return result.success()
        } catch (e: Exception) { // TODO: add exception handling
            log.error("Fail to delete anomaly detector $detectorId for monitor $monitorId", e)
            return false
        }
    }
}
