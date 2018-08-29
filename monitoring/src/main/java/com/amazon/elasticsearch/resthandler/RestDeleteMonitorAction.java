/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler;

import com.amazon.elasticsearch.monitor.MonitorManager;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.DELETE;

/**
 * This class consists of the REST handler to delete polices.
 * It handles marking policies as deleted. The actual deletion will be done by the MonitorManager
 */
public class RestDeleteMonitorAction extends BaseRestHandler {

    private final MonitorManager monitorManager;

    public RestDeleteMonitorAction(final Settings settings, final RestController controller) {
        this(settings, controller, new MonitorManager(settings));
    }

    //VisibleForTesting
    public RestDeleteMonitorAction(final Settings settings, final RestController controller, final MonitorManager monitorManager) {
        super(settings);
        this.monitorManager = monitorManager;
        controller.registerHandler(DELETE, "/_awses/monitors/{monitorID}", this); // Delete a monitor
    }

    @Override
    public String getName() {
        return "Delete a monitor.";
    }

    protected RestChannelConsumer doPolicyRequest(final RestRequest request, final NodeClient client) {
        String result = "";
        RestStatus statusCode = RestStatus.OK;
        try {
            if (!request.hasParam("monitorID")) {
                result = "Your request must contain a monitorID.";
                statusCode = RestStatus.BAD_REQUEST;
            } else {
                result = this.monitorManager.deletePolicy(request.param("monitorID"));
            }
        } catch (Exception e) {
            statusCode = RestStatus.BAD_REQUEST;
            result = e.getMessage();
        }
        final RestStatus resultStatusCode = statusCode;
        final String resultMessage = result;
        return restChannel -> restChannel.sendResponse(new BytesRestResponse(resultStatusCode, resultMessage));
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        return doPolicyRequest(request, client);
    }

}

