/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler

import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.action.RestResponseListener

import java.io.IOException

import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.elasticsearch.monitoring.util.REFRESH
import org.elasticsearch.rest.RestRequest.Method.DELETE

/**
 * This class consists of the REST handler to delete polices
 */
class RestDeleteMonitorAction(settings: Settings, controller: RestController) : BaseRestHandler(settings) {

    init {
        controller.registerHandler(DELETE, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this) // Delete a monitor
    }

    override fun getName(): String {
        return "delete_monitor_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer {
        val monitorId = request.param("monitorID")
        if (monitorId == null || monitorId.isEmpty()) {
            throw IllegalArgumentException("missing monitor id to delete")
        }
        val deleteRequest = DeleteRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE, monitorId)

        if (request.hasParam(REFRESH)) {
            deleteRequest.setRefreshPolicy(request.param(REFRESH))
        }

        return RestChannelConsumer{ channel -> client.delete(deleteRequest, deleteMonitorResponse(channel)) }
    }

    private fun deleteMonitorResponse(channel: RestChannel): RestResponseListener<DeleteResponse> {
        return object : RestResponseListener<DeleteResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: DeleteResponse): RestResponse {
                return BytesRestResponse(response.status(), channel.newBuilder())
            }
        }
    }

}

