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
import org.apache.logging.log4j.Level;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.GET;

/**
 * This class consists of the REST handler to retrieve polices.
 * It handles returning either all policies or a single policy if provided a name / id.
 */
public class RestGetMonitorAction extends BaseRestHandler {

    private final PolicyManager policyManager;

    private static final String FROM = "from";
    private static final String SIZE = "size";
    private static final String SORT = "sort";
    private static final List<String> SORT_OPTIONS = Arrays.asList("asc", "dsc");

    public RestGetMonitorAction(final Settings settings, final RestController controller) {
        this(settings, controller, new PolicyManager(settings));
    }

    @VisibleForTesting
    public RestGetMonitorAction(final Settings settings, final RestController controller, final PolicyManager policyManager) {
        super(settings);
        // Get all monitors
        controller.registerHandler(GET, "/_awses/monitors", this);
        // Get a specific monitor
        controller.registerHandler(GET, "/_awses/monitors/{monitorID}", this);
        // Test a monitor
        controller.registerHandler(GET, "/_awses/monitors/{monitorID}/_test", this);
        // Search monitors
        controller.registerHandler(GET, "/_awses/monitors/_search", this);
        this.policyManager = policyManager;
    }

    @Override
    public String getName() {
        return "Get one or all monitors.";
    }

    protected RestChannelConsumer doGetPolicyRequest(final RestRequest request, final NodeClient client) {
        String result = "";
        RestStatus statusCode = RestStatus.OK;
        try {
            Map<String, String> params = handleParams(request);
            if (request.hasParam("monitorID")) {
                String policyName = request.param("monitorID");
                if (request.uri().endsWith("/_test")) {
                    logger.log(Level.INFO, "Testing monitor: " + policyName);
                    result = this.policyManager.doSearchAndPainless(request.param("monitorID"));
                } else if (request.uri().endsWith("/_search")) {
                    result = this.policyManager.searchMonitors(request.content().utf8ToString());
                } else {
                    logger.log(Level.INFO, "Getting monitor: " + policyName);
                    result = this.policyManager.getPolicy(policyName, request.hasParam("pretty"));
                }
            } else {
                logger.log(Level.INFO, "Getting all monitors");
                result = this.policyManager.getAllPolicies(request.hasParam("pretty"), params);
            }
        } catch (Exception e) {
            statusCode = RestStatus.BAD_REQUEST;
            logger.log(Level.ERROR, "Failed to get monitor: " + e);
            result = e.getMessage();
        }

        final RestStatus resultStatusCode = statusCode;
        final String resultMessage = result;
        return restChannel -> restChannel.sendResponse(new BytesRestResponse(resultStatusCode, resultMessage));
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        return doGetPolicyRequest(request, client);
    }

    private Map<String, String> handleParams(RestRequest request) {
        Map<String, String> queryParams = new HashMap<String, String>();
        for (String key : request.params().keySet()) {
            if (key.equals(FROM)) {
                queryParams.put(FROM, request.param(FROM));
            } else if (key.equals(SIZE)){
                queryParams.put(SIZE, request.param(SIZE));
            } else if (key.equals(SORT)) {
                if (!SORT_OPTIONS.contains(request.param(SORT))) {
                    throw new IllegalArgumentException("Not a valid sorting order. Allowed: asc & desc");
                }
            }
        }
        return queryParams;
    }

}
