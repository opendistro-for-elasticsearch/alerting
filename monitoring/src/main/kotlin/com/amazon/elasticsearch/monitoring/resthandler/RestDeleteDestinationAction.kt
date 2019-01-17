package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import com.amazon.elasticsearch.monitoring.util.REFRESH
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.action.RestStatusToXContentListener
import java.io.IOException


/**
 * This class consists of the REST handler to delete destination.
 */
class RestDeleteDestinationAction(settings: Settings, controller: RestController) :
        BaseRestHandler(settings) {

    init {
        controller.registerHandler(RestRequest.Method.DELETE, MonitoringPlugin.DESTINATION_BASE_URI + "{destinationID}", this)
    }

    override fun getName(): String {
        return "delete_destination_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val destinationId = request.param("destinationID")
        val refreshPolicy = WriteRequest.RefreshPolicy.parse(request.param(REFRESH, WriteRequest.RefreshPolicy.IMMEDIATE.value))

        return RestChannelConsumer { channel ->
                val deleteDestinationRequest =
                        DeleteRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE, destinationId)
                                .setRefreshPolicy(refreshPolicy)
                client.delete(deleteDestinationRequest, RestStatusToXContentListener(channel))
        }
    }
}