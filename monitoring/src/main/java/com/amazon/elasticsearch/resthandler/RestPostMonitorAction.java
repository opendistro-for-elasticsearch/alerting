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


import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestPostMonitorAction extends BaseRestHandler {

    private final MonitorManager monitorManager;

    public RestPostMonitorAction(final Settings settings, final RestController controller) {
        this(settings, controller, new MonitorManager(settings));
    }

    //VisibleForTesting
    public RestPostMonitorAction(final Settings settings, final RestController controller, final MonitorManager monitorManager) {
        super(settings);
        controller.registerHandler(POST, "/_awses/monitors", this); // Create a new monitor
        controller.registerHandler(POST, "/_awses/monitors/{monitorID}/_acknowledge", this); // Create a new monitor
        this.monitorManager = monitorManager;
    }

    @Override
    public String getName() {
        return "Create or update a monitor.";
    }


    protected RestChannelConsumer doPolicyRequest(final RestRequest request, final NodeClient client) {
        String result = "";
        RestStatus statusCode = RestStatus.OK;
        try {
            if (request.uri().endsWith("/monitors")) {
                result = this.monitorManager.createPolicy(request.content().utf8ToString());
            } else {
                result = this.monitorManager.acknowledgeMonitor(request.param("monitorID"), request.content().utf8ToString());
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
