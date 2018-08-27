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
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.PUT;


/**
 * This class consists of the REST handler to create polices.
 * It handles the request and passes it along to the MonitorManager which actually creates the monitor.
 */
public class RestPutMonitorAction extends BaseRestHandler {

    private final MonitorManager monitorManager;

    private final String VERSION = "version";

    public RestPutMonitorAction(final Settings settings, final RestController controller) {
        this(settings, controller, new MonitorManager(settings));
    }

    //VisibleForTesting
    public RestPutMonitorAction(final Settings settings, final RestController controller, final MonitorManager monitorManager) {
        super(settings);
        controller.registerHandler(PUT, "/_awses/monitors/{monitorID}", this); // Create a new monitor
        this.monitorManager = monitorManager;
    }

    @Override
    public String getName() {
        return "Update a monitor.";
    }


    protected RestChannelConsumer doPolicyRequest(final RestRequest request, final NodeClient client) {
        String result = "";
        RestStatus statusCode = RestStatus.OK;
        try {
            if (!request.hasParam("alertID")) {
                result = "Your request must contain a alertID.";
                statusCode = RestStatus.BAD_REQUEST;
            } else {
                result = this.monitorManager.updatePolicy(request.content().utf8ToString(), request.param("alertID"));
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

    private Map<String, String> handleParams(RestRequest request) {
        Map<String, String> queryParams = new HashMap<String, String>();
        for (String key : request.params().keySet()) {
            //todo handle version
        }
        return queryParams;
    }

}
