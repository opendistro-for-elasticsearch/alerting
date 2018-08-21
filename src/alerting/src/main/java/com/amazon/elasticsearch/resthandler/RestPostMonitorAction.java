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


import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestPostMonitorAction extends BaseRestHandler {

    private final PolicyManager policyManager;

    public RestPostMonitorAction(final Settings settings, final RestController controller) {
        this(settings, controller, new PolicyManager(settings));
    }

    @VisibleForTesting
    public RestPostMonitorAction(final Settings settings, final RestController controller, final PolicyManager policyManager) {
        super(settings);
        controller.registerHandler(POST, "/_awses/monitors", this); // Create a new policy
        controller.registerHandler(POST, "/_awses/monitors/{monitorID}/_acknowledge", this); // Create a new policy
        this.policyManager = policyManager;
    }

    @Override
    public String getName() {
        return "Create or update a policy.";
    }


    protected RestChannelConsumer doPolicyRequest(final RestRequest request, final NodeClient client) {
        String result = "";
        RestStatus statusCode = RestStatus.OK;
        try {
            if (request.uri().endsWith("/monitors")) {
                result = this.policyManager.createPolicy(request.content().utf8ToString());
            } else {
                result = this.policyManager.acknowledgeMonitor(request.param("monitorID"), request.content().utf8ToString());
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
