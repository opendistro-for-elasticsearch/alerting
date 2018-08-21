/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazon.elasticsearch.resthandler;


import com.amazon.elasticsearch.policy.PolicyManager;
import com.google.common.annotations.VisibleForTesting;
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
 * It handles the request and passes it along to the PolicyManager which actually creates the policy.
 */
public class RestPutMonitorAction extends BaseRestHandler {

    private final PolicyManager policyManager;

    private final String VERSION = "version";

    public RestPutMonitorAction(final Settings settings, final RestController controller) {
        this(settings, controller, new PolicyManager(settings));
    }

    @VisibleForTesting
    public RestPutMonitorAction(final Settings settings, final RestController controller, final PolicyManager policyManager) {
        super(settings);
        controller.registerHandler(PUT, "/_awses/monitors/{monitorID}", this); // Create a new monitor
        this.policyManager = policyManager;
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
                result = this.policyManager.updatePolicy(request.content().utf8ToString(), request.param("alertID"));
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
