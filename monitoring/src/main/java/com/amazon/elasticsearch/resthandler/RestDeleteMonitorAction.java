/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler;

import com.amazon.elasticsearch.MonitoringPlugin;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.action.RestResponseListener;

import java.io.IOException;

import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOBS_INDEX;
import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOB_TYPE;
import static org.elasticsearch.rest.RestRequest.Method.DELETE;

/**
 * This class consists of the REST handler to delete polices.
 * It handles marking policies as deleted. The actual deletion will be done by the MonitorManager
 */
public class RestDeleteMonitorAction extends BaseRestHandler {

    public RestDeleteMonitorAction(final Settings settings, final RestController controller) {
        super(settings);
        controller.registerHandler(DELETE, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this); // Delete a monitor
    }

    @Override
    public String getName() {
        return "delete_monitor_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        String monitorId = request.param("monitorID");
        if (monitorId == null || monitorId.isEmpty()) {
            throw new IllegalArgumentException("missing monitor id to delete");
        }
        DeleteRequest deleteRequest = new DeleteRequest(SCHEDULED_JOBS_INDEX,
                SCHEDULED_JOB_TYPE, monitorId);

        return channel -> client.delete(deleteRequest, deleteMonitorResponse(channel));
    }

    private static RestResponseListener<DeleteResponse> deleteMonitorResponse(RestChannel channel) {
        return new RestResponseListener<DeleteResponse>(channel) {
            @Override
            public RestResponse buildResponse(DeleteResponse response) throws Exception {
                return new BytesRestResponse(response.status(), channel.newBuilder());
            }
        };
    }

}

